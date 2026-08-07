package com.devpulse.contracts.events;

import java.time.Instant;

public class AlertPrHighRiskEvent extends BaseEvent {
    private Integer predictionId;
    private Integer prId;
    private String algorithm;
    private String modelVersion;
    private String riskCategory;
    private double riskScore;
    private Double confidence;
    private Instant predictedAt;

    public AlertPrHighRiskEvent() {
        super();
    }

    public AlertPrHighRiskEvent(String eventId, Integer companyId, Integer projectId, Instant timestamp,
                                Integer predictionId, Integer prId, String algorithm,
                                String modelVersion, String riskCategory, double riskScore,
                                Double confidence, Instant predictedAt) {
        super(eventId, companyId, projectId, "alert.pr_high_risk", timestamp);
        this.predictionId = predictionId;
        this.prId = prId;
        this.algorithm = algorithm;
        this.modelVersion = modelVersion;
        this.riskCategory = riskCategory;
        this.riskScore = riskScore;
        this.confidence = confidence;
        this.predictedAt = predictedAt;
    }

    public Integer getPredictionId() { return predictionId; }
    public void setPredictionId(Integer predictionId) { this.predictionId = predictionId; }

    public Integer getPrId() { return prId; }
    public void setPrId(Integer prId) { this.prId = prId; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getRiskCategory() { return riskCategory; }
    public void setRiskCategory(String riskCategory) { this.riskCategory = riskCategory; }

    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Instant getPredictedAt() { return predictedAt; }
    public void setPredictedAt(Instant predictedAt) { this.predictedAt = predictedAt; }
}
