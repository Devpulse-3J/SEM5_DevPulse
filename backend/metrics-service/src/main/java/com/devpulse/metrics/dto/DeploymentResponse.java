package com.devpulse.metrics.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DeploymentResponse(
        String id,
        String externalId,
        String commitSha,
        String environment,
        String status,
        Instant deployedAt,
        Instant failureRecoveredAt,
        String triggeredByUserId,
        String triggeredByName,
        BigDecimal leadTimeHours) {
}
