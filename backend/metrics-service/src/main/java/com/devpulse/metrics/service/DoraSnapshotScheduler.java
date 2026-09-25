package com.devpulse.metrics.service;

import com.devpulse.metrics.repository.ProjectScopeRepository;
import com.devpulse.metrics.repository.ProjectScopeRepository.ProjectScope;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
    private final List<Integer> windowDaysList;

    public DoraSnapshotScheduler(
            ProjectScopeRepository projectRepository,
            DoraMetricsService metricsService,
            Clock clock,
            @Value("${devpulse.metrics.snapshots.window-days:7,14,30,90}") String windowDaysConfig) {
        this.projectRepository = projectRepository;
        this.metricsService = metricsService;
        this.clock = clock;
        this.windowDaysList = parseWindows(windowDaysConfig);
    }

    private static List<Integer> parseWindows(String config) {
        if (config == null || config.isBlank()) {
            return List.of(30);
        }
        try {
            return Arrays.stream(config.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .toList();
        } catch (NumberFormatException e) {
            log.warn("Invalid window-days config '{}', falling back to [30]", config);
            return List.of(30);
        }
    }

    @Scheduled(cron = "${devpulse.metrics.snapshots.cron:0 5 0 * * *}", zone = "UTC")
    public SnapshotExecutionResult captureDailySnapshots() {
        Instant now = clock.instant();
        log.info("Starting daily DORA snapshot capture across windows: {}", windowDaysList);
        List<ProjectScope> projects = projectRepository.findAllProjects();
        int totalSnapshots = 0;
        int successfulSnapshots = 0;
        int failedSnapshots = 0;

        for (ProjectScope project : projects) {
            for (int window : windowDaysList) {
                totalSnapshots++;
                try {
                    metricsService.calculateAndStore(project, now, window);
                    successfulSnapshots++;
                } catch (RuntimeException exception) {
                    failedSnapshots++;
                    log.error("Failed calculating DORA snapshot for project {} (window: {} days)",
                            project.projectId(), window, exception);
                }
            }
        }

        log.info("Completed DORA snapshot run: {} succeeded, {} failed of {} total",
                successfulSnapshots, failedSnapshots, totalSnapshots);
        return new SnapshotExecutionResult(projects.size(), totalSnapshots, successfulSnapshots, failedSnapshots, now);
    }

    public SnapshotExecutionResult triggerManualSnapshot(Integer targetProjectId, Integer targetWindowDays) {
        Instant now = clock.instant();
        List<ProjectScope> projects = targetProjectId != null
                ? projectRepository.findAllProjects().stream()
                        .filter(p -> p.projectId().equals(targetProjectId))
                        .toList()
                : projectRepository.findAllProjects();

        List<Integer> windows = targetWindowDays != null ? List.of(targetWindowDays) : windowDaysList;
        int totalSnapshots = 0;
        int successfulSnapshots = 0;
        int failedSnapshots = 0;

        for (ProjectScope project : projects) {
            for (int window : windows) {
                totalSnapshots++;
                try {
                    metricsService.calculateAndStore(project, now, window);
                    successfulSnapshots++;
                } catch (RuntimeException exception) {
                    failedSnapshots++;
                    log.error("Manual DORA snapshot failed for project {} (window: {} days)",
                            project.projectId(), window, exception);
                }
            }
        }

        return new SnapshotExecutionResult(projects.size(), totalSnapshots, successfulSnapshots, failedSnapshots, now);
    }

    public record SnapshotExecutionResult(
            int projectsProcessed,
            int totalSnapshotsAttempted,
            int successfulSnapshots,
            int failedSnapshots,
            Instant executedAt) {
    }
}
