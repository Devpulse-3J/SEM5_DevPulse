package com.devpulse.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devpulse.contracts.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Normalizes raw external GitHub and Jira webhook JSON payloads into
 * DevPulse's canonical event classes (BaseEvent subclasses).
 */
@Component
public class WebhookEventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventNormalizer.class);
    private final ObjectMapper objectMapper;

    public WebhookEventNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Normalizes a raw GitHub or Jira event payload into a BaseEvent object.
     *
     * @param provider "github" or "jira"
     * @param eventType the event header (e.g. "pull_request", "push", "jira:issue_updated")
     * @param companyId company context ID
     * @param rawJson JSON payload string
     * @return Normalized BaseEvent, or null if event is unsupported or unparseable
     */
    public BaseEvent normalize(String provider, String eventType, Integer companyId, String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if ("github".equalsIgnoreCase(provider)) {
                return normalizeGithubEvent(eventType, companyId, root);
            } else if ("jira".equalsIgnoreCase(provider)) {
                return normalizeJiraEvent(eventType, companyId, root);
            }
        } catch (Exception e) {
            log.error("Failed to parse raw JSON payload for provider '{}', eventType '{}': {}", provider, eventType, e.getMessage());
        }
        return null;
    }

    private BaseEvent normalizeGithubEvent(String eventType, Integer companyId, JsonNode root) {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Integer projectId = root.path("repository").path("id").asInt(1);

        if ("pull_request".equalsIgnoreCase(eventType)) {
            String action = root.path("action").asText("");
            JsonNode prNode = root.path("pull_request");
            Integer prId = prNode.path("id").asInt(1);
            Integer repoId = root.path("repository").path("id").asInt(1);

            if ("opened".equalsIgnoreCase(action)) {
                return new PrOpenedEvent(
                        eventId, companyId, projectId, now,
                        prId,
                        repoId,
                        prNode.path("number").asInt(1),
                        prNode.path("title").asText("PR Title"),
                        prNode.path("user").path("id").asInt(1),
                        prNode.path("base").path("ref").asText("main"),
                        prNode.path("draft").asBoolean(false),
                        prNode.path("additions").asInt(0),
                        prNode.path("deletions").asInt(0),
                        prNode.path("changed_files").asInt(0)
                );
            } else if ("closed".equalsIgnoreCase(action)) {
                boolean isMerged = prNode.path("merged").asBoolean(false);
                if (isMerged) {
                    return new PrMergedEvent(eventId, companyId, projectId, now, prId, repoId, now);
                } else {
                    return new PrClosedEvent(eventId, companyId, projectId, now, prId, repoId, now);
                }
            }
        } else if ("push".equalsIgnoreCase(eventType)) {
            JsonNode headCommit = root.path("head_commit");
            String commitSha = headCommit.path("id").asText(root.path("after").asText(UUID.randomUUID().toString()));
            Integer repoId = root.path("repository").path("id").asInt(1);
            String message = headCommit.path("message").asText("Pushed commit");
            Integer authorId = root.path("sender").path("id").asInt(1);

            return new CommitPushedEvent(
                    eventId, companyId, projectId, now,
                    commitSha, repoId, null, authorId,
                    message, now, 0, 0
            );
        } else if ("deployment".equalsIgnoreCase(eventType) || "deployment_status".equalsIgnoreCase(eventType)) {
            JsonNode depNode = root.path("deployment");
            Integer deploymentId = depNode.path("id").asInt(1);
            String sha = depNode.path("sha").asText("abc1234");
            String env = depNode.path("environment").asText("production");
            String status = root.path("deployment_status").path("state").asText("success");

            return new DeploymentCreatedEvent(
                    eventId, companyId, projectId, now,
                    deploymentId, sha, env, status, now
            );
        }
        log.warn("Unsupported GitHub event type or action: {}", eventType);
        return null;
    }

    private BaseEvent normalizeJiraEvent(String eventType, Integer companyId, JsonNode root) {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Integer projectId = 1;

        JsonNode issueNode = root.path("issue");
        if (issueNode.isMissingNode() || issueNode.isNull()) {
            // Check if root itself represents issue fields
            issueNode = root;
        }

        Integer issueId = issueNode.path("id").asInt(1);
        String jiraKey = issueNode.path("key").asText("DEV-1");
        JsonNode fields = issueNode.path("fields");

        String summary = fields.path("summary").asText("Issue Summary");
        String issueTypeStr = fields.path("issuetype").path("name").asText("Task");
        String priority = fields.path("priority").path("name").asText("Medium");
        String status = fields.path("status").path("name").asText("In Progress");
        Integer storyPoints = fields.path("customfield_10016").asInt(fields.path("storyPoints").asInt(0));
        Integer assigneeId = fields.path("assignee").path("id").asInt(1);

        return new IssueUpdatedEvent(
                eventId, companyId, projectId, now,
                issueId, jiraKey, summary, issueTypeStr,
                priority, status, storyPoints, assigneeId, now
        );
    }
}
