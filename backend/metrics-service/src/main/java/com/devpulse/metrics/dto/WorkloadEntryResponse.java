package com.devpulse.metrics.dto;

import java.math.BigDecimal;

public record WorkloadEntryResponse(
        String userId,
        String name,
        long activePrs,
        BigDecimal loadPct,
        BigDecimal cycleTimeHours,
        long completedReviews,
        long activeRepositories,
        BigDecimal contextSwitchingIndex,
        BigDecimal reviewBurdenRatio,
        String workloadStatus,
        BigDecimal devexScore) {

    public WorkloadEntryResponse(
            String userId,
            String name,
            long activePrs,
            BigDecimal loadPct,
            BigDecimal cycleTimeHours) {
        this(userId, name, activePrs, loadPct, cycleTimeHours, 0, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, "OPTIMAL", BigDecimal.valueOf(100.0));
    }
}
