package com.devpulse.metrics.service;

import com.devpulse.metrics.dto.DeploymentResponse;
import com.devpulse.metrics.dto.PullRequestResponse;
import com.devpulse.metrics.dto.PullRequestResponse.CheckResponse;
import com.devpulse.metrics.dto.PullRequestResponse.ReviewResponse;
import com.devpulse.metrics.dto.WorkloadEntryResponse;
import com.devpulse.metrics.exception.ApiException;
import com.devpulse.metrics.repository.ActivityQueryRepository;
import com.devpulse.metrics.repository.ActivityQueryRepository.CheckRow;
import com.devpulse.metrics.repository.ActivityQueryRepository.PullRequestCycleFact;
import com.devpulse.metrics.repository.ActivityQueryRepository.ReviewRow;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityMetricsService {

    private static final BigDecimal MILLIS_PER_HOUR = BigDecimal.valueOf(3_600_000);
    private static final Set<String> ENVIRONMENTS = Set.of("development", "staging", "production");
    private static final Set<String> DEPLOYMENT_STATUSES = Set.of("pending", "success", "failed", "rolled_back");

    private final ProjectAccessService accessService;
    private final ActivityQueryRepository queryRepository;
    private final Clock clock;
    private final int targetActivePullRequests;

    public ActivityMetricsService(
            ProjectAccessService accessService,
            ActivityQueryRepository queryRepository,
            Clock clock,
            @Value("${devpulse.metrics.workload.target-active-prs:4}") int targetActivePullRequests) {
        this.accessService = accessService;
        this.queryRepository = queryRepository;
        this.clock = clock;
        this.targetActivePullRequests = Math.max(1, targetActivePullRequests);
    }

    @Transactional(readOnly = true)
    public List<PullRequestResponse> getPullRequests(
            RequestContext context, Integer projectId, int limit, int offset) {
        accessService.requireViewAccess(context, projectId);
        var rows = queryRepository.findPullRequests(context.companyId(), projectId, limit, offset);
        List<Integer> ids = rows.stream().map(row -> row.id()).toList();
        Map<Integer, List<ReviewRow>> reviews = queryRepository.findReviews(ids).stream()
                .collect(Collectors.groupingBy(ReviewRow::prId));
        Map<Integer, List<CheckRow>> checks = queryRepository.findChecks(ids).stream()
                .collect(Collectors.groupingBy(CheckRow::prId));
        return rows.stream().map(row -> new PullRequestResponse(
                row.id().toString(),
                row.number(),
                row.title(),
                row.description(),
                row.authorName() == null ? "Unknown" : row.authorName(),
                row.authorAvatar(),
                row.repositoryId().toString(),
                row.repositoryName(),
                row.draft() ? "draft" : row.state(),
                row.headBranch(),
                row.baseBranch(),
                row.additions(),
                row.deletions(),
                row.changedFiles(),
                row.url(),
                row.createdAt(),
                row.updatedAt(),
                row.mergedAt(),
                reviews.getOrDefault(row.id(), List.of()).stream()
                        .map(review -> new ReviewResponse(
                                review.id().toString(),
                                review.reviewerName() == null ? "Unknown" : review.reviewerName(),
                                review.reviewerAvatar(),
                                review.state(),
                                review.submittedAt()))
                        .toList(),
                checks.getOrDefault(row.id(), List.of()).stream()
                        .map(check -> new CheckResponse(
                                check.id().toString(), check.name(), check.status(), check.url()))
                        .toList(),
                null)).toList();
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getDeployments(
            RequestContext context, Integer projectId, String environment, String status,
            int limit, int offset) {
        accessService.requireViewAccess(context, projectId);
        String normalizedEnvironment = validateFilter("environment", normalize(environment), ENVIRONMENTS);
        String normalizedStatus = validateFilter("status", normalize(status), DEPLOYMENT_STATUSES);
        return queryRepository.findDeployments(
                        context.companyId(), projectId, normalizedEnvironment, normalizedStatus, limit, offset)
                .stream().map(row -> new DeploymentResponse(
                        row.id().toString(),
                        row.githubDeploymentId() == null ? null : row.githubDeploymentId().toString(),
                        row.commitSha(),
                        row.environment(),
                        row.status(),
                        row.deployedAt(),
                        row.failureRecoveredAt(),
                        row.triggeredByUserId() == null ? null : row.triggeredByUserId().toString(),
                        row.triggeredByName(),
                        durationHours(row.commitTime(), row.deployedAt())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkloadEntryResponse> getWorkload(
            RequestContext context, Integer projectId, int windowDays) {
        accessService.requireViewAccess(context, projectId);
        var members = queryRepository.findProjectMembers(context.companyId(), projectId);
        Map<Integer, List<PullRequestCycleFact>> factsByAuthor = queryRepository
                .findPullRequestCycleFacts(
                        context.companyId(), projectId, clock.instant().minus(windowDays, ChronoUnit.DAYS))
                .stream()
                .filter(fact -> fact.authorId() != null)
                .collect(Collectors.groupingBy(PullRequestCycleFact::authorId));
        return members.stream().map(member -> {
            List<PullRequestCycleFact> facts = factsByAuthor.getOrDefault(member.userId(), List.of());
            long activePrs = facts.stream().filter(fact -> "open".equals(fact.state())).count();
            BigDecimal loadPct = BigDecimal.valueOf(activePrs)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(targetActivePullRequests), 2, RoundingMode.HALF_UP);
            List<BigDecimal> cycleTimes = facts.stream()
                    .filter(fact -> "merged".equals(fact.state()))
                    .map(fact -> durationHours(fact.createdAt(), fact.mergedAt()))
                    .filter(value -> value != null)
                    .toList();
            BigDecimal cycleTime = cycleTimes.isEmpty() ? null : cycleTimes.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(cycleTimes.size()), 2, RoundingMode.HALF_UP);
            return new WorkloadEntryResponse(
                    member.userId().toString(), member.name(), activePrs, loadPct, cycleTime);
        }).toList();
    }

    private BigDecimal durationHours(java.time.Instant start, java.time.Instant end) {
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        return BigDecimal.valueOf(Duration.between(start, end).toMillis())
                .divide(MILLIS_PER_HOUR, 2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private String validateFilter(String name, String value, Set<String> allowed) {
        if (value != null && !allowed.contains(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER",
                    "Unsupported " + name + " value: " + value);
        }
        return value;
    }
}
