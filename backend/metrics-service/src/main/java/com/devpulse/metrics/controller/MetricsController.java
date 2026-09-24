package com.devpulse.metrics.controller;

import com.devpulse.metrics.dto.DeploymentResponse;
import com.devpulse.metrics.dto.DevExSummaryResponse;
import com.devpulse.metrics.dto.DoraSummaryResponse;
import com.devpulse.metrics.dto.PullRequestResponse;
import com.devpulse.metrics.dto.ReviewVelocitySummaryResponse;
import com.devpulse.metrics.dto.WorkloadEntryResponse;
import com.devpulse.metrics.exception.ApiException;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import com.devpulse.metrics.security.RequestContextResolver;
import com.devpulse.metrics.service.ActivityMetricsService;
import com.devpulse.metrics.service.DoraMetricsService;
import com.devpulse.metrics.service.DoraSnapshotScheduler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/metrics")
public class MetricsController {

    private final RequestContextResolver contextResolver;
    private final DoraMetricsService doraMetricsService;
    private final ActivityMetricsService activityMetricsService;
    private final ProjectAccessService projectAccessService;
    private final DoraSnapshotScheduler snapshotScheduler;

    public MetricsController(
            RequestContextResolver contextResolver,
            DoraMetricsService doraMetricsService,
            ActivityMetricsService activityMetricsService,
            ProjectAccessService projectAccessService,
            ObjectProvider<DoraSnapshotScheduler> snapshotSchedulerProvider) {
        this.contextResolver = contextResolver;
        this.doraMetricsService = doraMetricsService;
        this.activityMetricsService = activityMetricsService;
        this.projectAccessService = projectAccessService;
        this.snapshotScheduler = snapshotSchedulerProvider.getIfAvailable();
    }

    @GetMapping("/dora")
    public DoraSummaryResponse dora(
            HttpServletRequest request,
            @RequestParam @Positive Integer projectId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int windowDays,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int historyDays) {
        RequestContext context = contextResolver.resolve(request);
        return doraMetricsService.getSummary(context, projectId, windowDays, historyDays);
    }

    @GetMapping("/prs")
    public List<PullRequestResponse> pullRequests(
            HttpServletRequest request,
            @RequestParam(required = false) @Positive Integer projectId,
            @RequestParam(required = false) Boolean myPrs,
            @RequestParam(required = false) @Positive Integer authorId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        RequestContext context = contextResolver.resolve(request);
        Integer targetAuthorId = Boolean.TRUE.equals(myPrs) ? context.userId() : authorId;
        return activityMetricsService.getPullRequests(
                context, projectId, targetAuthorId, limit, offset);
    }

    @GetMapping("/deployments")
    public List<DeploymentResponse> deployments(
            HttpServletRequest request,
            @RequestParam @Positive Integer projectId,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return activityMetricsService.getDeployments(
                contextResolver.resolve(request), projectId, environment, status, limit, offset);
    }

    @GetMapping("/workload")
    public List<WorkloadEntryResponse> workload(
            HttpServletRequest request,
            @RequestParam @Positive Integer projectId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int windowDays) {
        return activityMetricsService.getWorkload(
                contextResolver.resolve(request), projectId, windowDays);
    }

    @GetMapping("/review-velocity")
    public ReviewVelocitySummaryResponse reviewVelocity(
            HttpServletRequest request,
            @RequestParam @Positive Integer projectId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int windowDays) {
        return activityMetricsService.getReviewVelocity(
                contextResolver.resolve(request), projectId, windowDays);
    }

    @GetMapping("/devex")
    public DevExSummaryResponse devex(
            HttpServletRequest request,
            @RequestParam @Positive Integer projectId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int windowDays) {
        return activityMetricsService.getDevExSummary(
                contextResolver.resolve(request), projectId, windowDays);
    }

    @PostMapping("/dora/snapshots/trigger")
    public DoraSnapshotScheduler.SnapshotExecutionResult triggerSnapshots(
            HttpServletRequest request,
            @RequestParam(required = false) @Positive Integer projectId,
            @RequestParam(required = false) @Min(1) @Max(365) Integer windowDays) {
        if (snapshotScheduler == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SNAPSHOTS_DISABLED",
                    "DORA snapshot scheduler is disabled by configuration");
        }
        RequestContext context = contextResolver.resolve(request);
        if (projectId != null) {
            projectAccessService.requireViewAccess(context, projectId);
        } else {
            projectAccessService.requireCompanyAccess(context);
        }
        return snapshotScheduler.triggerManualSnapshot(projectId, windowDays);
    }
}
