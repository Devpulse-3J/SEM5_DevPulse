package com.devpulse.metrics.service.calculation;

import com.devpulse.metrics.domain.DeploymentFact;
import com.devpulse.metrics.domain.DeploymentStatus;
import com.devpulse.metrics.domain.DoraMetricKey;
import com.devpulse.metrics.domain.MetricResult;
import com.devpulse.metrics.domain.MetricWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LeadTimeCalculator implements DoraMetricCalculator {

    private static final BigDecimal MILLIS_PER_HOUR = BigDecimal.valueOf(3_600_000);

    @Override
    public DoraMetricKey key() {
        return DoraMetricKey.LEAD_TIME;
    }

    @Override
    public MetricResult calculate(List<DeploymentFact> facts, MetricWindow window) {
        List<Duration> durations = facts.stream()
                .filter(fact -> window.contains(fact.deployedAt()))
                .filter(fact -> fact.status() == DeploymentStatus.SUCCESS)
                .filter(fact -> fact.commitTime() != null)
                .filter(fact -> !fact.commitTime().isAfter(fact.deployedAt()))
                .map(fact -> Duration.between(fact.commitTime(), fact.deployedAt()))
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
}
