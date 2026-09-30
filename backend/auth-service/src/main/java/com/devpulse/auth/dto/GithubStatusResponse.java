package com.devpulse.auth.dto;

public class GithubStatusResponse {
    private boolean connected;
    private Long githubUserId;
    private String githubUsername;

    public GithubStatusResponse() {}

    public GithubStatusResponse(boolean connected, Long githubUserId, String githubUsername) {
        this.connected = connected;
        this.githubUserId = githubUserId;
        this.githubUsername = githubUsername;
    }

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public Long getGithubUserId() { return githubUserId; }
    public void setGithubUserId(Long githubUserId) { this.githubUserId = githubUserId; }

    public Long getGithubId() { return githubUserId; }
    public void setGithubId(Long githubId) { this.githubUserId = githubId; }

    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }
}
