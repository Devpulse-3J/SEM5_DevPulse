package com.devpulse.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devpulse.contracts.events.BaseEvent;
import com.devpulse.integration.entity.JiraIssue;
import com.devpulse.integration.entity.RawEventLog;
import com.devpulse.integration.entity.Repo;
import com.devpulse.integration.github.GithubSignatureValidator;
import com.devpulse.integration.jira.JiraSignatureValidator;
import com.devpulse.integration.repository.JiraIssueRepository;
import com.devpulse.integration.repository.RawEventLogRepository;
import com.devpulse.integration.repository.RepoRepository;
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
    private RepoRepository repoRepository;
    private JiraIssueRepository jiraIssueRepository;
    private GithubSignatureValidator stubGithubValidator;
    private JiraSignatureValidator stubJiraValidator;
    private WebhookEventNormalizer normalizer;
    private EventPublisherService eventPublisherService;
    private WebhookController controller;
    private boolean githubSignatureResult = true;
    private boolean jiraSignatureResult = true;

    @BeforeEach
    public void setUp() {
        rawEventLogRepository = mock(RawEventLogRepository.class);
        repoRepository = mock(RepoRepository.class);
        jiraIssueRepository = mock(JiraIssueRepository.class);
        eventPublisherService = mock(EventPublisherService.class);
        normalizer = new WebhookEventNormalizer(new ObjectMapper());

        stubGithubValidator = new GithubSignatureValidator() {
            @Override
            public boolean isValidSignature(String payload, String signatureHeader) {
                return githubSignatureResult;
            }
        };

        stubJiraValidator = new JiraSignatureValidator() {
            @Override
            public boolean isValidSignature(String payload, String signatureHeader) {
                return jiraSignatureResult;
            }
        };

        controller = new WebhookController(
                rawEventLogRepository,
                repoRepository,
                jiraIssueRepository,
                stubGithubValidator,
                stubJiraValidator,
                normalizer,
                eventPublisherService,
                new ObjectMapper()
        );
    }

    @Test
    public void testHandleGithubWebhookSuccess() {
        githubSignatureResult = true;
        when(rawEventLogRepository.save(any())).thenAnswer(invocation -> {
            RawEventLog log = invocation.getArgument(0);
            log.setEventId(1);
            return log;
        });

        String githubJson = "{\"action\":\"opened\",\"repository\":{\"id\":500,\"name\":\"demo-repo\",\"owner\":{\"login\":\"devpulse-org\"},\"full_name\":\"devpulse-org/demo-repo\"},\"pull_request\":{\"id\":101,\"number\":5,\"title\":\"Test PR\"}}";

        ResponseEntity<?> response = controller.handleGithubWebhook(
                githubJson, "pull_request", "sha256=valid", 1
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(rawEventLogRepository, times(1)).save(any(RawEventLog.class));
        verify(repoRepository, times(1)).save(any(Repo.class));
        verify(eventPublisherService, times(1)).publishEvent(any(BaseEvent.class));
    }

    @Test
    public void testHandleGithubWebhookInvalidSignature() {
        githubSignatureResult = false;

        ResponseEntity<?> response = controller.handleGithubWebhook(
                "{\"action\":\"opened\"}", "pull_request", "sha256=invalid", 1
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rawEventLogRepository, never()).save(any());
        verify(repoRepository, never()).save(any());
        verify(eventPublisherService, never()).publishEvent(any());
    }

    @Test
    public void testHandleJiraWebhookSuccess() {
        jiraSignatureResult = true;
        when(rawEventLogRepository.save(any())).thenAnswer(invocation -> {
            RawEventLog log = invocation.getArgument(0);
            log.setEventId(2);
            return log;
        });

        String jiraJson = "{\"issue\":{\"id\":200,\"key\":\"DEV-42\",\"fields\":{\"summary\":\"Fix bug\",\"status\":{\"name\":\"In Progress\"}}}}";

        // A signature must now be present. This previously passed null, which
        // the controller treated as "nothing to verify" and accepted.
        ResponseEntity<?> response = controller.handleJiraWebhook(
                jiraJson, "jira:issue_updated", "sha256=valid", 1
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(rawEventLogRepository, times(1)).save(any(RawEventLog.class));
        verify(jiraIssueRepository, times(1)).save(any(JiraIssue.class));
        verify(eventPublisherService, times(1)).publishEvent(any(BaseEvent.class));
    }

    /**
     * An unsigned webhook must be rejected outright. /api/webhooks/** is a
     * public path at the gateway, so the HMAC is the only authentication —
     * accepting a request with no signature header let anyone write to
     * raw_event_log and publish events onto RabbitMQ.
     */
    @Test
    public void testHandleGithubWebhookMissingSignatureIsRejected() {
        ResponseEntity<?> response = controller.handleGithubWebhook(
                "{\"action\":\"opened\"}", "pull_request", null, 1
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rawEventLogRepository, never()).save(any());
        verify(repoRepository, never()).save(any());
        verify(eventPublisherService, never()).publishEvent(any());
    }

    @Test
    public void testHandleJiraWebhookMissingSignatureIsRejected() {
        ResponseEntity<?> response = controller.handleJiraWebhook(
                "{\"issue\":{\"key\":\"DEV-1\"}}", "jira:issue_updated", null, 1
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rawEventLogRepository, never()).save(any());
        verify(jiraIssueRepository, never()).save(any());
        verify(eventPublisherService, never()).publishEvent(any());
    }

    /** A blank header is as good as no header. */
    @Test
    public void testHandleGithubWebhookBlankSignatureIsRejected() {
        ResponseEntity<?> response = controller.handleGithubWebhook(
                "{\"action\":\"opened\"}", "pull_request", "   ", 1
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rawEventLogRepository, never()).save(any());
    }

    @Test
    public void testHandleJiraWebhookInvalidSignature() {
        jiraSignatureResult = false;

        ResponseEntity<?> response = controller.handleJiraWebhook(
                "{}", "jira:issue_updated", "wrong-sig", 1
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rawEventLogRepository, never()).save(any());
        verify(jiraIssueRepository, never()).save(any());
        verify(eventPublisherService, never()).publishEvent(any());
    }
}

