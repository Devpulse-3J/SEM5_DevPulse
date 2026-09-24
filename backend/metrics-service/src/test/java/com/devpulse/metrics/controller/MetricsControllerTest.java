package com.devpulse.metrics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpulse.metrics.dto.DevExSummaryResponse;
import com.devpulse.metrics.dto.ReviewVelocitySummaryResponse;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import com.devpulse.metrics.security.RequestContextResolver;
import com.devpulse.metrics.service.ActivityMetricsService;
import com.devpulse.metrics.service.DoraMetricsService;
import com.devpulse.metrics.service.DoraSnapshotScheduler;
import com.devpulse.metrics.service.DoraSnapshotScheduler.SnapshotExecutionResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.Mockito;

class MetricsControllerTest {

    private MockMvc mockMvc;
    private RequestContextResolver contextResolver;
    private DoraMetricsService doraMetricsService;
    private ActivityMetricsService activityMetricsService;
    private ProjectAccessService projectAccessService;
    private DoraSnapshotScheduler snapshotScheduler;

    @BeforeEach
    void setUp() {
        contextResolver = Mockito.mock(RequestContextResolver.class);
        doraMetricsService = Mockito.mock(DoraMetricsService.class);
        activityMetricsService = Mockito.mock(ActivityMetricsService.class);
        projectAccessService = Mockito.mock(ProjectAccessService.class);
        snapshotScheduler = Mockito.mock(DoraSnapshotScheduler.class);

        when(contextResolver.resolve(any())).thenReturn(new RequestContext(1, 10));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("doraSnapshotScheduler", snapshotScheduler);

        MetricsController controller = new MetricsController(
                contextResolver,
                doraMetricsService,
                activityMetricsService,
                projectAccessService,
                beanFactory.getBeanProvider(DoraSnapshotScheduler.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getReviewVelocityReturnsVelocitySummary() throws Exception {
        ReviewVelocitySummaryResponse summary = new ReviewVelocitySummaryResponse(
                "5", 30, Instant.now(), 10L, 8L, BigDecimal.valueOf(80.0),
                BigDecimal.valueOf(3.5), BigDecimal.valueOf(3.0),
                BigDecimal.valueOf(1.8), BigDecimal.valueOf(4.2), List.of());

        when(activityMetricsService.getReviewVelocity(any(), eq(5), eq(30))).thenReturn(summary);

        mockMvc.perform(get("/metrics/review-velocity")
                        .param("projectId", "5")
                        .param("windowDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("5"))
                .andExpect(jsonPath("$.totalPullRequests").value(10))
                .andExpect(jsonPath("$.averageTtfrHours").value(3.5));
    }

    @Test
    void getDevExReturnsTeamHealthSummary() throws Exception {
        DevExSummaryResponse response = new DevExSummaryResponse(
                "5", 30, Instant.now(), BigDecimal.valueOf(88.5),
                "HEALTHY", BigDecimal.valueOf(2.4), BigDecimal.valueOf(1.1),
                4L, 0L, 1L, List.of());

        when(activityMetricsService.getDevExSummary(any(), eq(5), eq(30))).thenReturn(response);

        mockMvc.perform(get("/metrics/devex")
                        .param("projectId", "5")
                        .param("windowDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("5"))
                .andExpect(jsonPath("$.overallTeamHealth").value("HEALTHY"))
                .andExpect(jsonPath("$.overallDevExScore").value(88.5));
    }

    @Test
    void triggerSnapshotsCallsSchedulerAndReturnsResult() throws Exception {
        SnapshotExecutionResult result = new SnapshotExecutionResult(1, 4, 4, 0, Instant.now());
        when(snapshotScheduler.triggerManualSnapshot(eq(5), eq(30))).thenReturn(result);

        mockMvc.perform(post("/metrics/dora/snapshots/trigger")
                        .param("projectId", "5")
                        .param("windowDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectsProcessed").value(1))
                .andExpect(jsonPath("$.totalSnapshotsAttempted").value(4))
                .andExpect(jsonPath("$.successfulSnapshots").value(4));
    }
}
