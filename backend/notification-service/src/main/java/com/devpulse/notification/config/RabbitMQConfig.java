package com.devpulse.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for notification-service.
 * Configures topic exchange, notification queue, bindings, and Jackson JSON converter.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${devpulse.rabbitmq.exchange:devpulse.events}")
    private String exchangeName;

    @Value("${devpulse.rabbitmq.queue.notification:notification.events}")
    private String queueName;

    @Bean
    public TopicExchange devpulseExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding bindingAlertEvents(Queue notificationQueue, TopicExchange devpulseExchange) {
        return BindingBuilder.bind(notificationQueue).to(devpulseExchange).with("alert.#");
    }

    @Bean
    public Binding bindingPrEvents(Queue notificationQueue, TopicExchange devpulseExchange) {
        return BindingBuilder.bind(notificationQueue).to(devpulseExchange).with("pr.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
