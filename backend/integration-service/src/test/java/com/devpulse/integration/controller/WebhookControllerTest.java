package com.devpulse.integration.controller;

import com.devpulse.integration.entity.RawEventLog;
import com.devpulse.integration.github.GithubSignatureValidator;
import com.devpulse.integration.repository.RawEventLogRepository;
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
    private WebhookController controller;
    private boolean signatureResult = true;

    @BeforeEach
    public void setUp() {
        rawEventLogRepository = mock(RawEventLogRepository.class);
        
        stubValidator = new GithubSignatureValidator() {
            @Override
            public boolean isValidSignature(String payload, String signatureHeader) {
                return signatureResult;
            }
        };

        controller = new WebhookController(rawEventLogRepository, stubValidator);
    }

    @Test
    public void testHandleGithubWebhookSuccess() {
        signatureResult = true;
        when(rawEventLogRepository.save(any())).thenAnswer(invocation -> {
            RawEventLog log = invocation.getArgument(0);
            log.setEventId(1);
            return log;
        });

        ResponseEntity<?> response = controller.handleGithubWebhook(
                "{\"action\":\"opened\"}", "pull_request", "sha256=valid", 1
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(rawEventLogRepository, times(1)).save(any(RawEventLog.class));
    }

    @Test
    public void testHandleGithubWebhookInvalidSignature() {
        signatureResult = false;

        ResponseEntity<?> response = controller.handleGithubWebhook(
                "{\"action\":\"opened\"}", "pull_request", "sha256=invalid", 1
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rawEventLogRepository, never()).save(any());
    }

    @Test
    public void testHandleJiraWebhookSuccess() {
        when(rawEventLogRepository.save(any())).thenAnswer(invocation -> {
            RawEventLog log = invocation.getArgument(0);
            log.setEventId(2);
            return log;
        });

        ResponseEntity<?> response = controller.handleJiraWebhook(
                "{\"issue\":{}}", "jira:issue_updated", 1
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(rawEventLogRepository, times(1)).save(any(RawEventLog.class));
    }
}
