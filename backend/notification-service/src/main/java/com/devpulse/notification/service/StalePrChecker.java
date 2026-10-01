package com.devpulse.notification.service;

import com.devpulse.notification.entity.Alert;
import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.repository.AlertRuleRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository.ProjectPullRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Evaluates the STALE_PR alert rules: a pull request that has stayed open
 * longer than a rule's threshold is reported to that rule's Slack channel and
 * to the managers of its project.
 *
 * <p>A pull request is alerted on once (dedup key {@code stale-pr:<prId>}),
 * however many rules it matches or checks it lives through. Everything newly
 * stale in one project goes out as a single message, so the first check after
 * a rule is created cannot flood a channel or an inbox.
 */
@Service
public class StalePrChecker {

    private static final Logger log = LoggerFactory.getLogger(StalePrChecker.class);
    // "stale_pull_request" is what the seeded demo rule uses; the UI creates STALE_PR.
    static final Set<String> RULE_TYPES = Set.of("stale_pr", "stale_pull_request");
    static final int DEFAULT_THRESHOLD_HOURS = 24;

    private final AlertRuleRepository alertRuleRepository;
    private final ProjectDirectoryRepository projectDirectory;
    private final PrAlertDispatcher dispatcher;

    public StalePrChecker(AlertRuleRepository alertRuleRepository,
                          ProjectDirectoryRepository projectDirectory,
                          PrAlertDispatcher dispatcher) {
        this.alertRuleRepository = alertRuleRepository;
        this.projectDirectory = projectDirectory;
        this.dispatcher = dispatcher;
    }

    @Scheduled(initialDelayString = "${devpulse.notification.stale-pr.initial-delay-ms:60000}",
            fixedDelayString = "${devpulse.notification.stale-pr.check-interval-ms:900000}")
    public void check() {
        check(Instant.now());
    }

    void check(Instant now) {
        Map<Integer, List<AlertRule>> rulesByCompany = alertRuleRepository.findByIsActiveTrue().stream()
                .filter(rule -> PrAlertDispatcher.isOfType(rule, RULE_TYPES))
                .collect(Collectors.groupingBy(AlertRule::getCompanyId, LinkedHashMap::new, Collectors.toList()));
        rulesByCompany.forEach((companyId, rules) -> {
            try {
                checkCompany(companyId, rules, now);
            } catch (RuntimeException e) {
                // One company's failure must not stop the others, or the next run.
                log.error("Stale PR check failed for company {}: {}", companyId, e.getMessage(), e);
            }
        });
    }

    private void checkCompany(Integer companyId, List<AlertRule> rules, Instant now) {
        int shortest = rules.stream().mapToInt(StalePrChecker::thresholdHours).min().orElse(DEFAULT_THRESHOLD_HOURS);
        List<ProjectPullRequest> candidates = projectDirectory.findOpenPullRequestsOpenedBefore(
                companyId, now.minus(Duration.ofHours(shortest)));

        Map<Integer, List<Finding>> newByProject = new LinkedHashMap<>();
        for (ProjectPullRequest pr : candidates) {
            long hoursOpen = Duration.between(pr.createdAt(), now).toHours();
            List<AlertRule> matching = rules.stream()
                    .filter(rule -> PrAlertDispatcher.covers(rule, pr.projectId()))
                    .filter(rule -> hoursOpen >= thresholdHours(rule))
                    .toList();
            if (matching.isEmpty()) {
                continue;
            }
            Optional<Alert> recorded = dispatcher.recordOnce(
                    new Alert(companyId, pr.projectId(), matching.get(0).getRuleId(), "pull_request", pr.prId(),
                            "warning", "Stale pull request in " + pr.projectName() + ": #" + pr.number() + " "
                            + pr.title() + " (open " + age(hoursOpen) + ")"),
                    "stale-pr:" + pr.prId());
            recorded.ifPresent(alert -> newByProject.computeIfAbsent(pr.projectId(), id -> new ArrayList<>())
                    .add(new Finding(pr, alert, hoursOpen, PrAlertDispatcher.channelsOf(matching))));
        }

        newByProject.forEach((projectId, findings) -> notify(companyId, projectId, findings));
    }

    private void notify(Integer companyId, Integer projectId, List<Finding> findings) {
        String projectName = findings.get(0).pr().projectName();
        String headline = findings.size() == 1
                ? "1 stale pull request in " + projectName
                : findings.size() + " stale pull requests in " + projectName;

        Set<String> channels = findings.stream().flatMap(finding -> finding.channels().stream())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        for (String channel : channels) {
            List<Finding> forChannel = findings.stream().filter(finding -> finding.channels().contains(channel)).toList();
            dispatcher.postToSlack(channel, headline + "\n" + lines(forChannel), companyId,
                    forChannel.stream().map(Finding::alert).toList());
        }

        String body = "These pull requests in project \"" + projectName + "\" have been open longer than the "
                + "project's stale pull request alert allows:\n\n" + lines(findings);
        int managers = dispatcher.emailManagers(projectId, headline, body, companyId,
                findings.stream().map(Finding::alert).toList());
        log.info("{}: alerted {} Slack channel(s) and {} manager(s)", headline, channels.size(), managers);
    }

    private static String lines(List<Finding> findings) {
        StringBuilder text = new StringBuilder();
        for (Finding finding : findings) {
            ProjectPullRequest pr = finding.pr();
            text.append("#").append(pr.number()).append(" ").append(pr.title())
                    .append(" - open ").append(age(finding.hoursOpen())).append('\n')
                    .append(pr.link()).append('\n');
        }
        return text.toString();
    }

    private static String age(long hours) {
        return hours < 48 ? hours + " hours" : (hours / 24) + " days";
    }

    private static int thresholdHours(AlertRule rule) {
        return rule.getThresholdHours() == null || rule.getThresholdHours() < 1
                ? DEFAULT_THRESHOLD_HOURS : rule.getThresholdHours();
    }

    private record Finding(ProjectPullRequest pr, Alert alert, long hoursOpen, Set<String> channels) {
    }
}
