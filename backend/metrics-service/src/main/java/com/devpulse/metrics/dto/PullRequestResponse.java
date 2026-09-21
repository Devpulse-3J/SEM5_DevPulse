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
        RiskAnalysisResponse riskAnalysis) {

    public record ReviewResponse(
            String id,
            String reviewerName,
            String reviewerAvatar,
            String state,
            Instant submittedAt) {
    }

    public record CheckResponse(String id, String name, String status, String url) {
    }

    /**
     * The latest stored prediction for a PR; null when it has not been scored.
     * {@code riskScore} is a percentage (0 to 100) and {@code riskLevel} is LOW,
     * MEDIUM or HIGH, the shape the frontend's PRRiskAnalysis already expects.
     * {@code factors} is empty: the model does not explain individual scores.
     */
    public record RiskAnalysisResponse(
            double riskScore,
            String riskLevel,
            String summary,
            List<RiskFactorResponse> factors,
            String algorithm,
            String modelVersion,
            Instant predictedAt) {
    }

    public record RiskFactorResponse(String category, String description, double impactScore) {
    }
}
