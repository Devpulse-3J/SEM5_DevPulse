package com.devpulse.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQConfigTest {

    @Test
    void testRabbitMQConfigBeans() {
        RabbitMQConfig config = new RabbitMQConfig();
        ReflectionTestUtils.setField(config, "exchangeName", "devpulse.events");
        ReflectionTestUtils.setField(config, "queueName", "notification.events");

        TopicExchange exchange = config.devpulseExchange();
        Queue queue = config.notificationQueue();

        assertNotNull(exchange);
        assertEquals("devpulse.events", exchange.getName());
        assertNotNull(queue);
        assertEquals("notification.events", queue.getName());

        Binding alertBinding = config.bindingAlertEvents(queue, exchange);
        Binding prBinding = config.bindingPrEvents(queue, exchange);

        assertEquals("alert.#", alertBinding.getRoutingKey());
        assertEquals("pr.#", prBinding.getRoutingKey());

        assertInstanceOf(Jackson2JsonMessageConverter.class, config.jsonMessageConverter());
    }
}
