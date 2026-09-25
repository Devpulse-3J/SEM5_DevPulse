package com.devpulse.metrics.controller;

import com.devpulse.metrics.dto.DeploymentResponse;
import com.devpulse.metrics.dto.DoraSummaryResponse;
import com.devpulse.metrics.dto.PullRequestResponse;
import com.devpulse.metrics.dto.RebuildSnapshotsResponse;
import com.devpulse.metrics.dto.WorkloadEntryResponse;
import com.devpulse.metrics.security.RequestContext;
import com.devpulse.metrics.security.RequestContextResolver;
import com.devpulse.metrics.service.ActivityMetricsService;
import com.devpulse.metrics.service.DoraMetricsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
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

    public MetricsController(
            RequestContextResolver contextResolver,
            DoraMetricsService doraMetricsService,
            ActivityMetricsService activityMetricsService) {
        this.contextResolver = contextResolver;
        this.doraMetricsService = doraMetricsService;
        this.activityMetricsService = activityMetricsService;
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

    /**
     * Admin-only: recalculates the last {@code days} daily DORA snapshots for a
     * project from the data as it is now. See {@link DoraMetricsService#rebuildHistory}.
     */
    @PostMapping("/dora/snapshots/rebuild")
    public RebuildSnapshotsResponse rebuildSnapshots(
            HttpServletRequest request,
            @RequestParam @Positive Integer projectId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int windowDays) {
        RequestContext context = contextResolver.resolve(request);
        return new RebuildSnapshotsResponse(
                doraMetricsService.rebuildHistory(context, projectId, days, windowDays));
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
}
