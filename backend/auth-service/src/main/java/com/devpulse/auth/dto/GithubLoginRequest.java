package com.devpulse.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for GitHub OAuth login and sign-up.
 */
public class GithubLoginRequest {

    @NotBlank(message = "Authorization code is required")
    private String code;

    private String inviteToken;

    public GithubLoginRequest() {
    }

    public GithubLoginRequest(String code) {
        this.code = code;
    }

    public GithubLoginRequest(String code, String inviteToken) {
        this.code = code;
        this.inviteToken = inviteToken;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getInviteToken() {
        return inviteToken;
    }

    public void setInviteToken(String inviteToken) {
        this.inviteToken = inviteToken;
    }
}
