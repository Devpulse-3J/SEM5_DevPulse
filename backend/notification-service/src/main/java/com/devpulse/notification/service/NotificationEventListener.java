package com.devpulse.notification.service;

import com.devpulse.contracts.events.AlertPrHighRiskEvent;
import com.devpulse.contracts.events.BaseEvent;
import com.devpulse.contracts.events.DeploymentCreatedEvent;
import com.devpulse.contracts.events.PrOpenedEvent;
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

    private final HighRiskPrNotifier highRiskPrNotifier;
    private final DeploymentFailureNotifier deploymentFailureNotifier;

    public NotificationEventListener(HighRiskPrNotifier highRiskPrNotifier,
                                     DeploymentFailureNotifier deploymentFailureNotifier) {
        this.highRiskPrNotifier = highRiskPrNotifier;
        this.deploymentFailureNotifier = deploymentFailureNotifier;
    }

    @RabbitListener(queues = "${devpulse.rabbitmq.queue.notification:notification.events}")
    public void handleIncomingEvent(BaseEvent event) {
        log.info("Notification Service received event [{}] with eventId: {}, eventType: {}",
                event.getClass().getSimpleName(), event.getEventId(), event.getEventType());

        if (event instanceof AlertPrHighRiskEvent highRiskEvent) {
            highRiskPrNotifier.handle(highRiskEvent);
        } else if (event instanceof PrOpenedEvent prOpenedEvent) {
            log.info("Logged PR opened event for PR #{} ({})", prOpenedEvent.getGithubPrNumber(), prOpenedEvent.getTitle());
        } else if (event instanceof DeploymentCreatedEvent deploymentEvent) {
            deploymentFailureNotifier.handle(deploymentEvent);
        }
    }
}
