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
                PrOpenedEvent openedEvent = new PrOpenedEvent(
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
                        prNode.path("changed_files").asInt(0),
                        prNode.path("author_association").asText(null)
                );
                openedEvent.setBody(prNode.path("body").asText(null));
                String authorEmail = prNode.path("user").path("email").asText(null);
                if (authorEmail == null || authorEmail.isBlank()) {
                    authorEmail = root.path("sender").path("email").asText(null);
                }
                openedEvent.setAuthorEmail(authorEmail);
                return openedEvent;
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

            CommitPushedEvent commitEvent = new CommitPushedEvent(
                    eventId, companyId, projectId, now,
                    commitSha, repoId, null, authorId,
                    message, now, 0, 0
            );
            String authorEmail = headCommit.path("author").path("email").asText(null);
            if (authorEmail == null || authorEmail.isBlank()) {
                authorEmail = headCommit.path("committer").path("email").asText(null);
            }
            if (authorEmail == null || authorEmail.isBlank()) {
                authorEmail = root.path("pusher").path("email").asText(null);
            }
            commitEvent.setAuthorEmail(authorEmail);
            return commitEvent;
        } else if ("deployment".equalsIgnoreCase(eventType) || "deployment_status".equalsIgnoreCase(eventType)) {
            JsonNode depNode = root.path("deployment");
            if (depNode.isMissingNode() || depNode.isNull()) {
                depNode = root.path("deployment_status").path("deployment");
            }
            Long externalDeploymentId = depNode.path("id").asLong(1L);
            int deploymentId = (int) (Math.abs(externalDeploymentId) % Integer.MAX_VALUE);
            if (deploymentId <= 0) {
                deploymentId = 1;
            }

            String sha = depNode.path("sha").asText("");
            if (sha.isBlank()) {
                sha = root.path("deployment_status").path("deployment").path("sha").asText("");
            }
            if (sha.isBlank()) {
                sha = root.path("sha").asText("abc1234");
            }

            String env = depNode.path("environment").asText("");
            if (env.isBlank()) {
                env = root.path("deployment_status").path("environment").asText("production");
            }

            String status;
            if ("deployment_status".equalsIgnoreCase(eventType)) {
                status = root.path("deployment_status").path("state").asText("success");
            } else {
                status = "pending";
            }

            Long githubRepoId = root.path("repository").path("id").asLong(1L);
            int targetRepoId = (int) (Math.abs(githubRepoId) % Integer.MAX_VALUE);

            return new DeploymentCreatedEvent(
                    eventId, companyId, targetRepoId, now,
                    deploymentId, sha, env, status, now
            );
        } else if ("workflow_job".equalsIgnoreCase(eventType)) {
            return normalizeWorkflowJobEvent(eventId, companyId, projectId, now, root);
        }
        log.warn("Unsupported GitHub event type or action: {}", eventType);
        return null;
    }

    /**
     * The GitHub App this project uses is subscribed to Actions events, not the
     * separate "Deployments" permission the {@code deployment}/{@code
     * deployment_status} branch above expects — that permission needs the org
     * owner's approval and hasn't been granted, so those two events never
     * arrive in practice. {@code workflow_job} does arrive, and is a reliable
     * proxy: CD's own "Deploy to EC2" job only runs, and only finishes, when a
     * real deploy attempt happened.
     *
     * <p>{@code workflow_job} payloads carry no environment field, so this
     * only ever reports "production" — this project has no other environment.
     */
    private BaseEvent normalizeWorkflowJobEvent(
            String eventId, Integer companyId, Integer projectId, Instant now, JsonNode root) {
        if (!"completed".equalsIgnoreCase(root.path("action").asText(""))) {
            return null; // still queued or running; nothing to report yet
        }

        JsonNode jobNode = root.path("workflow_job");
        // Every job in the CD workflow (test, build-and-push, deploy) fires this
        // event; only the job that actually deploys should become a deployment.
        if (!"Deploy to EC2".equalsIgnoreCase(jobNode.path("name").asText(""))) {
            return null;
        }

        String conclusion = jobNode.path("conclusion").asText("");
        if ("skipped".equalsIgnoreCase(conclusion)) {
            return null; // an earlier job failed and this one never ran; nothing was deployed
        }

        Integer deploymentId = jobNode.path("id").asInt(1);
        String sha = jobNode.path("head_sha").asText("abc1234");
        String status = normalizeWorkflowConclusion(conclusion);

        return new DeploymentCreatedEvent(
                eventId, companyId, projectId, now,
                deploymentId, sha, "production", status, now
        );
    }

    /**
     * workflow_job's conclusion values don't match what MetricEventIngestionService's
     * normalizeStatus() accepts, so they're translated here rather than passed through.
     */
    private String normalizeWorkflowConclusion(String conclusion) {
        if ("success".equalsIgnoreCase(conclusion)) {
            return "success";
        }
        if ("cancelled".equalsIgnoreCase(conclusion) || "timed_out".equalsIgnoreCase(conclusion)) {
            return "rolled_back";
        }
        // failure, neutral, action_required, or anything unrecognized.
        return "failure";
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
