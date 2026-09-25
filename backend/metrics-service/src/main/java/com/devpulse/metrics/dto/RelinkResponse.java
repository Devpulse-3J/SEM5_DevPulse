package com.devpulse.metrics.dto;

/** Result of {@code POST /metrics/authors/relink}. */
public record RelinkResponse(int linkedPullRequests) {
}
