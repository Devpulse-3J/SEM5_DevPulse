package com.devpulse.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devpulse.contracts.events.BaseEvent;
import com.devpulse.integration.entity.RawEventLog;
import com.devpulse.integration.github.GithubSignatureValidator;
import com.devpulse.integration.repository.RawEventLogRepository;
import com.devpulse.integration.service.EventPublisherService;
import com.devpulse.integration.service.WebhookEventNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class WebhookControllerTest {

    private RawEventLogRepository rawEventLogRepository;
    private GithubSignatureValidator stubValidator;
    private WebhookEventNormalizer normalizer;
    private EventPublisherService eventPublisherService;
    private WebhookController controller;
    private boolean signatureResult = true;

    @BeforeEach
    public void setUp() {
        rawEventLogRepository = mock(RawEventLogRepository.class);
        eventPublisherService = mock(EventPublisherService.class);
        normalizer = new WebhookEventNormalizer(new ObjectMapper());
        
        stubValidator = new GithubSignatureValidator() {
            @Override
            public boolean isValidSignature(String payload, String signatureHeader) {
                return signatureResult;
            }
        };

        controller = new WebhookController(rawEventLogRepository, stubValidator, normalizer, eventPublisherService);
    }

    @Test
    public void testHandleGithubWebhookSuccess() {
        signatureResult = true;
        when(rawEventLogRepository.save(any())).thenAnswer(invocation -> {
            RawEventLog log = invocation.getArgument(0);
            log.setEventId(1);
            return log;
        });

        String githubJson = "{\"action\":\"opened\",\"pull_request\":{\"id\":101,\"number\":5,\"title\":\"Test PR\"}}";

        ResponseEntity<?> response = controller.handleGithubWebhook(
                githubJson, "pull_request", "sha256=valid", 1
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(rawEventLogRepository, times(1)).save(any(RawEventLog.class));
        verify(eventPublisherService, times(1)).publishEvent(any(BaseEvent.class));
    }

    @Test
    public void testHandleGithubWebhookInvalidSignature() {
        signatureResult = false;

        ResponseEntity<?> response = controller.handleGithubWebhook(
                "{\"action\":\"opened\"}", "pull_request", "sha256=invalid", 1
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rawEventLogRepository, never()).save(any());
        verify(eventPublisherService, never()).publishEvent(any());
    }

    @Test
    public void testHandleJiraWebhookSuccess() {
        when(rawEventLogRepository.save(any())).thenAnswer(invocation -> {
            RawEventLog log = invocation.getArgument(0);
            log.setEventId(2);
            return log;
        });

        String jiraJson = "{\"issue\":{\"id\":200,\"key\":\"DEV-42\",\"fields\":{\"summary\":\"Fix bug\"}}}";

        ResponseEntity<?> response = controller.handleJiraWebhook(
                jiraJson, "jira:issue_updated", 1
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(rawEventLogRepository, times(1)).save(any(RawEventLog.class));
        verify(eventPublisherService, times(1)).publishEvent(any(BaseEvent.class));
    }
}
