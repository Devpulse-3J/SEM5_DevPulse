package com.devpulse.metrics.dto;

import java.math.BigDecimal;

public record WorkloadEntryResponse(
        String userId,
        String name,
        long activePrs,
        BigDecimal loadPct,
        BigDecimal cycleTimeHours) {
}
