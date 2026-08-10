package com.devpulse.integration.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing a Jira issue in PostgreSQL (jira_issues table).
 */
@Entity
@Table(name = "jira_issues", uniqueConstraints = {
    @UniqueConstraint(name = "uq_jira_issues_company_key", columnNames = {"company_id", "jira_key"})
})
public class JiraIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Integer issueId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "jira_key", nullable = false, length = 50)
    private String jiraKey;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "issue_type", length = 50)
    private String issueType;

    @Column(name = "priority", length = 50)
    private String priority;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "To Do";

    @Column(name = "story_points")
    private Integer storyPoints;

    @Column(name = "assignee_id")
    private Integer assigneeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    public JiraIssue() {}

    public JiraIssue(Integer companyId, Integer projectId, String jiraKey, String summary, String issueType, String priority, String status, Integer storyPoints, Integer assigneeId) {
        this.companyId = companyId;
        this.projectId = projectId;
        this.jiraKey = jiraKey;
        this.summary = summary;
        this.issueType = issueType;
        this.priority = priority;
        this.status = status != null ? status : "To Do";
        this.storyPoints = storyPoints;
        this.assigneeId = assigneeId;
        this.createdAt = Instant.now();
    }

    public Integer getIssueId() { return issueId; }
    public void setIssueId(Integer issueId) { this.issueId = issueId; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public Integer getProjectId() { return projectId; }
    public void setProjectId(Integer projectId) { this.projectId = projectId; }

    public String getJiraKey() { return jiraKey; }
    public void setJiraKey(String jiraKey) { this.jiraKey = jiraKey; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getStoryPoints() { return storyPoints; }
    public void setStoryPoints(Integer storyPoints) { this.storyPoints = storyPoints; }

    public Integer getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Integer assigneeId) { this.assigneeId = assigneeId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}
