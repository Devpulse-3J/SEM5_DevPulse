package com.devpulse.integration.controller;

import com.devpulse.integration.entity.RawEventLog;
import com.devpulse.integration.github.GithubSignatureValidator;
import com.devpulse.integration.repository.RawEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller receiving raw external webhooks from GitHub and Jira.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RawEventLogRepository rawEventLogRepository;
    private final GithubSignatureValidator signatureValidator;

    public WebhookController(RawEventLogRepository rawEventLogRepository,
                             GithubSignatureValidator signatureValidator) {
        this.rawEventLogRepository = rawEventLogRepository;
        this.signatureValidator = signatureValidator;
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

        // Save raw event log
        RawEventLog rawLog = new RawEventLog(companyId, "github", eventType, payload);
        rawEventLogRepository.save(rawLog);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "GitHub webhook received and logged",
                "eventId", rawLog.getEventId()
        ));
    }

    @PostMapping("/jira")
    public ResponseEntity<?> handleJiraWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Jira-Event", defaultValue = "jira:issue_updated") String eventType,
            @RequestHeader(value = "X-Company-Id", defaultValue = "1") Integer companyId) {

        log.info("Received Jira webhook event: {}, companyId: {}", eventType, companyId);

        // Save raw event log
        RawEventLog rawLog = new RawEventLog(companyId, "jira", eventType, payload);
        rawEventLogRepository.save(rawLog);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Jira webhook received and logged",
                "eventId", rawLog.getEventId()
        ));
    }
}
