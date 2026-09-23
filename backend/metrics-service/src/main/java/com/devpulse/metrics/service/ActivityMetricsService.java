package com.devpulse.metrics.service;

import com.devpulse.metrics.dto.DeploymentResponse;
import com.devpulse.metrics.dto.DevExSummaryResponse;
import com.devpulse.metrics.dto.PullRequestResponse;
import com.devpulse.metrics.dto.PullRequestResponse.CheckResponse;
import com.devpulse.metrics.dto.PullRequestResponse.ReviewResponse;
import com.devpulse.metrics.dto.PullRequestResponse.RiskAnalysisResponse;
import com.devpulse.metrics.dto.ReviewVelocitySummaryResponse;
import com.devpulse.metrics.dto.ReviewVelocitySummaryResponse.PrVelocityDetail;
import com.devpulse.metrics.dto.WorkloadEntryResponse;
import com.devpulse.metrics.exception.ApiException;
import com.devpulse.metrics.repository.ActivityQueryRepository;
import com.devpulse.metrics.repository.ActivityQueryRepository.CheckRow;
import com.devpulse.metrics.repository.ActivityQueryRepository.PredictionRow;
import com.devpulse.metrics.repository.ActivityQueryRepository.PullRequestCycleFact;
import com.devpulse.metrics.repository.ActivityQueryRepository.ReviewRow;
import com.devpulse.metrics.repository.ActivityQueryRepository.ReviewVelocityFact;
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
            RequestContext context, Integer projectId, Integer authorId, int limit, int offset) {
        if (projectId != null) {
            accessService.requireViewAccess(context, projectId);
        } else {
            accessService.requireCompanyAccess(context);
        }
        var rows = queryRepository.findPullRequests(context.companyId(), projectId, authorId, limit, offset);
        List<Integer> ids = rows.stream().map(row -> row.id()).toList();
        Map<Integer, List<ReviewRow>> reviews = queryRepository.findReviews(ids).stream()
                .collect(Collectors.groupingBy(ReviewRow::prId));
        Map<Integer, List<CheckRow>> checks = queryRepository.findChecks(ids).stream()
                .collect(Collectors.groupingBy(CheckRow::prId));
        Map<Integer, PredictionRow> predictions = queryRepository
                .findLatestPredictions(context.companyId(), ids).stream()
                .collect(Collectors.toMap(PredictionRow::prId, prediction -> prediction, (a, b) -> a));
        return rows.stream().map(row -> {
            List<ReviewRow> prReviews = reviews.getOrDefault(row.id(), List.of());
            java.time.Instant firstReviewTime = row.firstReviewAt();
            if (firstReviewTime == null && !prReviews.isEmpty()) {
                firstReviewTime = prReviews.stream()
                        .map(ReviewRow::submittedAt)
                        .filter(t -> t != null)
                        .min(java.time.Instant::compareTo)
                        .orElse(null);
            }
            BigDecimal ttfrHours = durationHours(row.createdAt(), firstReviewTime);
            int reviewIterations = prReviews.size();
            java.time.Instant resolutionTime = row.mergedAt();
            if (resolutionTime == null && ("closed".equalsIgnoreCase(row.state()) || "merged".equalsIgnoreCase(row.state()))) {
                resolutionTime = row.updatedAt();
            }
            BigDecimal turnaroundHours = (firstReviewTime != null && resolutionTime != null)
                    ? durationHours(firstReviewTime, resolutionTime)
                    : null;

            return new PullRequestResponse(
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
                    prReviews.stream()
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
                    toRiskAnalysis(predictions.get(row.id())),
                    ttfrHours,
                    reviewIterations,
                    turnaroundHours);
        }).toList();
    }

    /** The prediction as the frontend shows it, or null for a PR that has not been scored. */
    static RiskAnalysisResponse toRiskAnalysis(PredictionRow prediction) {
        if (prediction == null) {
            return null;
        }
        double percent = Math.round(prediction.riskScore() * 1000.0) / 10.0;
        String level = prediction.riskCategory() == null
                ? "MEDIUM" : prediction.riskCategory().toUpperCase();
        String summary = String.format(
                "The model estimates a %.0f%% chance this pull request goes stale (%s v%s).",
                percent, prediction.algorithm(), prediction.modelVersion());
        return new RiskAnalysisResponse(percent, level, summary, List.of(),
                prediction.algorithm(), prediction.modelVersion(), prediction.predictedAt());
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
        java.time.Instant windowStart = clock.instant().minus(windowDays, ChronoUnit.DAYS);
        var members = queryRepository.findProjectMembers(context.companyId(), projectId);
        Map<Integer, List<PullRequestCycleFact>> factsByAuthor = queryRepository
                .findPullRequestCycleFacts(
                        context.companyId(), projectId, windowStart)
                .stream()
                .filter(fact -> fact.authorId() != null)
                .collect(Collectors.groupingBy(PullRequestCycleFact::authorId));

        Map<Integer, Long> reviewsByUser = queryRepository.countCompletedReviewsByUser(
                context.companyId(), projectId, windowStart);
        Map<Integer, Long> reposByUser = queryRepository.countActiveRepositoriesByUser(
                context.companyId(), projectId, windowStart);

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

            long completedReviews = reviewsByUser.getOrDefault(member.userId(), 0L);
            long activeRepos = reposByUser.getOrDefault(member.userId(), activePrs > 0 ? 1L : 0L);

            long totalPrsTouched = facts.size();
            BigDecimal reviewBurden = BigDecimal.valueOf(completedReviews)
                    .divide(BigDecimal.valueOf(Math.max(1L, totalPrsTouched)), 2, RoundingMode.HALF_UP);

            // Context Switching Index: 0.0 - 10.0 scale
            double rawCsi = (activeRepos * 1.5) + (activePrs * 0.8) + (completedReviews * 0.15);
            BigDecimal csi = BigDecimal.valueOf(Math.min(10.0, Math.max(0.0, rawCsi))).setScale(2, RoundingMode.HALF_UP);

            // Workload Balance Status
            String workloadStatus;
            if (loadPct.compareTo(BigDecimal.valueOf(150.0)) > 0 || csi.compareTo(BigDecimal.valueOf(7.5)) >= 0) {
                workloadStatus = "OVERLOADED";
            } else if (loadPct.compareTo(BigDecimal.valueOf(40.0)) < 0 && completedReviews == 0) {
                workloadStatus = "UNDERUTILIZED";
            } else {
                workloadStatus = "OPTIMAL";
            }

            // DevEx Score (0 - 100)
            double loadPenalty = Math.min(40.0, Math.abs(loadPct.doubleValue() - 100.0) * 0.3);
            double csiPenalty = csi.doubleValue() * 3.5;
            double score = Math.max(10.0, Math.min(100.0, 100.0 - loadPenalty - csiPenalty));
            BigDecimal devexScore = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);

            return new WorkloadEntryResponse(
                    member.userId().toString(),
                    member.name(),
                    activePrs,
                    loadPct,
                    cycleTime,
                    completedReviews,
                    activeRepos,
                    csi,
                    reviewBurden,
                    workloadStatus,
                    devexScore);
        }).toList();
    }

    @Transactional(readOnly = true)
    public DevExSummaryResponse getDevExSummary(
            RequestContext context, Integer projectId, int windowDays) {
        accessService.requireViewAccess(context, projectId);
        java.time.Instant now = clock.instant();
        List<WorkloadEntryResponse> workload = getWorkload(context, projectId, windowDays);

        long optimalCount = workload.stream().filter(w -> "OPTIMAL".equals(w.workloadStatus())).count();
        long overloadedCount = workload.stream().filter(w -> "OVERLOADED".equals(w.workloadStatus())).count();
        long underutilizedCount = workload.stream().filter(w -> "UNDERUTILIZED".equals(w.workloadStatus())).count();

        List<BigDecimal> devexScores = workload.stream().map(WorkloadEntryResponse::devexScore).filter(s -> s != null).toList();
        BigDecimal overallDevExScore = average(devexScores);
        if (overallDevExScore == null) {
            overallDevExScore = BigDecimal.valueOf(100.0).setScale(2, RoundingMode.HALF_UP);
        }

        List<BigDecimal> csiList = workload.stream().map(WorkloadEntryResponse::contextSwitchingIndex).filter(s -> s != null).toList();
        BigDecimal avgCsi = average(csiList);

        List<BigDecimal> reviewBurdens = workload.stream().map(WorkloadEntryResponse::reviewBurdenRatio).filter(s -> s != null).toList();
        BigDecimal teamReviewBurden = average(reviewBurdens);

        String health;
        if (overallDevExScore.compareTo(BigDecimal.valueOf(80.0)) >= 0 && overloadedCount == 0) {
            health = "HEALTHY";
        } else if (overloadedCount > workload.size() / 3) {
            health = "BURNOUT_RISK";
        } else {
            health = "MODERATE";
        }

        return new DevExSummaryResponse(
                projectId.toString(),
                windowDays,
                now,
                overallDevExScore,
                health,
                avgCsi,
                teamReviewBurden,
                optimalCount,
                overloadedCount,
                underutilizedCount,
                workload);
    }

    @Transactional(readOnly = true)
    public ReviewVelocitySummaryResponse getReviewVelocity(
            RequestContext context, Integer projectId, int windowDays) {
        accessService.requireViewAccess(context, projectId);
        java.time.Instant now = clock.instant();
        java.time.Instant windowStart = now.minus(windowDays, ChronoUnit.DAYS);
        List<ReviewVelocityFact> facts = queryRepository.findReviewVelocityFacts(
                context.companyId(), projectId, windowStart);

        long totalPrs = facts.size();
        java.util.List<PrVelocityDetail> details = new java.util.ArrayList<>();
        java.util.List<BigDecimal> ttfrList = new java.util.ArrayList<>();
        java.util.List<BigDecimal> turnaroundList = new java.util.ArrayList<>();
        int totalIterations = 0;

        for (ReviewVelocityFact fact : facts) {
            java.time.Instant firstReview = fact.firstReviewAt() != null ? fact.firstReviewAt() : fact.minReviewedAt();
            BigDecimal ttfr = durationHours(fact.createdAt(), firstReview);
            if (ttfr != null) {
                ttfrList.add(ttfr);
            }

            java.time.Instant resolution = fact.mergedAt() != null ? fact.mergedAt() : fact.closedAt();
            BigDecimal turnaround = (firstReview != null && resolution != null)
                    ? durationHours(firstReview, resolution)
                    : null;
            if (turnaround != null) {
                turnaroundList.add(turnaround);
            }

            totalIterations += fact.reviewCount();

            details.add(new PrVelocityDetail(
                    fact.prId(),
                    fact.prNumber(),
                    fact.title(),
                    fact.state(),
                    fact.createdAt(),
                    firstReview,
                    fact.reviewCount(),
                    ttfr,
                    turnaround));
        }

        long reviewedPrs = ttfrList.size();
        BigDecimal reviewCoveragePct = totalPrs == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(reviewedPrs)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalPrs), 2, RoundingMode.HALF_UP);

        BigDecimal avgTtfr = average(ttfrList);
        BigDecimal medianTtfr = median(ttfrList);
        BigDecimal avgIterations = totalPrs == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalIterations)
                        .divide(BigDecimal.valueOf(totalPrs), 2, RoundingMode.HALF_UP);
        BigDecimal avgTurnaround = average(turnaroundList);

        return new ReviewVelocitySummaryResponse(
                projectId.toString(),
                windowDays,
                now,
                totalPrs,
                reviewedPrs,
                reviewCoveragePct,
                avgTtfr,
                medianTtfr,
                avgIterations,
                avgTurnaround,
                details);
    }

    private BigDecimal average(java.util.List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal median(java.util.List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        java.util.List<BigDecimal> sorted = new java.util.ArrayList<>(values);
        java.util.Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return sorted.get((size / 2) - 1).add(sorted.get(size / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
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
