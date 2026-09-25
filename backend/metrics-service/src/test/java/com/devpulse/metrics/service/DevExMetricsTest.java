package com.devpulse.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devpulse.metrics.dto.DevExSummaryResponse;
import com.devpulse.metrics.dto.WorkloadEntryResponse;
import com.devpulse.metrics.repository.ActivityQueryRepository;
import com.devpulse.metrics.repository.ActivityQueryRepository.MemberRow;
import com.devpulse.metrics.repository.ActivityQueryRepository.PullRequestCycleFact;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DevExMetricsTest {

    @Mock
    private ProjectAccessService accessService;

    @Mock
    private ActivityQueryRepository queryRepository;

    private ActivityMetricsService service;
    private RequestContext context;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-09-24T12:00:00Z");
        service = new ActivityMetricsService(accessService, queryRepository, Clock.fixed(now, ZoneOffset.UTC), 4);
        context = new RequestContext(1, 10);
    }

    @Test
    void getWorkloadComputesDevExAndBalanceMetricsAccurately() {
        MemberRow member1 = new MemberRow(101, "Alice Dev");
        MemberRow member2 = new MemberRow(102, "Bob Overloaded");

        // Alice: 2 open PRs (50% load), 4 completed reviews, 2 active repos
        PullRequestCycleFact pr1 = new PullRequestCycleFact(101, "open", now.minusSeconds(86400), null);
        PullRequestCycleFact pr2 = new PullRequestCycleFact(101, "open", now.minusSeconds(43200), null);

        // Bob: 8 open PRs (200% load), 1 completed review, 4 active repos
        PullRequestCycleFact pr3 = new PullRequestCycleFact(102, "open", now.minusSeconds(86400), null);
        PullRequestCycleFact pr4 = new PullRequestCycleFact(102, "open", now.minusSeconds(86400), null);
        PullRequestCycleFact pr5 = new PullRequestCycleFact(102, "open", now.minusSeconds(86400), null);
        PullRequestCycleFact pr6 = new PullRequestCycleFact(102, "open", now.minusSeconds(86400), null);
        PullRequestCycleFact pr7 = new PullRequestCycleFact(102, "open", now.minusSeconds(86400), null);
        PullRequestCycleFact pr8 = new PullRequestCycleFact(102, "open", now.minusSeconds(86400), null);
        PullRequestCycleFact pr9 = new PullRequestCycleFact(102, "open", now.minusSeconds(86400), null);
        PullRequestCycleFact pr10 = new PullRequestCycleFact(102, "open", now.minusSeconds(86400), null);

        when(queryRepository.findProjectMembers(10, 5)).thenReturn(List.of(member1, member2));
        when(queryRepository.findPullRequestCycleFacts(eq(10), eq(5), any()))
                .thenReturn(List.of(pr1, pr2, pr3, pr4, pr5, pr6, pr7, pr8, pr9, pr10));
        when(queryRepository.countCompletedReviewsByUser(eq(10), eq(5), any()))
                .thenReturn(Map.of(101, 4L, 102, 1L));
        when(queryRepository.countActiveRepositoriesByUser(eq(10), eq(5), any()))
                .thenReturn(Map.of(101, 2L, 102, 4L));

        List<WorkloadEntryResponse> workload = service.getWorkload(context, 5, 30);

        assertThat(workload).hasSize(2);

        WorkloadEntryResponse alice = workload.get(0);
        assertThat(alice.name()).isEqualTo("Alice Dev");
        assertThat(alice.activePrs()).isEqualTo(2);
        assertThat(alice.loadPct()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(alice.completedReviews()).isEqualTo(4);
        assertThat(alice.activeRepositories()).isEqualTo(2);
        assertThat(alice.workloadStatus()).isEqualTo("OPTIMAL");
        assertThat(alice.devexScore()).isGreaterThan(BigDecimal.valueOf(60.00));

        WorkloadEntryResponse bob = workload.get(1);
        assertThat(bob.name()).isEqualTo("Bob Overloaded");
        assertThat(bob.activePrs()).isEqualTo(8);
        assertThat(bob.loadPct()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
        assertThat(bob.workloadStatus()).isEqualTo("OVERLOADED");
    }

    @Test
    void getDevExSummaryAggregatesTeamHealth() {
        MemberRow member1 = new MemberRow(101, "Alice");
        when(queryRepository.findProjectMembers(10, 5)).thenReturn(List.of(member1));
        when(queryRepository.findPullRequestCycleFacts(eq(10), eq(5), any())).thenReturn(List.of());
        when(queryRepository.countCompletedReviewsByUser(eq(10), eq(5), any())).thenReturn(Map.of());
        when(queryRepository.countActiveRepositoriesByUser(eq(10), eq(5), any())).thenReturn(Map.of());

        DevExSummaryResponse summary = service.getDevExSummary(context, 5, 30);

        verify(accessService).requireViewAccess(context, 5);
        assertThat(summary.projectId()).isEqualTo("5");
        assertThat(summary.members()).hasSize(1);
        assertThat(summary.overallDevExScore()).isNotNull();
        assertThat(summary.overallTeamHealth()).isNotNull();
    }
}
