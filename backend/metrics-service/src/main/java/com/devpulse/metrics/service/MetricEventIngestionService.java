package com.devpulse.metrics.service;

import com.devpulse.contracts.events.CommitPushedEvent;
import com.devpulse.contracts.events.DeploymentCreatedEvent;
import com.devpulse.contracts.events.PrClosedEvent;
import com.devpulse.contracts.events.PrMergedEvent;
import com.devpulse.contracts.events.PrOpenedEvent;
import com.devpulse.metrics.domain.DeploymentStatus;
import com.devpulse.metrics.entity.CommitEntity;
import com.devpulse.metrics.entity.DeploymentEntity;
import com.devpulse.metrics.entity.PullRequestEntity;
import com.devpulse.metrics.exception.InvalidMetricEventException;
import com.devpulse.metrics.repository.CommitRepository;
import com.devpulse.metrics.repository.DeploymentRepository;
import com.devpulse.metrics.repository.EventReferenceRepository;
import com.devpulse.metrics.repository.EventReferenceRepository.RepoReference;
import com.devpulse.metrics.repository.PullRequestRepository;
import java.time.Instant;
import java.time.Clock;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricEventIngestionService {

    private final EventReferenceRepository referenceRepository;
    private final PullRequestRepository pullRequestRepository;
    private final CommitRepository commitRepository;
    private final DeploymentRepository deploymentRepository;
    private final Clock clock;

    public MetricEventIngestionService(
            EventReferenceRepository referenceRepository,
            PullRequestRepository pullRequestRepository,
            CommitRepository commitRepository,
            DeploymentRepository deploymentRepository,
            Clock clock) {
        this.referenceRepository = referenceRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.commitRepository = commitRepository;
        this.deploymentRepository = deploymentRepository;
        this.clock = clock;
    }

    @Transactional
    public void ingest(PrOpenedEvent event) {
        requireCompany(event.getCompanyId());
        RepoReference repo = requireRepo(event.getCompanyId(), event.getRepoId());
        if (event.getPrId() == null || event.getGithubPrNumber() == null) {
            throw new InvalidMetricEventException("pr.opened is missing its PR id or number");
        }
        PullRequestEntity pullRequest = pullRequestRepository
                .findByCompanyIdAndRepoIdAndGithubPrId(
                        event.getCompanyId(), repo.repoId(), event.getPrId().longValue())
                .or(() -> pullRequestRepository.findByCompanyIdAndRepoIdAndGithubPrNumber(
                        event.getCompanyId(), repo.repoId(), event.getGithubPrNumber()))
                .orElseGet(PullRequestEntity::new);
        pullRequest.setCompanyId(event.getCompanyId());
        pullRequest.setRepoId(repo.repoId());
        pullRequest.setGithubPrId(event.getPrId().longValue());
        pullRequest.setGithubPrNumber(event.getGithubPrNumber());
        pullRequest.setTitle(event.getTitle());
        pullRequest.setDescription(event.getBody());
        pullRequest.setAuthorId(referenceRepository
                .resolveUserId(event.getCompanyId(), event.getAuthorId()).orElse(null));
        pullRequest.setBaseBranch(blankToDefault(event.getBaseBranch(), "main"));
        pullRequest.setDraft(event.isDraft());
        pullRequest.setState("open");
        pullRequest.setLinesAdded(Math.max(0, event.getLinesAdded()));
        pullRequest.setLinesDeleted(Math.max(0, event.getLinesDeleted()));
        pullRequest.setFilesChanged(Math.max(0, event.getFilesChanged()));
        pullRequest.setAuthorAssociation(event.getAuthorAssociation());
        pullRequest.setCreatedAt(timestamp(event.getTimestamp()));
        pullRequestRepository.save(pullRequest);
    }

    @Transactional
    public void ingest(PrMergedEvent event) {
        PullRequestEntity pullRequest = requirePullRequest(
                event.getCompanyId(), event.getRepoId(), event.getPrId());
        Instant mergedAt = timestamp(event.getMergedAt() == null ? event.getTimestamp() : event.getMergedAt());
        pullRequest.setState("merged");
        pullRequest.setDraft(false);
        pullRequest.setMergedAt(mergedAt);
        pullRequest.setClosedAt(mergedAt);
        pullRequestRepository.save(pullRequest);
    }

    @Transactional
    public void ingest(PrClosedEvent event) {
        PullRequestEntity pullRequest = requirePullRequest(
                event.getCompanyId(), event.getRepoId(), event.getPrId());
        pullRequest.setState("closed");
        pullRequest.setDraft(false);
        pullRequest.setClosedAt(timestamp(
                event.getClosedAt() == null ? event.getTimestamp() : event.getClosedAt()));
        pullRequestRepository.save(pullRequest);
    }

    @Transactional
    public void ingest(CommitPushedEvent event) {
        requireCompany(event.getCompanyId());
        String sha = requireSha(event.getCommitSha());
        RepoReference repo = requireRepo(event.getCompanyId(), event.getRepoId());
        CommitEntity commit = commitRepository.findByCommitShaAndCompanyId(sha, event.getCompanyId())
                .orElseGet(() -> {
                    if (commitRepository.existsById(sha)) {
                        throw new InvalidMetricEventException(
                                "commitSha already belongs to another company in the current schema");
                    }
                    return new CommitEntity(sha);
                });
        if (commit.getRepoId() != null && !commit.getRepoId().equals(repo.repoId())) {
            throw new InvalidMetricEventException(
                    "commitSha already belongs to another repository in the current schema");
        }
        commit.setCompanyId(event.getCompanyId());
        commit.setRepoId(repo.repoId());
        if (event.getPrId() != null) {
            pullRequestRepository.findByCompanyIdAndRepoIdAndGithubPrId(
                            event.getCompanyId(), repo.repoId(), event.getPrId().longValue())
                    .ifPresent(pullRequest -> commit.setPrId(pullRequest.getId()));
        }
        commit.setAuthorId(referenceRepository
                .resolveUserId(event.getCompanyId(), event.getAuthorId()).orElse(null));
        commit.setMessage(event.getMessage());
        commit.setCommitTime(timestamp(
                event.getCommitTime() == null ? event.getTimestamp() : event.getCommitTime()));
        commit.setLinesAdded(Math.max(0, event.getLinesAdded()));
        commit.setLinesDeleted(Math.max(0, event.getLinesDeleted()));
        commitRepository.save(commit);
    }

    @Transactional
    public void ingest(DeploymentCreatedEvent event) {
        requireCompany(event.getCompanyId());
        if (event.getDeploymentId() == null) {
            throw new InvalidMetricEventException("deployment.created is missing deploymentId");
        }
        long externalId = event.getDeploymentId().longValue();
        String sha = nullableSha(event.getCommitSha());
        Integer projectId = referenceRepository.resolveDeploymentProject(
                        event.getCompanyId(), event.getProjectId(), sha)
                .orElseThrow(() -> new InvalidMetricEventException(
                        "Cannot resolve a project for deployment " + externalId));
        DeploymentEntity deployment = deploymentRepository
                .findByCompanyIdAndGithubDeploymentId(event.getCompanyId(), externalId)
                .orElseGet(DeploymentEntity::new);
        DeploymentStatus incomingStatus = normalizeStatus(event.getStatus());
        DeploymentStatus previousStatus = DeploymentStatus.fromDatabase(deployment.getStatus());
        Instant eventTime = timestamp(
                event.getDeployedAt() == null ? event.getTimestamp() : event.getDeployedAt());
        deployment.setGithubDeploymentId(externalId);
        deployment.setCompanyId(event.getCompanyId());
        deployment.setProjectId(projectId);
        deployment.setCommitSha(sha != null
                && referenceRepository.commitExistsForCompany(sha, event.getCompanyId()) ? sha : null);
        deployment.setEnvironment(normalizeEnvironment(event.getEnvironment()));
        if ((previousStatus == DeploymentStatus.FAILED || previousStatus == DeploymentStatus.ROLLED_BACK)
                && incomingStatus == DeploymentStatus.SUCCESS) {
            deployment.setFailureRecoveredAt(eventTime);
        } else {
            deployment.setStatus(incomingStatus.toDatabase());
            if ((incomingStatus == DeploymentStatus.FAILED
                    || incomingStatus == DeploymentStatus.ROLLED_BACK)
                    && previousStatus == DeploymentStatus.SUCCESS) {
                deployment.setFailureRecoveredAt(null);
                deployment.setDeployedAt(eventTime);
            }
        }
        if (deployment.getDeployedAt() == null
                || (previousStatus == DeploymentStatus.PENDING && incomingStatus != DeploymentStatus.PENDING)) {
            deployment.setDeployedAt(eventTime);
        }
        deploymentRepository.save(deployment);
    }

    private PullRequestEntity requirePullRequest(Integer companyId, Integer eventRepoId, Integer githubPrId) {
        requireCompany(companyId);
        RepoReference repo = requireRepo(companyId, eventRepoId);
        if (githubPrId == null) {
            throw new InvalidMetricEventException("PR event is missing prId");
        }
        return pullRequestRepository.findByCompanyIdAndRepoIdAndGithubPrId(
                        companyId, repo.repoId(), githubPrId.longValue())
                .orElseThrow(() -> new InvalidMetricEventException(
                        "PR " + githubPrId + " was not opened before its state-change event"));
    }

    private RepoReference requireRepo(Integer companyId, Integer eventRepoId) {
        if (eventRepoId == null) {
            throw new InvalidMetricEventException("Metric event is missing repoId");
        }
        return referenceRepository.resolveRepo(companyId, eventRepoId)
                .orElseThrow(() -> new InvalidMetricEventException(
                        "No repository mapping for event repoId " + eventRepoId));
    }

    private void requireCompany(Integer companyId) {
        if (companyId == null || companyId < 1) {
            throw new InvalidMetricEventException("Metric event has no valid companyId");
        }
    }

    private String requireSha(String sha) {
        String value = nullableSha(sha);
        if (value == null) {
            throw new InvalidMetricEventException("commit.pushed is missing commitSha");
        }
        return value;
    }

    private String nullableSha(String sha) {
        if (sha == null || sha.isBlank()) {
            return null;
        }
        String value = sha.trim();
        if (value.length() > 40) {
            throw new InvalidMetricEventException("commitSha exceeds the 40-character schema limit");
        }
        return value;
    }

    private DeploymentStatus normalizeStatus(String status) {
        String value = blankToDefault(status, "pending").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "success" -> DeploymentStatus.SUCCESS;
            case "failure", "failed", "error" -> DeploymentStatus.FAILED;
            case "rolled_back", "inactive" -> DeploymentStatus.ROLLED_BACK;
            case "pending", "queued", "in_progress" -> DeploymentStatus.PENDING;
            default -> throw new InvalidMetricEventException("Unsupported deployment status: " + status);
        };
    }

    private String normalizeEnvironment(String environment) {
        String value = blankToDefault(environment, "production").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "prod", "production" -> "production";
            case "stage", "staging" -> "staging";
            case "dev", "development" -> "development";
            default -> throw new InvalidMetricEventException(
                    "Unsupported deployment environment: " + environment);
        };
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Instant timestamp(Instant value) {
        return value == null ? clock.instant() : value;
    }
}
