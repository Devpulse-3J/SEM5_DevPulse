package com.devpulse.integration.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQConfigTest {

    @Test
    void testDevpulseExchangeBean() {
        RabbitMQConfig config = new RabbitMQConfig();
        ReflectionTestUtils.setField(config, "exchangeName", "devpulse.events");

        TopicExchange exchange = config.devpulseExchange();

        assertNotNull(exchange);
        assertEquals("devpulse.events", exchange.getName());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }

    @Test
    void testJsonMessageConverterBean() {
        RabbitMQConfig config = new RabbitMQConfig();
        MessageConverter converter = config.jsonMessageConverter();

        assertNotNull(converter);
        assertInstanceOf(Jackson2JsonMessageConverter.class, converter);
    }
}
