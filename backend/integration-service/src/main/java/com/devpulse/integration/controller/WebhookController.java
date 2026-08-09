package com.devpulse.integration.controller;

import com.devpulse.contracts.events.BaseEvent;
import com.devpulse.integration.entity.RawEventLog;
import com.devpulse.integration.github.GithubSignatureValidator;
import com.devpulse.integration.repository.RawEventLogRepository;
import com.devpulse.integration.service.EventPublisherService;
import com.devpulse.integration.service.WebhookEventNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller receiving raw external webhooks from GitHub and Jira,
 * validating signatures, persisting raw logs, normalizing into canonical events,
 * and publishing events to RabbitMQ.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RawEventLogRepository rawEventLogRepository;
    private final GithubSignatureValidator signatureValidator;
    private final WebhookEventNormalizer normalizer;
    private final EventPublisherService eventPublisherService;

    public WebhookController(RawEventLogRepository rawEventLogRepository,
                             GithubSignatureValidator signatureValidator,
                             WebhookEventNormalizer normalizer,
                             EventPublisherService eventPublisherService) {
        this.rawEventLogRepository = rawEventLogRepository;
        this.signatureValidator = signatureValidator;
        this.normalizer = normalizer;
        this.eventPublisherService = eventPublisherService;
    }

    @PostMapping("/github")
    public ResponseEntity<?> handleGithubWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "unknown") String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-Company-Id", defaultValue = "1") Integer companyId) {

        log.info("Received GitHub webhook event: {}, companyId: {}", eventType, companyId);

        // Verify HMAC signature if provided
        if (signature != null && !signatureValidator.isValidSignature(payload, signature)) {
            log.warn("Invalid GitHub webhook signature received for event: {}", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        // 1. Save raw event log
        RawEventLog rawLog = new RawEventLog(companyId, "github", eventType, payload);
        rawEventLogRepository.save(rawLog);

        // 2. Normalize raw payload to canonical BaseEvent & 3. Publish to RabbitMQ
        BaseEvent event = normalizer.normalize("github", eventType, companyId, payload);
        if (event != null) {
            eventPublisherService.publishEvent(event);
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "GitHub webhook received, normalized, and published",
                "eventId", rawLog.getEventId()
        ));
    }

    @PostMapping("/jira")
    public ResponseEntity<?> handleJiraWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Jira-Event", defaultValue = "jira:issue_updated") String eventType,
            @RequestHeader(value = "X-Company-Id", defaultValue = "1") Integer companyId) {

        log.info("Received Jira webhook event: {}, companyId: {}", eventType, companyId);

        // 1. Save raw event log
        RawEventLog rawLog = new RawEventLog(companyId, "jira", eventType, payload);
        rawEventLogRepository.save(rawLog);

        // 2. Normalize raw payload to canonical BaseEvent & 3. Publish to RabbitMQ
        BaseEvent event = normalizer.normalize("jira", eventType, companyId, payload);
        if (event != null) {
            eventPublisherService.publishEvent(event);
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Jira webhook received, normalized, and published",
                "eventId", rawLog.getEventId()
        ));
    }
}
