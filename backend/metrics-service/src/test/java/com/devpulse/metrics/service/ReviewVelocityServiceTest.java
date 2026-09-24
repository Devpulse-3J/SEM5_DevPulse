package com.devpulse.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devpulse.metrics.dto.PullRequestResponse;
import com.devpulse.metrics.dto.ReviewVelocitySummaryResponse;
import com.devpulse.metrics.repository.ActivityQueryRepository;
import com.devpulse.metrics.repository.ActivityQueryRepository.PullRequestRow;
import com.devpulse.metrics.repository.ActivityQueryRepository.ReviewRow;
import com.devpulse.metrics.repository.ActivityQueryRepository.ReviewVelocityFact;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import java.math.BigDecimal;
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
class ReviewVelocityServiceTest {

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
    void getReviewVelocityCalculatesTtfrTurnaroundAndIterations() {
        // PR 1: Created at 10:00, first review at 12:00 (2h TTFR), merged at 16:00 (4h turnaround), 2 reviews
        ReviewVelocityFact fact1 = new ReviewVelocityFact(
                101, 1, "Feature A", 10, "merged",
                Instant.parse("2026-09-20T10:00:00Z"),
                Instant.parse("2026-09-20T12:00:00Z"),
                Instant.parse("2026-09-20T16:00:00Z"),
                null,
                2,
                Instant.parse("2026-09-20T12:00:00Z"),
                Instant.parse("2026-09-20T15:00:00Z"));

        // PR 2: Created at 08:00, first review at 14:00 (6h TTFR), closed at 20:00 (6h turnaround), 1 review
        ReviewVelocityFact fact2 = new ReviewVelocityFact(
                102, 2, "Bugfix B", 11, "closed",
                Instant.parse("2026-09-21T08:00:00Z"),
                null,
                null,
                Instant.parse("2026-09-21T20:00:00Z"),
                1,
                Instant.parse("2026-09-21T14:00:00Z"),
                Instant.parse("2026-09-21T14:00:00Z"));

        when(queryRepository.findReviewVelocityFacts(eq(10), eq(5), any())).thenReturn(List.of(fact1, fact2));

        ReviewVelocitySummaryResponse response = service.getReviewVelocity(context, 5, 30);

        verify(accessService).requireViewAccess(context, 5);
        assertThat(response.projectId()).isEqualTo("5");
        assertThat(response.totalPullRequests()).isEqualTo(2);
        assertThat(response.reviewedPullRequests()).isEqualTo(2);
        assertThat(response.reviewCoveragePct()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        // Average TTFR: (2h + 6h) / 2 = 4.00h
        assertThat(response.averageTtfrHours()).isEqualByComparingTo(BigDecimal.valueOf(4.00));
        // Average Turnaround: (4h + 6h) / 2 = 5.00h
        assertThat(response.averageTurnaroundHours()).isEqualByComparingTo(BigDecimal.valueOf(5.00));
        // Average Iterations: (2 + 1) / 2 = 1.50
        assertThat(response.averageReviewIterations()).isEqualByComparingTo(BigDecimal.valueOf(1.50));
        assertThat(response.pullRequests()).hasSize(2);
    }

    @Test
    void getPullRequestsPopulatesReviewVelocityFieldsPerPr() {
        PullRequestRow prRow = new PullRequestRow(
                101, 1, "Feature A", "Desc", 10, "Dev", "avatar.png",
                1, "repo", false, "merged", "feat", "main", 10, 5, 2, "http://pr/1",
                Instant.parse("2026-09-20T10:00:00Z"),
                Instant.parse("2026-09-20T15:00:00Z"),
                Instant.parse("2026-09-20T15:00:00Z"),
                Instant.parse("2026-09-20T12:00:00Z"));

        ReviewRow reviewRow = new ReviewRow(1, 101, "Reviewer", "avatar.png", "approved", Instant.parse("2026-09-20T12:00:00Z"));

        when(queryRepository.findPullRequests(eq(10), eq(5), any(), eq(100), eq(0))).thenReturn(List.of(prRow));
        when(queryRepository.findReviews(List.of(101))).thenReturn(List.of(reviewRow));
        when(queryRepository.findChecks(List.of(101))).thenReturn(List.of());

        List<PullRequestResponse> prs = service.getPullRequests(context, 5, null, 100, 0);

        assertThat(prs).hasSize(1);
        PullRequestResponse pr = prs.get(0);
        assertThat(pr.timeToFirstReviewHours()).isEqualByComparingTo(BigDecimal.valueOf(2.00));
        assertThat(pr.reviewIterations()).isEqualTo(1);
        assertThat(pr.reviewTurnaroundHours()).isEqualByComparingTo(BigDecimal.valueOf(3.00));
    }
}
