package com.devpulse.auth.dto;

public class JoinWorkspaceRequestDto {

    private String githubUsername;
    private String message;

    public JoinWorkspaceRequestDto() {}

    public JoinWorkspaceRequestDto(String githubUsername, String message) {
        this.githubUsername = githubUsername;
        this.message = message;
    }

    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
