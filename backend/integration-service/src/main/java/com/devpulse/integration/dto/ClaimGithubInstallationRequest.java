package com.devpulse.integration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body of {@code POST /integrations/projects/{id}/github/installation}. */
public class ClaimGithubInstallationRequest {

    @NotNull(message = "installationId is required")
    @Positive(message = "installationId must be positive")
    private Long installationId;

    public ClaimGithubInstallationRequest() {
    }

    public ClaimGithubInstallationRequest(Long installationId) {
        this.installationId = installationId;
    }

    public Long getInstallationId() {
        return installationId;
    }

    public void setInstallationId(Long installationId) {
        this.installationId = installationId;
    }
}
