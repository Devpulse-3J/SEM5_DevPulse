package com.devpulse.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Integer ruleId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @Column(name = "threshold_hours")
    private Integer thresholdHours;

    @Column(name = "slack_channel", length = 255)
    private String slackChannel;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_by_user_id")
    private Integer createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AlertRule() {}

    public AlertRule(Integer companyId, Integer projectId, String ruleType, Integer thresholdHours, String slackChannel, Integer createdByUserId) {
        this.companyId = companyId;
        this.projectId = projectId;
        this.ruleType = ruleType;
        this.thresholdHours = thresholdHours;
        this.slackChannel = slackChannel;
        this.createdByUserId = createdByUserId;
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    public Integer getRuleId() { return ruleId; }
    public void setRuleId(Integer ruleId) { this.ruleId = ruleId; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public Integer getProjectId() { return projectId; }
    public void setProjectId(Integer projectId) { this.projectId = projectId; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public Integer getThresholdHours() { return thresholdHours; }
    public void setThresholdHours(Integer thresholdHours) { this.thresholdHours = thresholdHours; }

    public String getSlackChannel() { return slackChannel; }
    public void setSlackChannel(String slackChannel) { this.slackChannel = slackChannel; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Integer getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Integer createdByUserId) { this.createdByUserId = createdByUserId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
