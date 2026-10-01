package com.devpulse.notification.service;

import com.devpulse.contracts.events.AlertPrHighRiskEvent;
import com.devpulse.notification.entity.Alert;
import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.repository.AlertRuleRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository.ProjectPullRequest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Acts on the ML service's alert.pr_high_risk events for projects that have an
 * active HIGH_RISK_PR alert rule.
 *
 * <p>The event carries only the pull request id, so the project is looked up
 * here. A pull request is re-scored every time its repository is synced; the
 * dedup key is the pull request, so it is alerted on once.
 */
@Service
public class HighRiskPrNotifier {

    private static final Logger log = LoggerFactory.getLogger(HighRiskPrNotifier.class);
    static final Set<String> RULE_TYPES = Set.of("high_risk_pr");

    private final AlertRuleRepository alertRuleRepository;
    private final ProjectDirectoryRepository projectDirectory;
    private final PrAlertDispatcher dispatcher;

    public HighRiskPrNotifier(AlertRuleRepository alertRuleRepository,
                              ProjectDirectoryRepository projectDirectory,
                              PrAlertDispatcher dispatcher) {
        this.alertRuleRepository = alertRuleRepository;
        this.projectDirectory = projectDirectory;
        this.dispatcher = dispatcher;
    }

    public void handle(AlertPrHighRiskEvent event) {
        if (event.getCompanyId() == null || event.getPrId() == null) {
            log.warn("alert.pr_high_risk without companyId/prId, ignoring: {}", event.getEventId());
            return;
        }
        Optional<ProjectPullRequest> resolved = projectDirectory.findPullRequest(event.getCompanyId(), event.getPrId());
        if (resolved.isEmpty()) {
            log.warn("High risk PR {} in company {} belongs to no linked project; nobody to notify",
                    event.getPrId(), event.getCompanyId());
            return;
        }
        ProjectPullRequest pr = resolved.get();

        List<AlertRule> rules = alertRuleRepository.findByCompanyIdAndIsActiveTrue(event.getCompanyId()).stream()
                .filter(rule -> PrAlertDispatcher.isOfType(rule, RULE_TYPES))
                .filter(rule -> PrAlertDispatcher.covers(rule, pr.projectId()))
                .toList();
        if (rules.isEmpty()) {
            log.info("High risk PR {} in project {} matches no active HIGH_RISK_PR rule; no alert raised",
                    pr.prId(), pr.projectId());
            return;
        }

        String score = String.format(Locale.ROOT, "%.2f", event.getRiskScore());
        String summary = "High risk pull request in " + pr.projectName() + ": #" + pr.number() + " " + pr.title()
                + " (risk score " + score + ")";
        Optional<Alert> recorded = dispatcher.recordOnce(
                new Alert(event.getCompanyId(), pr.projectId(), rules.get(0).getRuleId(), "pull_request", pr.prId(),
                        "critical", summary),
                "pr-high-risk:" + pr.prId());
        if (recorded.isEmpty()) {
            log.info("Already alerted for high risk PR {}; skipping repeat score", pr.prId());
            return;
        }
        List<Alert> alerts = List.of(recorded.get());

        for (String channel : PrAlertDispatcher.channelsOf(rules)) {
            dispatcher.postToSlack(channel, summary + "\n" + pr.link(), event.getCompanyId(), alerts);
        }

        String body = "A pull request in project \"" + pr.projectName() + "\" was scored as high risk.\n\n"
                + "Project:      " + pr.projectName() + '\n'
                + "Repository:   " + pr.repoFullName() + '\n'
                + "Pull request: #" + pr.number() + " " + pr.title() + '\n'
                + "Risk score:   " + score
                + (event.getRiskCategory() == null ? "" : " (" + event.getRiskCategory() + ")") + "\n\n"
                + "Review it: " + pr.link() + '\n';
        int managers = dispatcher.emailManagers(pr.projectId(),
                "High risk pull request in " + pr.projectName() + " (#" + pr.number() + ")", body,
                event.getCompanyId(), alerts);
        log.info("High risk alert {} for PR {} sent to {} Slack channel(s) and {} manager(s)",
                recorded.get().getAlertId(), pr.prId(), PrAlertDispatcher.channelsOf(rules).size(), managers);
    }
}
