package com.devpulse.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubEmailInfo(
        @JsonProperty("email") String email,
        @JsonProperty("primary") boolean primary,
        @JsonProperty("verified") boolean verified,
        @JsonProperty("visibility") String visibility
) {}
