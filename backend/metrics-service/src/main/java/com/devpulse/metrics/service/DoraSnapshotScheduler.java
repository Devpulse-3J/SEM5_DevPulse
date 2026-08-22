package com.devpulse.metrics.service;

import com.devpulse.metrics.repository.ProjectScopeRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "devpulse.metrics.snapshots", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DoraSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(DoraSnapshotScheduler.class);

    private final ProjectScopeRepository projectRepository;
    private final DoraMetricsService metricsService;
    private final Clock clock;
    private final int windowDays;

    public DoraSnapshotScheduler(
            ProjectScopeRepository projectRepository,
            DoraMetricsService metricsService,
            Clock clock,
            @Value("${devpulse.metrics.snapshots.window-days:30}") int windowDays) {
        this.projectRepository = projectRepository;
        this.metricsService = metricsService;
        this.clock = clock;
        this.windowDays = windowDays;
    }

    @Scheduled(cron = "${devpulse.metrics.snapshots.cron:0 5 0 * * *}", zone = "UTC")
    public void captureDailySnapshots() {
        Instant now = clock.instant();
        projectRepository.findAllProjects().forEach(project -> {
            try {
                metricsService.calculateAndStore(project, now, windowDays);
            } catch (RuntimeException exception) {
                log.error("Could not calculate DORA snapshot for project {}", project.projectId(), exception);
            }
        });
    }
}
