package com.devpulse.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devpulse.metrics.dto.PullRequestResponse;
import com.devpulse.metrics.dto.PullRequestResponse.RiskAnalysisResponse;
import com.devpulse.metrics.exception.ApiException;
import com.devpulse.metrics.repository.ActivityQueryRepository;
import com.devpulse.metrics.repository.ActivityQueryRepository.PredictionRow;
import com.devpulse.metrics.repository.ActivityQueryRepository.PullRequestRow;
import com.devpulse.metrics.security.ProjectAccessService;
import com.devpulse.metrics.security.RequestContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * The PR list carries each PR's latest stored prediction, so the frontend can
 * label every row "high / medium / low risk" for managers and developers alike.
 * A PR that has not been scored gets null, never a made-up value.
 */
@ExtendWith(MockitoExtension.class)
class ActivityMetricsServicePullRequestsTest {

    private static final Integer COMPANY_ID = 15;
    private static final Integer PROJECT_ID = 8;
    private static final Instant PREDICTED_AT = Instant.parse("2026-09-21T11:49:45Z");

    @Mock
    private ProjectAccessService accessService;

    @Mock
    private ActivityQueryRepository queryRepository;

    private ActivityMetricsService service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new ActivityMetricsService(accessService, queryRepository, Clock.systemUTC(), 4);
        context = new RequestContext(1, COMPANY_ID);
    }

    private static PullRequestRow row(int prId, int number, String title) {
        return new PullRequestRow(prId, number, title, "body", 1, "Didula", null,
                3, "SEM5_DevPulse", false, "open", "feature/x", "main",
                10, 2, 1, "https://github.com/o/r/pull/" + number,
                Instant.parse("2026-09-21T11:49:42Z"), null, null);
    }

    private static PredictionRow prediction(int prId, double score, String category) {
        return new PredictionRow(prId, score, category, "xgboost", "1.0.0", PREDICTED_AT);
    }

    private void givenRows(PullRequestRow... rows) {
        when(queryRepository.findPullRequests(COMPANY_ID, PROJECT_ID, null, 100, 0)).thenReturn(List.of(rows));
        when(queryRepository.findReviews(any())).thenReturn(List.of());
        when(queryRepository.findChecks(any())).thenReturn(List.of());
    }

    @Test
    void aScoredPullRequestCarriesItsRiskLevelAndPercentage() {
        givenRows(row(37, 37, "docs: a comment"));
        when(queryRepository.findLatestPredictions(COMPANY_ID, List.of(37)))
                .thenReturn(List.of(prediction(37, 0.7933, "high")));

        List<PullRequestResponse> result = service.getPullRequests(context, PROJECT_ID, null, 100, 0);

        RiskAnalysisResponse risk = result.get(0).riskAnalysis();
        assertThat(risk).isNotNull();
        assertThat(risk.riskLevel()).isEqualTo("HIGH");
        assertThat(risk.riskScore()).isEqualTo(79.3);
        assertThat(risk.algorithm()).isEqualTo("xgboost");
        assertThat(risk.modelVersion()).isEqualTo("1.0.0");
        assertThat(risk.predictedAt()).isEqualTo(PREDICTED_AT);
        assertThat(risk.summary()).contains("79%").contains("xgboost").contains("1.0.0");
        assertThat(risk.factors()).isEmpty();
    }

    @Test
    void aPullRequestThatHasNotBeenScoredHasNoRiskAnalysis() {
        givenRows(row(38, 38, "not scored yet"));
        when(queryRepository.findLatestPredictions(COMPANY_ID, List.of(38))).thenReturn(List.of());

        assertThat(service.getPullRequests(context, PROJECT_ID, null, 100, 0).get(0).riskAnalysis()).isNull();
    }

    @Test
    void eachPullRequestGetsItsOwnPrediction() {
        givenRows(row(1, 1, "a"), row(2, 2, "b"), row(3, 3, "c"));
        when(queryRepository.findLatestPredictions(COMPANY_ID, List.of(1, 2, 3)))
                .thenReturn(List.of(prediction(1, 0.15, "low"), prediction(3, 0.55, "medium")));

        List<PullRequestResponse> result = service.getPullRequests(context, PROJECT_ID, null, 100, 0);

        assertThat(result.get(0).riskAnalysis().riskLevel()).isEqualTo("LOW");
        assertThat(result.get(1).riskAnalysis()).isNull();
        assertThat(result.get(2).riskAnalysis().riskLevel()).isEqualTo("MEDIUM");
        assertThat(result.get(2).riskAnalysis().riskScore()).isEqualTo(55.0);
    }

    @Test
    void predictionsAreLookedUpOnceForTheWholePageAndScopedToTheCompany() {
        givenRows(row(1, 1, "a"), row(2, 2, "b"));
        when(queryRepository.findLatestPredictions(anyInt(), any())).thenReturn(List.of());

        service.getPullRequests(context, PROJECT_ID, null, 100, 0);

        verify(queryRepository).findLatestPredictions(COMPANY_ID, List.of(1, 2));
    }

    @Test
    void anAccessFailureStopsBeforeAnyPredictionIsRead() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "not a member"))
                .when(accessService).requireViewAccess(context, PROJECT_ID);

        assertThatThrownBy(() -> service.getPullRequests(context, PROJECT_ID, null, 100, 0))
                .isInstanceOf(ApiException.class);

        verify(queryRepository, never()).findLatestPredictions(anyInt(), any());
    }

    @Test
    void theRiskLevelIsTheModelsCategoryInUpperCase() {
        assertThat(ActivityMetricsService.toRiskAnalysis(prediction(1, 0.2, "low")).riskLevel())
                .isEqualTo("LOW");
        assertThat(ActivityMetricsService.toRiskAnalysis(prediction(1, 0.5, "medium")).riskLevel())
                .isEqualTo("MEDIUM");
        assertThat(ActivityMetricsService.toRiskAnalysis(prediction(1, 0.9, "high")).riskLevel())
                .isEqualTo("HIGH");
    }

    @Test
    void theScoreIsAPercentageRoundedToOneDecimal() {
        assertThat(ActivityMetricsService.toRiskAnalysis(prediction(1, 0.4353, "medium")).riskScore())
                .isEqualTo(43.5);
        assertThat(ActivityMetricsService.toRiskAnalysis(prediction(1, 1.0, "high")).riskScore())
                .isEqualTo(100.0);
        assertThat(ActivityMetricsService.toRiskAnalysis(prediction(1, 0.0, "low")).riskScore())
                .isEqualTo(0.0);
    }

    @Test
    void noPredictionMapsToNull() {
        assertThat(ActivityMetricsService.toRiskAnalysis(null)).isNull();
    }
}
