package com.devpulse.metrics.service.calculation;

import com.devpulse.metrics.domain.DeploymentFact;
import com.devpulse.metrics.domain.DoraMetricKey;
import com.devpulse.metrics.domain.MetricResult;
import com.devpulse.metrics.domain.MetricWindow;
import java.util.List;

/** Strategy interface: one independently testable implementation per DORA metric. */
public interface DoraMetricCalculator {
    DoraMetricKey key();
    MetricResult calculate(List<DeploymentFact> facts, MetricWindow window);
}
