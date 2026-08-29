package com.devpulse.integration.service;

import com.devpulse.contracts.events.*;
import com.devpulse.integration.client.GithubApiClient;
import com.devpulse.integration.entity.RawEventLog;
import com.devpulse.integration.entity.Repo;
import com.devpulse.integration.repository.RawEventLogRepository;
import com.devpulse.integration.repository.RepoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service to orchestrate historical project data sync via GitHub REST API.
 * Fetches existing repository details, pull requests, and commits, persists
 * them to PostgreSQL, and publishes canonical events to RabbitMQ.
 */
@Service
public class GithubHistoricalSyncService {

    private static final Logger log = LoggerFactory.getLogger(GithubHistoricalSyncService.class);

    private final GithubApiClient githubApiClient;
    private final RepoRepository repoRepository;
    private final RawEventLogRepository rawEventLogRepository;
    private final EventPublisherService eventPublisherService;
    private final ObjectMapper objectMapper;

    public GithubHistoricalSyncService(
            GithubApiClient githubApiClient,
            RepoRepository repoRepository,
            RawEventLogRepository rawEventLogRepository,
            EventPublisherService eventPublisherService,
            ObjectMapper objectMapper) {
        this.githubApiClient = githubApiClient;
        this.repoRepository = repoRepository;
        this.rawEventLogRepository = rawEventLogRepository;
        this.eventPublisherService = eventPublisherService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Triggers a full historical project sync for a given GitHub repository.
     *
     * @param owner GitHub repository owner (user or organization)
     * @param repo Repository name
     * @param companyId Tenant company context ID
     * @param projectId Project ID mapping
     * @return Summary map of synced records
     */
    public Map<String, Object> syncHistoricalProjectData(String owner, String repo, Integer companyId, Integer projectId) {
        log.info("Starting historical GitHub sync for owner: {}, repo: {}, companyId: {}", owner, repo, companyId);
        int prsSynced = 0;
        int commitsSynced = 0;

        // 1. Fetch & Persist Repo details
        JsonNode repoData = githubApiClient.fetchRepositoryDetails(owner, repo);
        Long githubRepoId = repoData.path("id").asLong(0L);
        String repoName = repoData.path("name").asText(repo);
        String ownerName = repoData.path("owner").path("login").asText(owner);
        String fullName = repoData.path("full_name").asText(owner + "/" + repo);
        String defaultBranch = repoData.path("default_branch").asText("main");

        Repo repoEntity = repoRepository.findByCompanyIdAndProjectId(companyId, projectId)
                .stream().findFirst()
                .orElseGet(() -> repoRepository.findByCompanyIdAndGithubRepoId(companyId, githubRepoId)
                        .orElseGet(() -> new Repo(companyId, projectId, githubRepoId > 0 ? githubRepoId : System.currentTimeMillis(), repoName, ownerName, fullName, defaultBranch)));

        if (githubRepoId > 0) {
            repoEntity.setGithubRepoId(githubRepoId);
        }
        repoEntity.setRepoName(repoName);
        repoEntity.setOwnerName(ownerName);
        repoEntity.setFullName(fullName);
        repoEntity.setDefaultBranch(defaultBranch);
        repoEntity.setLastSyncedAt(Instant.now());
        try {
            repoEntity = repoRepository.save(repoEntity);
        } catch (Exception e) {
            log.warn("Could not update repo lastSyncedAt: {}", e.getMessage());
        }

        // 2. Fetch & Process Historical Pull Requests
        JsonNode prsNode = githubApiClient.fetchPullRequests(owner, repo, "all");
        if (prsNode != null && prsNode.isArray()) {
            for (JsonNode prNode : prsNode) {
                try {
                    try {
                        String rawPayload = objectMapper.writeValueAsString(prNode);
                        RawEventLog rawLog = new RawEventLog(companyId, "github", "pull_request", rawPayload);
                        rawEventLogRepository.save(rawLog);
                    } catch (Exception logEx) {
                        log.warn("Could not persist raw log for PR: {}", logEx.getMessage());
                    }

                    Integer prId = prNode.path("id").asInt(1);
                    Integer prNumber = prNode.path("number").asInt(1);
                    String title = prNode.path("title").asText("Historical PR");
                    Integer authorId = prNode.path("user").path("id").asInt(1);
                    String baseRef = prNode.path("base").path("ref").asText(defaultBranch);
                    boolean isDraft = prNode.path("draft").asBoolean(false);
                    int additions = prNode.path("additions").asInt(0);
                    int deletions = prNode.path("deletions").asInt(0);
                    int changedFiles = prNode.path("changed_files").asInt(0);
                    String authorAssociation = prNode.path("author_association").asText(null);

                    // Publish PrOpenedEvent
                    PrOpenedEvent openedEvent = new PrOpenedEvent(
                            UUID.randomUUID().toString(), companyId, projectId, Instant.now(),
                            prId, repoEntity.getRepoId(), prNumber, title, authorId,
                            baseRef, isDraft, additions, deletions, changedFiles,
                            authorAssociation
                    );
                    openedEvent.setBody(prNode.path("body").asText(null));
                    eventPublisherService.publishEvent(openedEvent);
                    prsSynced++;

                    // If PR is merged or closed, publish corresponding state event
                    String state = prNode.path("state").asText("open");
                    boolean isMerged = prNode.path("merged_at").isTextual() || prNode.path("merged").asBoolean(false);
                    if ("closed".equalsIgnoreCase(state)) {
                        if (isMerged) {
                            PrMergedEvent mergedEvent = new PrMergedEvent(
                                    UUID.randomUUID().toString(), companyId, projectId, Instant.now(),
                                    prId, repoEntity.getRepoId(), Instant.now()
                            );
                            eventPublisherService.publishEvent(mergedEvent);
                        } else {
                            PrClosedEvent closedEvent = new PrClosedEvent(
                                    UUID.randomUUID().toString(), companyId, projectId, Instant.now(),
                                    prId, repoEntity.getRepoId(), Instant.now()
                            );
                            eventPublisherService.publishEvent(closedEvent);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to process historical PR item: {}", e.getMessage());
                }
            }
        }

        // 3. Fetch & Process Historical Commits
        JsonNode commitsNode = githubApiClient.fetchCommits(owner, repo);
        if (commitsNode.isArray()) {
            for (JsonNode commitNode : commitsNode) {
                try {
                    try {
                        String rawPayload = objectMapper.writeValueAsString(commitNode);
                        RawEventLog rawLog = new RawEventLog(companyId, "github", "push", rawPayload);
                        rawEventLogRepository.save(rawLog);
                    } catch (Exception logEx) {
                        log.warn("Could not persist raw log for commit: {}", logEx.getMessage());
                    }

                    String commitSha = commitNode.path("sha").asText(UUID.randomUUID().toString());
                    String commitMessage = commitNode.path("commit").path("message").asText("Historical Commit");
                    Integer authorId = commitNode.path("author").path("id").asInt(1);

                    CommitPushedEvent commitEvent = new CommitPushedEvent(
                            UUID.randomUUID().toString(), companyId, projectId, Instant.now(),
                            commitSha, repoEntity.getRepoId(), null, authorId,
                            commitMessage, Instant.now(), 0, 0
                    );
                    eventPublisherService.publishEvent(commitEvent);
                    commitsSynced++;
                } catch (Exception e) {
                    log.error("Failed to process historical commit item: {}", e.getMessage());
                }
            }
        }

        log.info("Finished historical GitHub sync. Synced {} PRs and {} commits for repo {}/{}", prsSynced, commitsSynced, owner, repo);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("owner", owner);
        result.put("repo", repo);
        result.put("repoId", repoEntity.getRepoId());
        result.put("prsSynced", prsSynced);
        result.put("commitsSynced", commitsSynced);
        return result;
    }
}
