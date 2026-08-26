package com.devpulse.metrics.consumer;

import com.devpulse.contracts.events.CommitPushedEvent;
import com.devpulse.contracts.events.DeploymentCreatedEvent;
import com.devpulse.contracts.events.PrClosedEvent;
import com.devpulse.contracts.events.PrMergedEvent;
import com.devpulse.contracts.events.PrOpenedEvent;
import com.devpulse.metrics.service.MetricEventIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RabbitListener(queues = "${devpulse.rabbitmq.metrics-queue:devpulse.metrics.events}")
public class MetricsEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MetricsEventConsumer.class);
    private final MetricEventIngestionService ingestionService;

    public MetricsEventConsumer(MetricEventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @RabbitHandler
    public void handle(PrOpenedEvent event) { ingestionService.ingest(event); }

    @RabbitHandler
    public void handle(PrMergedEvent event) { ingestionService.ingest(event); }

    @RabbitHandler
    public void handle(PrClosedEvent event) { ingestionService.ingest(event); }

    @RabbitHandler
    public void handle(CommitPushedEvent event) { ingestionService.ingest(event); }

    @RabbitHandler
    public void handle(DeploymentCreatedEvent event) { ingestionService.ingest(event); }

    @RabbitHandler(isDefault = true)
    public void handleUnknown(Object event) {
        log.warn("Ignoring unsupported message delivered to the metrics queue: {}",
                event == null ? "null" : event.getClass().getName());
    }
}
