package com.devpulse.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubOAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription
) {
    public GithubOAuthTokenResponse(String accessToken, String scope, String tokenType) {
        this(accessToken, scope, tokenType, null, null);
    }
}
