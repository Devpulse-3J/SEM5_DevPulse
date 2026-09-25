package com.devpulse.metrics.service.calculation;

import com.devpulse.metrics.domain.DeploymentFact;
import com.devpulse.metrics.domain.DeploymentStatus;
import com.devpulse.metrics.domain.DoraMetricKey;
import com.devpulse.metrics.domain.MetricResult;
import com.devpulse.metrics.domain.MetricWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MttrCalculator implements DoraMetricCalculator {

    private static final BigDecimal MILLIS_PER_HOUR = BigDecimal.valueOf(3_600_000);

    @Override
    public DoraMetricKey key() {
        return DoraMetricKey.MTTR;
    }

    /**
     * Mean time from a failed (or rolled-back) production deployment to recovery.
     *
     * <p>Recovery is the explicit {@code failureRecoveredAt} when one was recorded,
     * otherwise the next SUCCESSFUL deployment after the failure. An explicit time is
     * only ever recorded when the SAME GitHub deployment later flips from failed to
     * success, but a retry or a fix is a NEW deployment, so in practice nothing
     * carried a recovery time and MTTR was "not available" however many failures
     * had been fixed. A failure with no later success is still unrecovered and is
     * left out rather than counted as zero.
     */
    @Override
    public MetricResult calculate(List<DeploymentFact> facts, MetricWindow window) {
        List<DeploymentFact> ordered = facts.stream()
                .sorted(Comparator.comparing(DeploymentFact::deployedAt))
                .toList();

        List<Duration> durations = ordered.stream()
                .filter(fact -> window.contains(fact.deployedAt()))
                .filter(fact -> fact.status() == DeploymentStatus.FAILED
                        || fact.status() == DeploymentStatus.ROLLED_BACK)
                .map(fact -> recoveredAt(fact, ordered))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (durations.isEmpty()) {
            return new MetricResult(key(), null, 0);
        }
        long totalMillis = durations.stream().mapToLong(Duration::toMillis).sum();
        BigDecimal hours = BigDecimal.valueOf(totalMillis)
                .divide(MILLIS_PER_HOUR, 8, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(durations.size()), 2, RoundingMode.HALF_UP);
        return new MetricResult(key(), hours, durations.size());
    }

    /** Time to recover for one failed deployment, or null when it has not recovered. */
    private static Duration recoveredAt(DeploymentFact failure, List<DeploymentFact> ordered) {
        Instant recovered = failure.failureRecoveredAt();
        if (recovered == null) {
            recovered = ordered.stream()
                    .filter(next -> next.status() == DeploymentStatus.SUCCESS)
                    .map(DeploymentFact::deployedAt)
                    .filter(at -> at.isAfter(failure.deployedAt()))
                    .findFirst()
                    .orElse(null);
        }
        if (recovered == null || recovered.isBefore(failure.deployedAt())) {
            return null;
        }
        return Duration.between(failure.deployedAt(), recovered);
    }
}
