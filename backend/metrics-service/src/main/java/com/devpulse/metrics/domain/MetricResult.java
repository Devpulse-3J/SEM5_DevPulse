package com.devpulse.metrics.domain;

import java.math.BigDecimal;

/** Value uses the database-native scale; change failure rate is a 0..1 ratio. */
public record MetricResult(DoraMetricKey key, BigDecimal value, long sampleSize) {
}
