package com.devpulse.metrics.service;

import com.devpulse.metrics.domain.DoraMetricKey;
import com.devpulse.metrics.domain.MetricResult;
import com.devpulse.metrics.domain.MetricWindow;
import com.devpulse.metrics.dto.DoraSummaryResponse;
import com.devpulse.metrics.dto.DoraSummaryResponse.DoraMetricResponse;
import com.devpulse.metrics.dto.DoraSummaryResponse.HistoryPoint;
import com.devpulse.metrics.repository.DoraQueryRepository;
import com.devpulse.metrics.repository.DoraSnapshotStore;
import com.devpulse.metrics.repository.DoraSnapshotStore.Snapshot;
import com.devpulse.metrics.repository.ProjectScopeRepository.ProjectScope;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import com.devpulse.metrics.service.calculation.DoraMetricCalculator;
import com.devpulse.metrics.service.calculation.DoraRatingPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoraMetricsService {

    private final ProjectAccessService projectAccessService;
    private final DoraQueryRepository queryRepository;
    private final DoraSnapshotStore snapshotStore;
    private final Map<DoraMetricKey, DoraMetricCalculator> calculators;
    private final DoraRatingPolicy ratingPolicy;
    private final Clock clock;

    public DoraMetricsService(
            ProjectAccessService projectAccessService,
            DoraQueryRepository queryRepository,
            DoraSnapshotStore snapshotStore,
            List<DoraMetricCalculator> calculatorStrategies,
            DoraRatingPolicy ratingPolicy,
            Clock clock) {
        this.projectAccessService = projectAccessService;
        this.queryRepository = queryRepository;
        this.snapshotStore = snapshotStore;
        this.ratingPolicy = ratingPolicy;
        this.clock = clock;
        this.calculators = new EnumMap<>(DoraMetricKey.class);
        calculatorStrategies.forEach(calculator -> {
            if (this.calculators.put(calculator.key(), calculator) != null) {
                throw new IllegalStateException("Duplicate calculator for " + calculator.key());
            }
        });
        if (this.calculators.size() != DoraMetricKey.values().length) {
            throw new IllegalStateException("A DORA calculator strategy is missing");
        }
    }

    @Transactional(readOnly = true)
    public DoraSummaryResponse getSummary(
            RequestContext context, Integer projectId, int windowDays, int historyDays) {
        ProjectScope project = projectAccessService.requireViewAccess(context, projectId);
        Instant calculatedAt = clock.instant();
        Calculation calculation = calculate(project.companyId(), project.projectId(), calculatedAt, windowDays);
        LocalDate today = LocalDate.now(clock);
        List<Snapshot> snapshots = snapshotStore.findHistory(
                project.companyId(), project.projectId(), windowDays,
                today.minusDays(historyDays - 1L));

        List<DoraMetricResponse> responses = new ArrayList<>();
        for (DoraMetricKey key : DoraMetricKey.values()) {
            MetricResult current = calculation.current().get(key);
            MetricResult previous = calculation.previous().get(key);
            TreeMap<LocalDate, BigDecimal> history = new TreeMap<>();
            snapshots.forEach(snapshot -> history.put(
                    snapshot.calculatedDate(), toApiValue(key, snapshotValue(snapshot, key))));
            history.put(today, toApiValue(key, current.value()));
            responses.add(new DoraMetricResponse(
                    key.apiName(),
                    toApiValue(key, current.value()),
                    key.unit(),
                    ratingPolicy.rate(key, current.value()),
                    toApiValue(key, previous.value()),
                    current.sampleSize(),
                    history.entrySet().stream()
                            .map(entry -> new HistoryPoint(entry.getKey(), entry.getValue()))
                            .toList()));
        }
        return new DoraSummaryResponse(
                project.projectId().toString(), project.projectName(), project.repoCount(),
                calculatedAt, windowDays, responses);
    }

    @Transactional
    public void calculateAndStore(ProjectScope project, Instant calculatedAt, int windowDays) {
        Calculation calculation = calculate(
                project.companyId(), project.projectId(), calculatedAt, windowDays);
        Map<DoraMetricKey, MetricResult> current = calculation.current();
        snapshotStore.upsert(new Snapshot(
                project.companyId(),
                project.projectId(),
                calculatedAt.atZone(clock.getZone()).toLocalDate(),
                calculatedAt,
                windowDays,
                current.get(DoraMetricKey.DEPLOYMENT_FREQUENCY).value(),
                current.get(DoraMetricKey.LEAD_TIME).value(),
                current.get(DoraMetricKey.CHANGE_FAILURE_RATE).value(),
                current.get(DoraMetricKey.MTTR).value()));
    }

    Calculation calculate(Integer companyId, Integer projectId, Instant end, int windowDays) {
        Instant currentStart = end.minus(windowDays, ChronoUnit.DAYS);
        Instant previousStart = currentStart.minus(windowDays, ChronoUnit.DAYS);
        MetricWindow currentWindow = new MetricWindow(currentStart, end, windowDays);
        MetricWindow previousWindow = new MetricWindow(previousStart, currentStart, windowDays);
        var facts = queryRepository.findProductionFacts(companyId, projectId, previousStart, end);
        Map<DoraMetricKey, MetricResult> current = new EnumMap<>(DoraMetricKey.class);
        Map<DoraMetricKey, MetricResult> previous = new EnumMap<>(DoraMetricKey.class);
        calculators.forEach((key, calculator) -> {
            current.put(key, calculator.calculate(facts, currentWindow));
            previous.put(key, calculator.calculate(facts, previousWindow));
        });
        return new Calculation(current, previous);
    }

    private BigDecimal snapshotValue(Snapshot snapshot, DoraMetricKey key) {
        return switch (key) {
            case DEPLOYMENT_FREQUENCY -> snapshot.deploymentFrequency();
            case LEAD_TIME -> snapshot.leadTimeHours();
            case CHANGE_FAILURE_RATE -> snapshot.changeFailureRate();
            case MTTR -> snapshot.mttrHours();
        };
    }

    private BigDecimal toApiValue(DoraMetricKey key, BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (key == DoraMetricKey.CHANGE_FAILURE_RATE) {
            return value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }
        int scale = key == DoraMetricKey.DEPLOYMENT_FREQUENCY ? 4 : 2;
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    record Calculation(
            Map<DoraMetricKey, MetricResult> current,
            Map<DoraMetricKey, MetricResult> previous) {
    }
}
