package com.devpulse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body of {@code PUT /auth/me/github}. */
public class LinkGithubRequest {

    // GitHub usernames: 1-39 chars, letters/digits/hyphens, no leading hyphen.
    @NotBlank(message = "GitHub username is required")
    @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$", message = "Not a valid GitHub username")
    private String githubUsername;

    public LinkGithubRequest() {
    }

    public LinkGithubRequest(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }
}
