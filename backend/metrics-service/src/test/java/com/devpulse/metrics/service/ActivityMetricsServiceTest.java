package com.devpulse.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devpulse.metrics.dto.PullRequestResponse;
import com.devpulse.metrics.repository.ActivityQueryRepository;
import com.devpulse.metrics.repository.ActivityQueryRepository.PullRequestRow;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
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
class ActivityMetricsServiceTest {

    @Mock private ProjectAccessService accessService;
    @Mock private ActivityQueryRepository queryRepository;
    private ActivityMetricsService service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new ActivityMetricsService(
                accessService, queryRepository,
                Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC), 4);
        context = new RequestContext(42, 10);
    }

    @Test
    void getPullRequestsWithProjectIdEnforcesViewAccessAndPassesAuthorId() {
        PullRequestRow row = new PullRequestRow(
                101, 12, "Fix login", "Desc", 42, "John Doe", "http://avatar",
                5, "auth-repo", false, "open", "feature", "main",
                10, 2, 3, "http://github/pr/12",
                Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2026-09-20T10:00:00Z"), null);

        when(queryRepository.findPullRequests(10, 5, 42, 100, 0))
                .thenReturn(List.of(row));
        when(queryRepository.findReviews(List.of(101))).thenReturn(List.of());
        when(queryRepository.findChecks(List.of(101))).thenReturn(List.of());

        List<PullRequestResponse> result = service.getPullRequests(context, 5, 42, 100, 0);

        verify(accessService).requireViewAccess(context, 5);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("101");
        assertThat(result.get(0).author()).isEqualTo("John Doe");
    }

    @Test
    void getPullRequestsWithoutProjectIdEnforcesCompanyAccess() {
        PullRequestRow row = new PullRequestRow(
                102, 14, "Add feature", "Desc", 42, "John Doe", "http://avatar",
                6, "core-repo", false, "open", "feature-2", "main",
                20, 5, 4, "http://github/pr/14",
                Instant.parse("2026-09-21T10:00:00Z"), Instant.parse("2026-09-21T10:00:00Z"), null);

        when(queryRepository.findPullRequests(10, null, 42, 100, 0))
                .thenReturn(List.of(row));
        when(queryRepository.findReviews(List.of(102))).thenReturn(List.of());
        when(queryRepository.findChecks(List.of(102))).thenReturn(List.of());

        List<PullRequestResponse> result = service.getPullRequests(context, null, 42, 100, 0);

        verify(accessService).requireCompanyAccess(context);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("102");
    }
}
