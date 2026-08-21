package com.devpulse.metrics.dto;

import com.devpulse.metrics.domain.DoraRating;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DoraSummaryResponse(
        String projectId,
        String projectName,
        long repoCount,
        Instant calculatedAt,
        int windowDays,
        List<DoraMetricResponse> metrics) {

    public record DoraMetricResponse(
            String key,
            BigDecimal value,
            String unit,
            DoraRating rating,
            BigDecimal previousValue,
            long sampleSize,
            List<HistoryPoint> history) {
    }

    public record HistoryPoint(LocalDate date, BigDecimal value) {
    }
}
