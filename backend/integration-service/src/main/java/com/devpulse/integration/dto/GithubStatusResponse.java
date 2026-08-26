package com.devpulse.integration.dto;

import com.devpulse.integration.entity.Repo;
import java.time.Instant;

/**
 * Response for {@code GET /integrations/projects/{id}/github/status}.
 *
 * <p>{@code status} is derived, not stored:
 * <ul>
 *   <li>{@code NOT_LINKED} — no repo row references this project</li>
 *   <li>{@code CONNECTED} — a repo is linked</li>
 * </ul>
 * There is deliberately no {@code SYNCING} value yet: syncs run inline on the
 * request thread, so no row is ever observably mid-sync. Add it when the sync
 * moves to a queue.
 */
public class GithubStatusResponse {

    public static final String NOT_LINKED = "NOT_LINKED";
    public static final String CONNECTED = "CONNECTED";

    private String status;
    private Integer projectId;
    private Integer repoId;
    private Long githubRepoId;
    private String owner;
    private String repoName;
    private String repoUrl;
    private String defaultBranch;
    private boolean webhookSecretConfigured;
    private Instant lastSyncedAt;

    public GithubStatusResponse() {
    }

    public static GithubStatusResponse notLinked(Integer projectId) {
        GithubStatusResponse response = new GithubStatusResponse();
        response.status = NOT_LINKED;
        response.projectId = projectId;
        response.webhookSecretConfigured = false;
        return response;
    }

    public static GithubStatusResponse connected(Repo repo) {
        GithubStatusResponse response = new GithubStatusResponse();
        response.status = CONNECTED;
        response.projectId = repo.getProjectId();
        response.repoId = repo.getRepoId();
        response.githubRepoId = repo.getGithubRepoId();
        response.owner = repo.getOwnerName();
        response.repoName = repo.getRepoName();
        response.repoUrl = "https://github.com/" + repo.getFullName();
        response.defaultBranch = repo.getDefaultBranch();
        // The secret itself is never returned — only whether one is set.
        response.webhookSecretConfigured =
                repo.getWebhookSecret() != null && !repo.getWebhookSecret().isBlank();
        response.lastSyncedAt = repo.getLastSyncedAt();
        return response;
    }

    // -- getters / setters ---------------------------------------------------

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public Integer getRepoId() {
        return repoId;
    }

    public void setRepoId(Integer repoId) {
        this.repoId = repoId;
    }

    public Long getGithubRepoId() {
        return githubRepoId;
    }

    public void setGithubRepoId(Long githubRepoId) {
        this.githubRepoId = githubRepoId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public boolean isWebhookSecretConfigured() {
        return webhookSecretConfigured;
    }

    public void setWebhookSecretConfigured(boolean webhookSecretConfigured) {
        this.webhookSecretConfigured = webhookSecretConfigured;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
