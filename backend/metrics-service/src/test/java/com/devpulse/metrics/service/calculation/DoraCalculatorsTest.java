package com.devpulse.metrics.service.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpulse.metrics.domain.DeploymentFact;
import com.devpulse.metrics.domain.DeploymentStatus;
import com.devpulse.metrics.domain.DoraMetricKey;
import com.devpulse.metrics.domain.DoraRating;
import com.devpulse.metrics.domain.MetricWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DoraCalculatorsTest {

    private Instant end;
    private MetricWindow window;
    private List<DeploymentFact> facts;

    @BeforeEach
    void setUp() {
        end = Instant.parse("2026-08-31T00:00:00Z");
        window = new MetricWindow(end.minus(30, ChronoUnit.DAYS), end, 30);
        facts = List.of(
                fact(DeploymentStatus.SUCCESS, 20, 48, null),
                fact(DeploymentStatus.SUCCESS, 10, 24, null),
                fact(DeploymentStatus.FAILED, 15, null, 6),
                fact(DeploymentStatus.ROLLED_BACK, 5, null, 18),
                fact(DeploymentStatus.PENDING, 3, null, null),
                new DeploymentFact(DeploymentStatus.SUCCESS,
                        end.minus(40, ChronoUnit.DAYS), null,
                        end.minus(41, ChronoUnit.DAYS)));
    }

    @Test
    void deploymentFrequencyCountsOnlySuccessfulProductionFactsInWindow() {
        var result = new DeploymentFrequencyCalculator().calculate(facts, window);

        assertThat(result.value()).isEqualByComparingTo("0.0667");
        assertThat(result.sampleSize()).isEqualTo(2);
    }

    @Test
    void leadTimeAveragesCommitToSuccessfulDeploymentInHours() {
        var result = new LeadTimeCalculator().calculate(facts, window);

        assertThat(result.value()).isEqualByComparingTo("36.00");
        assertThat(result.sampleSize()).isEqualTo(2);
    }

    @Test
    void changeFailureRateExcludesPendingDeployments() {
        var result = new ChangeFailureRateCalculator().calculate(facts, window);

        assertThat(result.value()).isEqualByComparingTo("0.5000");
        assertThat(result.sampleSize()).isEqualTo(4);
    }

    @Test
    void mttrAveragesOnlyRecoveredFailures() {
        var result = new MttrCalculator().calculate(facts, window);

        assertThat(result.value()).isEqualByComparingTo("12.00");
        assertThat(result.sampleSize()).isEqualTo(2);
    }

    @Test
    void unavailableMetricsAreNullInsteadOfMisleadingZeroes() {
        assertThat(new LeadTimeCalculator().calculate(List.of(), window).value()).isNull();
        assertThat(new ChangeFailureRateCalculator().calculate(List.of(), window).value()).isNull();
        assertThat(new MttrCalculator().calculate(List.of(), window).value()).isNull();
        assertThat(new DeploymentFrequencyCalculator().calculate(List.of(), window).value())
                .isEqualByComparingTo(BigDecimal.ZERO.setScale(4));
    }

    @Test
    void ratingPolicyUsesMetricDirectionAndHandlesMissingData() {
        DoraRatingPolicy policy = new DoraRatingPolicy();

        assertThat(policy.rate(DoraMetricKey.DEPLOYMENT_FREQUENCY, BigDecimal.ONE))
                .isEqualTo(DoraRating.ELITE);
        assertThat(policy.rate(DoraMetricKey.LEAD_TIME, new BigDecimal("800")))
                .isEqualTo(DoraRating.LOW);
        assertThat(policy.rate(DoraMetricKey.CHANGE_FAILURE_RATE, null))
                .isEqualTo(DoraRating.NOT_AVAILABLE);
    }

    private DeploymentFact fact(
            DeploymentStatus status, long daysBeforeEnd, Integer leadHours, Integer recoveryHours) {
        Instant deployedAt = end.minus(daysBeforeEnd, ChronoUnit.DAYS);
        Instant commitTime = leadHours == null ? null : deployedAt.minus(leadHours, ChronoUnit.HOURS);
        Instant recoveredAt = recoveryHours == null ? null : deployedAt.plus(recoveryHours, ChronoUnit.HOURS);
        return new DeploymentFact(status, deployedAt, recoveredAt, commitTime);
    }
}
