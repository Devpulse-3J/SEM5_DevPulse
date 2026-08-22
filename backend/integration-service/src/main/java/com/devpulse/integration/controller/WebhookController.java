package com.devpulse.integration.controller;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Controller receiving raw external webhooks from GitHub and Jira,
 * validating signatures, persisting raw logs & domain entities (repos,
 * jira_issues),
 * normalizing into canonical events, and publishing events to RabbitMQ.
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RawEventLogRepository rawEventLogRepository;
    private final RepoRepository repoRepository;
    private final JiraIssueRepository jiraIssueRepository;
    private final GithubSignatureValidator githubSignatureValidator;
    private final JiraSignatureValidator jiraSignatureValidator;
    private final WebhookEventNormalizer normalizer;
    private final EventPublisherService eventPublisherService;
    private final ObjectMapper objectMapper;

    public WebhookController(RawEventLogRepository rawEventLogRepository,
            RepoRepository repoRepository,
            JiraIssueRepository jiraIssueRepository,
            GithubSignatureValidator githubSignatureValidator,
            JiraSignatureValidator jiraSignatureValidator,
            WebhookEventNormalizer normalizer,
            EventPublisherService eventPublisherService,
            ObjectMapper objectMapper) {
        this.rawEventLogRepository = rawEventLogRepository;
        this.repoRepository = repoRepository;
        this.jiraIssueRepository = jiraIssueRepository;
        this.githubSignatureValidator = githubSignatureValidator;
        this.jiraSignatureValidator = jiraSignatureValidator;
        this.normalizer = normalizer;
        this.eventPublisherService = eventPublisherService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @PostMapping("/github")
    public ResponseEntity<?> handleGithubWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "unknown") String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-Company-Id", defaultValue = "1") Integer companyId) {

        log.info("Received GitHub webhook event: {}, companyId: {}", eventType, companyId);

        // HMAC is MANDATORY. /api/webhooks/** is a public path at the gateway —
        // no JWT is checked — so the signature is the only thing separating a
        // genuine GitHub delivery from an anonymous forgery. Treating a missing
        // header as "nothing to verify" let unsigned requests through.
        if (signature == null || signature.isBlank()) {
            log.warn("Rejected GitHub webhook with no X-Hub-Signature-256 header, event: {}", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing webhook signature"));
        }
        if (!githubSignatureValidator.isValidSignature(payload, signature)) {
            log.warn("Invalid GitHub webhook signature received for event: {}", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        // 1. Save raw event log
        RawEventLog rawLog = new RawEventLog(companyId, "github", eventType, payload);
        rawEventLogRepository.save(rawLog);

        // 2. Persist/update Repo entity if repo data present
        saveOrUpdateRepo(companyId, payload);

        // 3. Normalize raw payload to canonical BaseEvent & 4. Publish to RabbitMQ
        BaseEvent event = normalizer.normalize("github", eventType, companyId, payload);
        if (event != null) {
            eventPublisherService.publishEvent(event);
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "GitHub webhook received, normalized, and published",
                "eventId", rawLog.getEventId()));
    }

    @PostMapping("/jira")
    public ResponseEntity<?> handleJiraWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Jira-Event", defaultValue = "jira:issue_updated") String eventType,
            @RequestHeader(value = "X-Jira-Signature", required = false) String signature,
            @RequestHeader(value = "X-Company-Id", defaultValue = "1") Integer companyId) {

        log.info("Received Jira webhook event: {}, companyId: {}", eventType, companyId);

        // Mandatory, for the same reason as the GitHub handler above: this path
        // is public at the gateway, so the HMAC is the only authentication.
        if (signature == null || signature.isBlank()) {
            log.warn("Rejected Jira webhook with no X-Jira-Signature header, event: {}", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing webhook signature"));
        }
        if (!jiraSignatureValidator.isValidSignature(payload, signature)) {
            log.warn("Invalid Jira webhook signature received for event: {}", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        // 1. Save raw event log
        RawEventLog rawLog = new RawEventLog(companyId, "jira", eventType, payload);
        rawEventLogRepository.save(rawLog);

        // 2. Persist/update JiraIssue entity
        saveOrUpdateJiraIssue(companyId, payload);

        // 3. Normalize raw payload to canonical BaseEvent & 4. Publish to RabbitMQ
        BaseEvent event = normalizer.normalize("jira", eventType, companyId, payload);
        if (event != null) {
            eventPublisherService.publishEvent(event);
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Jira webhook received, normalized, and published",
                "eventId", rawLog.getEventId()));
    }

    private void saveOrUpdateRepo(Integer companyId, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode repoNode = root.path("repository");
            if (!repoNode.isMissingNode() && !repoNode.isNull()) {
                Long githubRepoId = repoNode.path("id").asLong(0L);
                if (githubRepoId > 0) {
                    String repoName = repoNode.path("name").asText("unknown-repo");
                    String ownerName = repoNode.path("owner").path("login")
                            .asText(repoNode.path("owner").path("name").asText("unknown-owner"));
                    String fullName = repoNode.path("full_name").asText(ownerName + "/" + repoName);
                    String defaultBranch = repoNode.path("default_branch").asText("main");
                    Integer projectId = null;

                    Repo repo = repoRepository.findByCompanyIdAndGithubRepoId(companyId, githubRepoId)
                            .orElseGet(() -> new Repo(companyId, projectId, githubRepoId, repoName, ownerName, fullName,
                                    defaultBranch));

                    repo.setRepoName(repoName);
                    repo.setOwnerName(ownerName);
                    repo.setFullName(fullName);
                    repo.setDefaultBranch(defaultBranch);
                    repoRepository.save(repo);
                }
            }
        } catch (Exception e) {
            log.error("Failed to persist Repo entity from GitHub webhook: {}", e.getMessage());
        }
    }

    private void saveOrUpdateJiraIssue(Integer companyId, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode issueNode = root.path("issue");
            if (issueNode.isMissingNode() || issueNode.isNull()) {
                issueNode = root;
            }

            String jiraKey = issueNode.path("key").asText(null);
            if (jiraKey != null && !jiraKey.isBlank()) {
                JsonNode fields = issueNode.path("fields");
                String summary = fields.path("summary").asText("Issue Summary");
                String issueType = fields.path("issuetype").path("name").asText("Task");
                String priority = fields.path("priority").path("name").asText("Medium");
                String status = fields.path("status").path("name").asText("In Progress");
                Integer storyPoints = fields.path("customfield_10016").asInt(fields.path("storyPoints").asInt(0));
                Integer assigneeId = fields.path("assignee").path("id").asInt(1);
                Integer projectId = 1;

                JiraIssue jiraIssue = jiraIssueRepository.findByCompanyIdAndJiraKey(companyId, jiraKey)
                        .orElseGet(() -> new JiraIssue(companyId, projectId, jiraKey, summary, issueType, priority,
                                status, storyPoints, assigneeId));

                jiraIssue.setSummary(summary);
                jiraIssue.setIssueType(issueType);
                jiraIssue.setPriority(priority);
                jiraIssue.setStatus(status);
                jiraIssue.setStoryPoints(storyPoints);
                jiraIssue.setAssigneeId(assigneeId);

                if ("Done".equalsIgnoreCase(status) || "Closed".equalsIgnoreCase(status)
                        || "Resolved".equalsIgnoreCase(status)) {
                    jiraIssue.setClosedAt(Instant.now());
                }

                jiraIssueRepository.save(jiraIssue);
            }
        } catch (Exception e) {
            log.error("Failed to persist JiraIssue entity from Jira webhook: {}", e.getMessage());
        }
    }
}
