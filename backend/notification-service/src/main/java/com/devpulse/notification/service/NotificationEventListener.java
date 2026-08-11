package com.devpulse.notification.service;

import com.devpulse.contracts.events.AlertPrHighRiskEvent;
import com.devpulse.contracts.events.BaseEvent;
import com.devpulse.contracts.events.PrOpenedEvent;
import com.devpulse.notification.email.EmailNotificationService;
import com.devpulse.notification.entity.Alert;
import com.devpulse.notification.entity.AlertRule;
import com.devpulse.notification.entity.Notification;
import com.devpulse.notification.repository.AlertRepository;
import com.devpulse.notification.repository.AlertRuleRepository;
import com.devpulse.notification.repository.NotificationRepository;
import com.devpulse.notification.slack.SlackNotificationService;
import com.devpulse.notification.webhook.WebhookNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Listener consuming events from RabbitMQ notification.events queue.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final NotificationRepository notificationRepository;
    private final SlackNotificationService slackNotificationService;
    private final EmailNotificationService emailNotificationService;
    private final WebhookNotificationService webhookNotificationService;

    public NotificationEventListener(AlertRepository alertRepository,
                                     AlertRuleRepository alertRuleRepository,
                                     NotificationRepository notificationRepository,
                                     SlackNotificationService slackNotificationService,
                                     EmailNotificationService emailNotificationService,
                                     WebhookNotificationService webhookNotificationService) {
        this.alertRepository = alertRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.notificationRepository = notificationRepository;
        this.slackNotificationService = slackNotificationService;
        this.emailNotificationService = emailNotificationService;
        this.webhookNotificationService = webhookNotificationService;
    }

    @RabbitListener(queues = "${devpulse.rabbitmq.queue.notification:notification.events}")
    public void handleIncomingEvent(BaseEvent event) {
        log.info("Notification Service received event [{}] with eventId: {}, eventType: {}",
                event.getClass().getSimpleName(), event.getEventId(), event.getEventType());

        if (event instanceof AlertPrHighRiskEvent highRiskEvent) {
            processHighRiskPrAlert(highRiskEvent);
        } else if (event instanceof PrOpenedEvent prOpenedEvent) {
            log.info("Logged PR opened event for PR #{} ({})", prOpenedEvent.getGithubPrNumber(), prOpenedEvent.getTitle());
        }
    }

    private void processHighRiskPrAlert(AlertPrHighRiskEvent event) {
        log.warn("Processing HIGH RISK PR Alert for PR ID: {}, Risk Score: {}", event.getPrId(), event.getRiskScore());

        String severity = event.getRiskCategory() != null ? event.getRiskCategory() : "critical";
        String message = String.format("High Risk PR Alert: PR #%d (Risk Score: %.2f, Algorithm: %s)",
                event.getPrId(), event.getRiskScore(), event.getAlgorithm() != null ? event.getAlgorithm() : "ML");

        // 1. Evaluate active alert rules for the company/project
        List<AlertRule> activeRules = alertRuleRepository.findByCompanyIdAndIsActiveTrue(event.getCompanyId());
        AlertRule matchingRule = activeRules.stream()
                .filter(r -> r.getProjectId() == null || r.getProjectId().equals(event.getProjectId()))
                .findFirst()
                .orElse(null);

        Integer ruleId = matchingRule != null ? matchingRule.getRuleId() : null;
        String slackChannel = (matchingRule != null && matchingRule.getSlackChannel() != null && !matchingRule.getSlackChannel().isBlank())
                ? matchingRule.getSlackChannel()
                : "#dev-alerts";

        // 2. Create and save Alert record
        Alert alert = new Alert(
                event.getCompanyId(),
                event.getProjectId(),
                ruleId,
                "pull_request",
                event.getPrId(),
                severity,
                message
        );
        try {
            alert = alertRepository.save(alert);
        } catch (Exception e) {
            log.warn("Failed to save Alert with projectId {}, retrying with null project context: {}", event.getProjectId(), e.getMessage());
            alert.setProjectId(null);
            alert = alertRepository.save(alert);
        }

        // 3. Dispatch Slack notification
        boolean slackSuccess = slackNotificationService.sendSlackNotification(slackChannel, message);
        Notification slackNotification = new Notification(
                event.getCompanyId(),
                alert.getAlertId(),
                null,
                "slack",
                slackSuccess ? "sent" : "failed"
        );
        notificationRepository.save(slackNotification);

        // 4. Dispatch Email notification
        String emailRecipient = "alerts@devpulse.com";
        String emailSubject = String.format("High Risk PR Alert: PR #%d", event.getPrId());
        boolean emailSuccess = emailNotificationService.sendEmailNotification(emailRecipient, emailSubject, message);
        Notification emailNotification = new Notification(
                event.getCompanyId(),
                alert.getAlertId(),
                null,
                "email",
                emailSuccess ? "sent" : "failed"
        );
        notificationRepository.save(emailNotification);

        // 5. Dispatch Webhook notification (logged under in_app channel to comply with DB check constraint)
        boolean webhookSuccess = webhookNotificationService.sendWebhookNotification(null, message);
        Notification webhookNotification = new Notification(
                event.getCompanyId(),
                alert.getAlertId(),
                null,
                "in_app",
                webhookSuccess ? "sent" : "failed"
        );
        notificationRepository.save(webhookNotification);

        log.info("Alert ID #{} and multi-channel notifications (Slack, Email, Webhook) logged successfully", alert.getAlertId());
    }
}


