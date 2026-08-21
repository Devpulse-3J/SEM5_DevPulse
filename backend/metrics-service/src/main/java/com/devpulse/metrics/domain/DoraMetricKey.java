package com.devpulse.metrics.domain;

public enum DoraMetricKey {
    DEPLOYMENT_FREQUENCY("deploymentFrequency", "deployments/day"),
    LEAD_TIME("leadTime", "hours"),
    MTTR("mttr", "hours"),
    CHANGE_FAILURE_RATE("changeFailureRate", "%");

    private final String apiName;
    private final String unit;

    DoraMetricKey(String apiName, String unit) {
        this.apiName = apiName;
        this.unit = unit;
    }

    public String apiName() {
        return apiName;
    }

    public String unit() {
        return unit;
    }
}
