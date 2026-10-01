package com.devpulse.notification.service;

import com.devpulse.notification.email.EmailNotificationService;
import com.devpulse.notification.entity.Alert;
import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.entity.Notification;
import com.devpulse.notification.repository.AlertRepository;
import com.devpulse.notification.repository.NotificationRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository;
import com.devpulse.notification.repository.ProjectDirectoryRepository.Manager;
import com.devpulse.notification.slack.SlackNotificationService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The steps every rule-driven pull request alert shares: record the alert once,
 * post to the Slack channels of the rules that matched, email the project's
 * managers, and log each delivery in {@code notifications}.
 */
@Service
public class PrAlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PrAlertDispatcher.class);

    private final ProjectDirectoryRepository projectDirectory;
    private final AlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final SlackNotificationService slackNotificationService;
    private final EmailNotificationService emailNotificationService;

    public PrAlertDispatcher(ProjectDirectoryRepository projectDirectory,
                             AlertRepository alertRepository,
                             NotificationRepository notificationRepository,
                             SlackNotificationService slackNotificationService,
                             EmailNotificationService emailNotificationService) {
        this.projectDirectory = projectDirectory;
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
        this.slackNotificationService = slackNotificationService;
        this.emailNotificationService = emailNotificationService;
    }

    /** True when the rule is of one of the given types (case-insensitive). */
    public static boolean isOfType(AlertRule rule, Set<String> types) {
        return rule.getRuleType() != null && types.contains(rule.getRuleType().trim().toLowerCase(Locale.ROOT));
    }

    /** True when the rule covers the project: a rule without a project covers the whole company. */
    public static boolean covers(AlertRule rule, Integer projectId) {
        return rule.getProjectId() == null || rule.getProjectId().equals(projectId);
    }

    /**
     * Saves the alert unless one with the same dedup key exists. Empty means
     * this event was already alerted on and nothing more should be sent.
     */
    public Optional<Alert> recordOnce(Alert alert, String dedupKey) {
        if (alertRepository.existsByDedupKey(dedupKey)) {
            return Optional.empty();
        }
        alert.setDedupKey(dedupKey);
        try {
            return Optional.of(alertRepository.saveAndFlush(alert));
        } catch (DataIntegrityViolationException e) {
            // Raced past the exists check; the unique index let only one through.
            log.info("Already alerted for {} (concurrent delivery); skipping", dedupKey);
            return Optional.empty();
        }
    }

    /** The distinct, non-blank Slack channels of the given rules, in rule order. */
    public static Set<String> channelsOf(List<AlertRule> rules) {
        Set<String> channels = new LinkedHashSet<>();
        for (AlertRule rule : rules) {
            if (rule.getSlackChannel() != null && !rule.getSlackChannel().isBlank()) {
                channels.add(rule.getSlackChannel().trim());
            }
        }
        return channels;
    }

    /** Posts one Slack message and logs the delivery against each alert it covers. */
    public void postToSlack(String channel, String message, Integer companyId, List<Alert> alerts) {
        boolean sent = slackNotificationService.sendSlackNotification(channel, message);
        for (Alert alert : alerts) {
            save(companyId, alert, null, "slack", sent);
        }
    }

    /** Sends one email to every manager of the project and logs it against each alert it covers. */
    public int emailManagers(Integer projectId, String subject, String body, Integer companyId, List<Alert> alerts) {
        List<Manager> managers = projectDirectory.findManagers(projectId);
        for (Manager manager : managers) {
            boolean sent = emailNotificationService.sendEmailNotification(manager.email(), subject, body);
            for (Alert alert : alerts) {
                save(companyId, alert, manager.userId(), "email", sent);
            }
        }
        return managers.size();
    }

    private void save(Integer companyId, Alert alert, Integer userId, String channel, boolean sent) {
        Notification notification = new Notification(companyId, alert.getAlertId(), userId, channel, sent ? "sent" : "failed");
        if (sent) {
            notification.setSentAt(Instant.now());
        }
        notificationRepository.save(notification);
    }
}
