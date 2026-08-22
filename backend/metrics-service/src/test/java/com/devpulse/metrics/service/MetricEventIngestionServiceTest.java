package com.devpulse.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devpulse.contracts.events.CommitPushedEvent;
import com.devpulse.contracts.events.DeploymentCreatedEvent;
import com.devpulse.metrics.entity.DeploymentEntity;
import com.devpulse.metrics.exception.InvalidMetricEventException;
import com.devpulse.metrics.repository.CommitRepository;
import com.devpulse.metrics.repository.DeploymentRepository;
import com.devpulse.metrics.repository.EventReferenceRepository;
import com.devpulse.metrics.repository.EventReferenceRepository.RepoReference;
import com.devpulse.metrics.repository.PullRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricEventIngestionServiceTest {

    @Mock private EventReferenceRepository references;
    @Mock private PullRequestRepository pullRequests;
    @Mock private CommitRepository commits;
    @Mock private DeploymentRepository deployments;
    private MetricEventIngestionService service;

    @BeforeEach
    void setUp() {
        service = new MetricEventIngestionService(
                references, pullRequests, commits, deployments,
                Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void successfulStatusAfterFailureMarksRecoveryWithoutErasingFailure() {
        Instant failureAt = Instant.parse("2026-08-20T10:00:00Z");
        Instant recoveryAt = Instant.parse("2026-08-20T14:00:00Z");
        DeploymentEntity existing = new DeploymentEntity();
        existing.setCompanyId(2);
        existing.setProjectId(9);
        existing.setStatus("failed");
        existing.setDeployedAt(failureAt);
        when(deployments.findByCompanyIdAndGithubDeploymentId(2, 77L))
                .thenReturn(Optional.of(existing));
        when(references.resolveDeploymentProject(2, 9, null)).thenReturn(Optional.of(9));

        service.ingest(new DeploymentCreatedEvent(
                "event-2", 2, 9, recoveryAt, 77, null,
                "production", "success", recoveryAt));

        assertThat(existing.getStatus()).isEqualTo("failed");
        assertThat(existing.getDeployedAt()).isEqualTo(failureAt);
        assertThat(existing.getFailureRecoveredAt()).isEqualTo(recoveryAt);
        verify(deployments).save(existing);
    }

    @Test
    void commitShaOwnedByAnotherTenantIsRejectedInsteadOfOverwritten() {
        String sha = "0123456789012345678901234567890123456789";
        when(references.resolveRepo(2, 3))
                .thenReturn(Optional.of(new RepoReference(10, 2, 9, 3L)));
        when(commits.findByCommitShaAndCompanyId(sha, 2)).thenReturn(Optional.empty());
        when(commits.existsById(sha)).thenReturn(true);
        CommitPushedEvent event = new CommitPushedEvent(
                "event-3", 2, 9, Instant.parse("2026-08-21T00:00:00Z"),
                sha, 3, null, null, "message",
                Instant.parse("2026-08-21T00:00:00Z"), 1, 1);

        assertThatThrownBy(() -> service.ingest(event))
                .isInstanceOf(InvalidMetricEventException.class)
                .hasMessageContaining("another company");
    }
}
