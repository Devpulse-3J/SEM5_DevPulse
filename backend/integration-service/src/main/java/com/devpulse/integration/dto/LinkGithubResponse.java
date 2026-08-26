package com.devpulse.integration.dto;

import com.devpulse.integration.entity.Repo;

/**
 * Response for {@code POST /integrations/projects/{id}/github/link}.
 *
 * <p>{@code webhookRegistered} reports whether a webhook was actually created
 * on GitHub. It is <b>false</b> today: {@code GithubApiClient.createWebhook}
 * logs the call instead of making it. Do not treat a successful link as proof
 * that deliveries will arrive — the hook still has to be added by hand in the
 * repository's settings.
 */
public class LinkGithubResponse {

    private Integer projectId;
    private Integer repoId;
    private Long githubRepoId;
    private String owner;
    private String repoName;
    private String repoUrl;
    private String defaultBranch;
    private boolean webhookSecretConfigured;
    private boolean webhookRegistered;
    private String webhookNote;

    public LinkGithubResponse() {
    }

    public static LinkGithubResponse from(Repo repo, boolean webhookRegistered, String webhookNote) {
        LinkGithubResponse response = new LinkGithubResponse();
        response.projectId = repo.getProjectId();
        response.repoId = repo.getRepoId();
        response.githubRepoId = repo.getGithubRepoId();
        response.owner = repo.getOwnerName();
        response.repoName = repo.getRepoName();
        response.repoUrl = "https://github.com/" + repo.getFullName();
        response.defaultBranch = repo.getDefaultBranch();
        response.webhookSecretConfigured =
                repo.getWebhookSecret() != null && !repo.getWebhookSecret().isBlank();
        response.webhookRegistered = webhookRegistered;
        response.webhookNote = webhookNote;
        return response;
    }

    // -- getters / setters ---------------------------------------------------

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

    public boolean isWebhookRegistered() {
        return webhookRegistered;
    }

    public void setWebhookRegistered(boolean webhookRegistered) {
        this.webhookRegistered = webhookRegistered;
    }

    public String getWebhookNote() {
        return webhookNote;
    }

    public void setWebhookNote(String webhookNote) {
        this.webhookNote = webhookNote;
    }
}
