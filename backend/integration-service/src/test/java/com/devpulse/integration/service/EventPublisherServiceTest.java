package com.devpulse.integration.service;

import com.devpulse.contracts.events.PrOpenedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventPublisherServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void testPublishPrOpenedEvent() {
        String exchangeName = "devpulse.events";
        EventPublisherService publisherService = new EventPublisherService(rabbitTemplate, exchangeName);

        PrOpenedEvent event = new PrOpenedEvent(
                UUID.randomUUID().toString(),
                1, // companyId
                10, // projectId
                Instant.now(),
                100, // prId
                5, // repoId
                42, // githubPrNumber
                "feat: add authentication",
                20, // authorId
                "main", // baseBranch
                false, // draft
                150, // linesAdded
                20, // linesDeleted
                4 // filesChanged
        );

        publisherService.publishEvent(event);

        ArgumentCaptor<PrOpenedEvent> eventCaptor = ArgumentCaptor.forClass(PrOpenedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("devpulse.events"), eq("pr.opened"), eventCaptor.capture());

        PrOpenedEvent publishedEvent = eventCaptor.getValue();
        assertNotNull(publishedEvent);
        assertEquals("pr.opened", publishedEvent.getEventType());
        assertEquals("feat: add authentication", publishedEvent.getTitle());
        assertEquals(42, publishedEvent.getGithubPrNumber());
    }

    @Test
    void testPublishNullEventThrowsException() {
        EventPublisherService publisherService = new EventPublisherService(rabbitTemplate, "devpulse.events");
        assertThrows(IllegalArgumentException.class, () -> publisherService.publishEvent(null));
    }
}
