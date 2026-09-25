package com.devpulse.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devpulse.metrics.repository.ProjectScopeRepository;
import com.devpulse.metrics.repository.ProjectScopeRepository.ProjectScope;
import com.devpulse.metrics.service.DoraSnapshotScheduler.SnapshotExecutionResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoraSnapshotSchedulerTest {

    @Mock
    private ProjectScopeRepository projectRepository;

    @Mock
    private DoraMetricsService metricsService;

    private DoraSnapshotScheduler scheduler;
    private Instant fixedInstant;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-09-24T00:05:00Z");
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        scheduler = new DoraSnapshotScheduler(projectRepository, metricsService, clock, "7,14,30,90");
    }

    @Test
    void captureDailySnapshotsProcessesAllProjectsAcrossConfiguredWindows() {
        ProjectScope p1 = new ProjectScope(1, 10, "Project A", 2L);
        ProjectScope p2 = new ProjectScope(2, 10, "Project B", 3L);
        when(projectRepository.findAllProjects()).thenReturn(List.of(p1, p2));

        SnapshotExecutionResult result = scheduler.captureDailySnapshots();

        assertThat(result.projectsProcessed()).isEqualTo(2);
        assertThat(result.totalSnapshotsAttempted()).isEqualTo(8); // 2 projects * 4 windows
        assertThat(result.successfulSnapshots()).isEqualTo(8);
        assertThat(result.failedSnapshots()).isZero();

        verify(metricsService).calculateAndStore(eq(p1), eq(fixedInstant), eq(7));
        verify(metricsService).calculateAndStore(eq(p1), eq(fixedInstant), eq(14));
        verify(metricsService).calculateAndStore(eq(p1), eq(fixedInstant), eq(30));
        verify(metricsService).calculateAndStore(eq(p1), eq(fixedInstant), eq(90));
        verify(metricsService).calculateAndStore(eq(p2), eq(fixedInstant), eq(7));
    }

    @Test
    void captureDailySnapshotsResilientToProjectCalculationFailure() {
        ProjectScope p1 = new ProjectScope(1, 10, "Failing Project", 1L);
        ProjectScope p2 = new ProjectScope(2, 10, "Working Project", 1L);
        when(projectRepository.findAllProjects()).thenReturn(List.of(p1, p2));

        doThrow(new RuntimeException("DB Timeout")).when(metricsService)
                .calculateAndStore(eq(p1), any(), eq(7));

        SnapshotExecutionResult result = scheduler.captureDailySnapshots();

        assertThat(result.projectsProcessed()).isEqualTo(2);
        assertThat(result.totalSnapshotsAttempted()).isEqualTo(8);
        assertThat(result.successfulSnapshots()).isEqualTo(7);
        assertThat(result.failedSnapshots()).isEqualTo(1);
    }

    @Test
    void triggerManualSnapshotFiltersByProjectAndWindow() {
        ProjectScope p1 = new ProjectScope(1, 10, "Project A", 2L);
        ProjectScope p2 = new ProjectScope(2, 10, "Project B", 3L);
        when(projectRepository.findAllProjects()).thenReturn(List.of(p1, p2));

        SnapshotExecutionResult result = scheduler.triggerManualSnapshot(2, 14);

        assertThat(result.projectsProcessed()).isEqualTo(1);
        assertThat(result.totalSnapshotsAttempted()).isEqualTo(1);
        assertThat(result.successfulSnapshots()).isEqualTo(1);
        verify(metricsService, times(1)).calculateAndStore(eq(p2), eq(fixedInstant), eq(14));
    }
}
