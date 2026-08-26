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
public class DeploymentFrequencyCalculator implements DoraMetricCalculator {

    @Override
    public DoraMetricKey key() {
        return DoraMetricKey.DEPLOYMENT_FREQUENCY;
    }

    @Override
    public MetricResult calculate(List<DeploymentFact> facts, MetricWindow window) {
        long successfulDeployments = facts.stream()
                .filter(fact -> window.contains(fact.deployedAt()))
                .filter(fact -> fact.status() == DeploymentStatus.SUCCESS)
                .count();
        BigDecimal value = BigDecimal.valueOf(successfulDeployments)
                .divide(BigDecimal.valueOf(window.days()), 4, RoundingMode.HALF_UP);
        return new MetricResult(key(), value, successfulDeployments);
    }
}
