package com.devpulse.auth.dto;

/** The GitHub account now linked to the caller. */
public class LinkGithubResponse {

    private Long githubId;
    private String githubLogin;

    public LinkGithubResponse() {
    }

    public LinkGithubResponse(Long githubId, String githubLogin) {
        this.githubId = githubId;
        this.githubLogin = githubLogin;
    }

    public Long getGithubId() {
        return githubId;
    }

    public void setGithubId(Long githubId) {
        this.githubId = githubId;
    }

    public String getGithubLogin() {
        return githubLogin;
    }

    public void setGithubLogin(String githubLogin) {
        this.githubLogin = githubLogin;
    }
}
