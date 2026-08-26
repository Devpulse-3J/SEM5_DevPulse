package com.devpulse.integration.service;

import com.devpulse.contracts.events.BaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for publishing canonical DevPulse events to RabbitMQ.
 */
@Service
public class EventPublisherService {

    private static final Logger log = LoggerFactory.getLogger(EventPublisherService.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public EventPublisherService(
            RabbitTemplate rabbitTemplate,
            @Value("${devpulse.rabbitmq.exchange:devpulse.events}") String exchangeName) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
    }

    /**
     * Publishes a canonical BaseEvent to RabbitMQ.
     * Uses the event's eventType (e.g., "pr.opened") as the RabbitMQ routing key.
     *
     * @param event The event object to publish
     */
    public void publishEvent(BaseEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Cannot publish null event");
        }
        String routingKey = event.getEventType();
        log.info("Publishing event [{}] with eventId: {} to exchange '{}' using routing key '{}'",
                event.getClass().getSimpleName(), event.getEventId(), exchangeName, routingKey);

        rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
    }
}
