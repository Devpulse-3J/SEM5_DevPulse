package com.devpulse.notification.service;

import com.devpulse.contracts.events.AlertPrHighRiskEvent;
import com.devpulse.contracts.events.BaseEvent;
import com.devpulse.contracts.events.PrOpenedEvent;
import com.devpulse.notification.email.EmailNotificationService;
import com.devpulse.notification.entity.Alert;
import com.devpulse.notification.entity.Notification;
import com.devpulse.notification.repository.AlertRepository;
import com.devpulse.notification.repository.NotificationRepository;
import com.devpulse.notification.slack.SlackNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener consuming events from RabbitMQ notification.events queue.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final AlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final SlackNotificationService slackNotificationService;
    private final EmailNotificationService emailNotificationService;

    public NotificationEventListener(AlertRepository alertRepository,
                                     NotificationRepository notificationRepository,
                                     SlackNotificationService slackNotificationService,
                                     EmailNotificationService emailNotificationService) {
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
        this.slackNotificationService = slackNotificationService;
        this.emailNotificationService = emailNotificationService;
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

        // 1. Create and save Alert record
        Alert alert = new Alert(
                event.getCompanyId(),
                event.getProjectId(),
                null,
                "pull_request",
                event.getPrId(),
                severity,
                message
        );
        alert = alertRepository.save(alert);

        // 2. Dispatch Slack notification
        boolean slackSuccess = slackNotificationService.sendSlackNotification("#dev-alerts", message);

        // 3. Create and save Notification record for Slack channel delivery
        Notification notification = new Notification(
                event.getCompanyId(),
                alert.getAlertId(),
                null,
                "slack",
                slackSuccess ? "sent" : "failed"
        );
        notificationRepository.save(notification);

        log.info("Alert ID #{} and Notification ID #{} logged successfully", alert.getAlertId(), notification.getNotificationId());
    }
}
