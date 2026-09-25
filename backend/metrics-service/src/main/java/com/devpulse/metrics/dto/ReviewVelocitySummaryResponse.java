package com.devpulse.metrics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReviewVelocitySummaryResponse(
        String projectId,
        int windowDays,
        Instant calculatedAt,
        long totalPullRequests,
        long reviewedPullRequests,
        BigDecimal reviewCoveragePct,
        BigDecimal averageTtfrHours,
        BigDecimal medianTtfrHours,
        BigDecimal averageReviewIterations,
        BigDecimal averageTurnaroundHours,
        List<PrVelocityDetail> pullRequests) {

    public record PrVelocityDetail(
            Integer prId,
            int prNumber,
            String title,
            String state,
            Instant createdAt,
            Instant firstReviewAt,
            int reviewCount,
            BigDecimal ttfrHours,
            BigDecimal turnaroundHours) {
    }
}
