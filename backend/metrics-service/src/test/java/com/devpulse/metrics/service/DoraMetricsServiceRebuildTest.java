package com.devpulse.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devpulse.metrics.domain.DeploymentFact;
import com.devpulse.metrics.domain.DeploymentStatus;
import com.devpulse.metrics.exception.ApiException;
import com.devpulse.metrics.repository.DoraQueryRepository;
import com.devpulse.metrics.repository.DoraSnapshotStore;
import com.devpulse.metrics.repository.DoraSnapshotStore.Snapshot;
import com.devpulse.metrics.repository.ProjectScopeRepository.ProjectScope;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import com.devpulse.metrics.service.calculation.ChangeFailureRateCalculator;
import com.devpulse.metrics.service.calculation.DeploymentFrequencyCalculator;
import com.devpulse.metrics.service.calculation.DoraRatingPolicy;
import com.devpulse.metrics.service.calculation.LeadTimeCalculator;
import com.devpulse.metrics.service.calculation.MttrCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * Rebuilding stored daily snapshots after the underlying data was fixed.
 *
 * <p>Each past day must be calculated as of the moment the nightly job captures it
 * (00:05), from the deployments that existed by then - not from today's data,
 * which would make every day's history identical to today.
 */
class DoraMetricsServiceRebuildTest {

    private static final RequestContext ADMIN = new RequestContext(1, 15);

    private ProjectAccessService accessService;
    private DoraSnapshotStore snapshotStore;
    private DoraMetricsService service;

    @BeforeEach
    void setUp() {
        accessService = mock(ProjectAccessService.class);
        snapshotStore = mock(DoraSnapshotStore.class);
        DoraQueryRepository queryRepository = mock(DoraQueryRepository.class);

        ProjectScope project = new ProjectScope(8, 15, "Dev_pulse_Backend", 1);
        when(accessService.requireAdminAccess(ADMIN, 8)).thenReturn(project);

        // Only what is inside the queried window, like the real query.
        List<DeploymentFact> all = List.of(
                new DeploymentFact(DeploymentStatus.SUCCESS, at("2026-09-22T10:00:00Z"), null, at("2026-09-22T09:00:00Z")),
                new DeploymentFact(DeploymentStatus.FAILED, at("2026-09-23T10:00:00Z"), null, null),
                new DeploymentFact(DeploymentStatus.SUCCESS, at("2026-09-24T10:00:00Z"), null, at("2026-09-23T10:00:00Z")));
        when(queryRepository.findProductionFacts(anyInt(), anyInt(), any(Instant.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Instant start = invocation.getArgument(2);
                    Instant end = invocation.getArgument(3);
                    return all.stream()
                            .filter(fact -> !fact.deployedAt().isBefore(start) && fact.deployedAt().isBefore(end))
                            .toList();
                });

        service = new DoraMetricsService(accessService, queryRepository, snapshotStore,
                List.of(new DeploymentFrequencyCalculator(), new LeadTimeCalculator(),
                        new ChangeFailureRateCalculator(), new MttrCalculator()),
                new DoraRatingPolicy(),
                Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC));
    }

    private static Instant at(String time) {
        return Instant.parse(time);
    }

    private List<Snapshot> stored() {
        ArgumentCaptor<Snapshot> captor = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshotStore, org.mockito.Mockito.atLeast(0)).upsert(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void writesOneSnapshotPerPastDayAndSkipsToday() {
        int rebuilt = service.rebuildHistory(ADMIN, 8, 3, 30);

        assertThat(rebuilt).isEqualTo(3);
        assertThat(stored()).extracting(Snapshot::calculatedDate).containsExactly(
                LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-23"), LocalDate.parse("2026-09-24"));
    }

    @Test
    void eachDayIsCalculatedFromTheDataThatExistedByThen() {
        service.rebuildHistory(ADMIN, 8, 3, 30);

        List<Snapshot> snapshots = stored();
        // 09-22 00:05: nothing deployed yet.
        assertThat(snapshots.get(0).leadTimeHours()).isNull();
        // 09-23 00:05: only the first deployment (1h lead) exists.
        assertThat(snapshots.get(1).leadTimeHours()).isEqualByComparingTo("1.00");
        // 09-24 00:05: the failed deploy exists, but the 24h-lead deployment (09-24 10:00) does not yet.
        assertThat(snapshots.get(2).leadTimeHours()).isEqualByComparingTo("1.00");
        assertThat(snapshots.get(2).changeFailureRate()).isEqualByComparingTo("0.5000");
    }

    @Test
    void aNonAdminCannotRebuildAndNothingIsWritten() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Only company admins"))
                .when(accessService).requireAdminAccess(ADMIN, 8);

        assertThatThrownBy(() -> service.rebuildHistory(ADMIN, 8, 3, 30)).isInstanceOf(ApiException.class);
        verify(snapshotStore, never()).upsert(any(Snapshot.class));
    }
}
