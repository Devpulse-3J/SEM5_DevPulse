package com.devpulse.metrics.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    TopicExchange devpulseExchange(
            @Value("${devpulse.rabbitmq.exchange:devpulse.events}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean("metricsEventsQueue")
    Queue metricsEventsQueue(
            @Value("${devpulse.rabbitmq.metrics-queue:devpulse.metrics.events}") String queueName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange("")
                .deadLetterRoutingKey(queueName + ".dlq")
                .build();
    }

    @Bean("metricsDeadLetterQueue")
    Queue metricsDeadLetterQueue(
            @Value("${devpulse.rabbitmq.metrics-queue:devpulse.metrics.events}") String queueName) {
        return QueueBuilder.durable(queueName + ".dlq").build();
    }

    @Bean
    Binding pullRequestEventsBinding(
            @Qualifier("metricsEventsQueue") Queue queue, TopicExchange devpulseExchange) {
        return BindingBuilder.bind(queue).to(devpulseExchange).with("pr.*");
    }

    @Bean
    Binding commitEventsBinding(
            @Qualifier("metricsEventsQueue") Queue queue, TopicExchange devpulseExchange) {
        return BindingBuilder.bind(queue).to(devpulseExchange).with("commit.pushed");
    }

    @Bean
    Binding deploymentEventsBinding(
            @Qualifier("metricsEventsQueue") Queue queue, TopicExchange devpulseExchange) {
        return BindingBuilder.bind(queue).to(devpulseExchange).with("deployment.created");
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
