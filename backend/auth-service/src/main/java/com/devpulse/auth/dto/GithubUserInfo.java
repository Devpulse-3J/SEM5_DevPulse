package com.devpulse.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubUserInfo(
        @JsonProperty("id") Long id,
        @JsonProperty("login") String login,
        @JsonProperty("name") String name,
        @JsonProperty("email") String email,
        @JsonProperty("avatar_url") String avatarUrl
) {
    public GithubUserInfo(Long id, String login, String name, String avatarUrl) {
        this(id, login, name, null, avatarUrl);
    }
}
