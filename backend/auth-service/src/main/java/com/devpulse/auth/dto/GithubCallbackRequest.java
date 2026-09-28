package com.devpulse.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class GithubCallbackRequest {
    @NotBlank(message = "Authorization code is required")
    private String code;

    public GithubCallbackRequest() {}

    public GithubCallbackRequest(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
