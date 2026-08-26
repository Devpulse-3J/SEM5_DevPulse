package com.devpulse.metrics.domain;

import java.time.Instant;

public record MetricWindow(Instant startInclusive, Instant endExclusive, int days) {

    public MetricWindow {
        if (days < 1 || !startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("Metric window must have a positive duration");
        }
    }

    public boolean contains(Instant timestamp) {
        return timestamp != null
                && !timestamp.isBefore(startInclusive)
                && timestamp.isBefore(endExclusive);
    }
}
