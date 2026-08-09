package com.devpulse.integration.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for integration-service.
 * Configures the central Topic Exchange ('devpulse.events') and 
 * the Jackson JSON converter for event payload serialization.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${devpulse.rabbitmq.exchange:devpulse.events}")
    private String exchangeName;

    /**
     * Declares the main Topic Exchange for DevPulse.
     * Durable = true (survives broker restart), AutoDelete = false.
     */
    @Bean
    public TopicExchange devpulseExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * Configures Spring AMQP to use Jackson JSON serialization instead of
     * default Java binary serialization when converting messages.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
