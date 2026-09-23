package com.devpulse.metrics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DevExSummaryResponse(
        String projectId,
        int windowDays,
        Instant calculatedAt,
        BigDecimal overallDevExScore,
        String overallTeamHealth,
        BigDecimal averageContextSwitchingIndex,
        BigDecimal teamReviewBurdenRatio,
        long optimalCount,
        long overloadedCount,
        long underutilizedCount,
        List<WorkloadEntryResponse> members) {
}
