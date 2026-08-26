package com.devpulse.metrics.dto;

import java.time.Instant;
import java.util.List;

public record PullRequestResponse(
        String id,
        int number,
        String title,
        String description,
        String author,
        String authorAvatar,
        String repositoryId,
        String repositoryName,
        String status,
        String headBranch,
        String baseBranch,
        int additions,
        int deletions,
        int changedFiles,
        String url,
        Instant createdAt,
        Instant updatedAt,
        Instant mergedAt,
        List<ReviewResponse> reviews,
        List<CheckResponse> checks,
        Object riskAnalysis) {

    public record ReviewResponse(
            String id,
            String reviewerName,
            String reviewerAvatar,
            String state,
            Instant submittedAt) {
    }

    public record CheckResponse(String id, String name, String status, String url) {
    }
}
