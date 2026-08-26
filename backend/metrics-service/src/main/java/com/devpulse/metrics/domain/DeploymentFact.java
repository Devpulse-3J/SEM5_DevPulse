package com.devpulse.metrics.domain;

import java.time.Instant;

/** A read-only fact used by every DORA calculation strategy. */
public record DeploymentFact(
        DeploymentStatus status,
        Instant deployedAt,
        Instant failureRecoveredAt,
        Instant commitTime) {
}
