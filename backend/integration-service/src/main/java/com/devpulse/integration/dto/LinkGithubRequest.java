package com.devpulse.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /integrations/projects/{id}/github/link}.
 *
 * <p>The URL is only length-checked here; its shape is decided by
 * {@code GithubRepoUrlParser}, which is the single source of truth for what a
 * repository URL looks like. Duplicating that as a {@code @Pattern} would leave
 * two rules to drift apart.
 */
public class LinkGithubRequest {

    @NotBlank(message = "repoUrl is required")
    @Size(max = 512, message = "repoUrl must be at most 512 characters")
    private String repoUrl;

    /**
     * Optional. When absent the repo keeps validating against the service-wide
     * GITHUB_WEBHOOK_SECRET. Minimum length mirrors GitHub's own guidance that
     * a webhook secret should be a high-entropy random string.
     */
    @Size(min = 16, max = 255,
            message = "webhookSecret must be between 16 and 255 characters")
    private String webhookSecret;

    // -- constructors --------------------------------------------------------

    public LinkGithubRequest() {
    }

    public LinkGithubRequest(String repoUrl, String webhookSecret) {
        this.repoUrl = repoUrl;
        this.webhookSecret = webhookSecret;
    }

    // -- getters / setters ---------------------------------------------------

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
