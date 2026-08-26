package com.devpulse.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Integer alertId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "rule_id")
    private Integer ruleId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Integer entityId;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "warning";

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "triggered_at", nullable = false, updatable = false)
    private Instant triggeredAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Alert() {}

    public Alert(Integer companyId, Integer projectId, Integer ruleId, String entityType, Integer entityId, String severity, String message) {
        this.companyId = companyId;
        this.projectId = projectId;
        this.ruleId = ruleId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.severity = severity != null ? severity : "warning";
        this.message = message;
        this.triggeredAt = Instant.now();
    }

    public Integer getAlertId() { return alertId; }
    public void setAlertId(Integer alertId) { this.alertId = alertId; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public Integer getProjectId() { return projectId; }
    public void setProjectId(Integer projectId) { this.projectId = projectId; }

    public Integer getRuleId() { return ruleId; }
    public void setRuleId(Integer ruleId) { this.ruleId = ruleId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Integer getEntityId() { return entityId; }
    public void setEntityId(Integer entityId) { this.entityId = entityId; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
