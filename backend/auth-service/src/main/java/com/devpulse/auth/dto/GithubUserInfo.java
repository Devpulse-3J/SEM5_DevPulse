package com.devpulse.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubUserInfo(
        @JsonProperty("id") Long id,
        @JsonProperty("login") String login,
        @JsonProperty("name") String name,
        @JsonProperty("avatar_url") String avatarUrl
) {}
