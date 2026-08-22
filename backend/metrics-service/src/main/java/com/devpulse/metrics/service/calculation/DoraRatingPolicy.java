package com.devpulse.metrics.service.calculation;

import com.devpulse.metrics.domain.DoraMetricKey;
import com.devpulse.metrics.domain.DoraRating;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Central policy object so performance-band thresholds cannot drift between endpoints. */
@Component
public class DoraRatingPolicy {

    public DoraRating rate(DoraMetricKey key, BigDecimal value) {
        if (value == null) {
            return DoraRating.NOT_AVAILABLE;
        }
        return switch (key) {
            case DEPLOYMENT_FREQUENCY -> higherIsBetter(value,
                    new BigDecimal("1.0000"), new BigDecimal("0.1429"), new BigDecimal("0.0333"));
            case LEAD_TIME -> lowerIsBetter(value,
                    new BigDecimal("24"), new BigDecimal("168"), new BigDecimal("720"));
            case MTTR -> lowerIsBetter(value,
                    new BigDecimal("1"), new BigDecimal("24"), new BigDecimal("168"));
            case CHANGE_FAILURE_RATE -> lowerIsBetter(value,
                    new BigDecimal("0.05"), new BigDecimal("0.10"), new BigDecimal("0.15"));
        };
    }

    private DoraRating higherIsBetter(
            BigDecimal value, BigDecimal elite, BigDecimal high, BigDecimal medium) {
        if (value.compareTo(elite) >= 0) return DoraRating.ELITE;
        if (value.compareTo(high) >= 0) return DoraRating.HIGH;
        if (value.compareTo(medium) >= 0) return DoraRating.MEDIUM;
        return DoraRating.LOW;
    }

    private DoraRating lowerIsBetter(
            BigDecimal value, BigDecimal elite, BigDecimal high, BigDecimal medium) {
        if (value.compareTo(elite) <= 0) return DoraRating.ELITE;
        if (value.compareTo(high) <= 0) return DoraRating.HIGH;
        if (value.compareTo(medium) <= 0) return DoraRating.MEDIUM;
        return DoraRating.LOW;
    }
}
