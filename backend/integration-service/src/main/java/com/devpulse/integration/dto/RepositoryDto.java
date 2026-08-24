package com.devpulse.integration.dto;

import com.devpulse.integration.entity.Repo;
import java.time.Instant;

/**
 * Response item for {@code GET /integrations/repositories} and
 * {@code GET /integrations/repositories/{id}}.
 *
 * <p>{@code webhookSecret} is deliberately absent. Following the convention
 * {@link GithubStatusResponse} already sets, the secret's presence is exposed as
 * the boolean {@code webhookSecretConfigured} and its value never leaves the
 * service.
 *
 * <p>There is no {@code createdAt}: the {@code repos} table has no such column.
 * {@code lastSyncedAt} is the only timestamp the entity carries.
 *
 * <p>{@code projectId} is returned without a project name. {@code projects} is
 * an auth-service-owned table and this service has no access to it, so the
 * caller joins the name client-side from {@code /api/projects}.
 */
public class RepositoryDto {

    private Integer id;
    private Long githubRepoId;
    private String fullName;
    private String ownerName;
    private String repoName;
    private String defaultBranch;
    private Integer projectId;
    private boolean webhookSecretConfigured;
    private Instant lastSyncedAt;

    public RepositoryDto() {
    }

    /**
     * Maps an entity to its API representation. Static factory rather than a
     * {@code mapper/} package, matching {@link GithubStatusResponse}.
     */
    public static RepositoryDto from(Repo repo) {
        RepositoryDto dto = new RepositoryDto();
        dto.id = repo.getRepoId();
        dto.githubRepoId = repo.getGithubRepoId();
        dto.fullName = repo.getFullName();
        dto.ownerName = repo.getOwnerName();
        dto.repoName = repo.getRepoName();
        dto.defaultBranch = repo.getDefaultBranch();
        dto.projectId = repo.getProjectId();
        // The secret itself is never returned — only whether one is set.
        dto.webhookSecretConfigured =
                repo.getWebhookSecret() != null && !repo.getWebhookSecret().isBlank();
        dto.lastSyncedAt = repo.getLastSyncedAt();
        return dto;
    }

    // -- getters / setters ---------------------------------------------------

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getGithubRepoId() {
        return githubRepoId;
    }

    public void setGithubRepoId(Long githubRepoId) {
        this.githubRepoId = githubRepoId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
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
