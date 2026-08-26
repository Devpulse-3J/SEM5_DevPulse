package com.devpulse.metrics.service.calculation;

import com.devpulse.metrics.domain.DeploymentFact;
import com.devpulse.metrics.domain.DeploymentStatus;
import com.devpulse.metrics.domain.DoraMetricKey;
import com.devpulse.metrics.domain.MetricResult;
import com.devpulse.metrics.domain.MetricWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChangeFailureRateCalculator implements DoraMetricCalculator {

    @Override
    public DoraMetricKey key() {
        return DoraMetricKey.CHANGE_FAILURE_RATE;
    }

    @Override
    public MetricResult calculate(List<DeploymentFact> facts, MetricWindow window) {
        List<DeploymentFact> completed = facts.stream()
                .filter(fact -> window.contains(fact.deployedAt()))
                .filter(fact -> fact.status() != DeploymentStatus.PENDING)
                .toList();
        if (completed.isEmpty()) {
            return new MetricResult(key(), null, 0);
        }
        long failed = completed.stream()
                .filter(fact -> fact.status() == DeploymentStatus.FAILED
                        || fact.status() == DeploymentStatus.ROLLED_BACK)
                .count();
        BigDecimal ratio = BigDecimal.valueOf(failed)
                .divide(BigDecimal.valueOf(completed.size()), 4, RoundingMode.HALF_UP);
        return new MetricResult(key(), ratio, completed.size());
    }
}
