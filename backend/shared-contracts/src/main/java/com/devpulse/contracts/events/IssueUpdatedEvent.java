package com.devpulse.contracts.events;

import java.time.Instant;

public class IssueUpdatedEvent extends BaseEvent {
    private Integer issueId;
    private String jiraKey;
    private String summary;
    private String issueType;
    private String priority;
    private String status;
    private Integer storyPoints;
    private Integer assigneeId;
    private Instant updatedAt;

    public IssueUpdatedEvent() {
        super();
    }

    public IssueUpdatedEvent(String eventId, Integer companyId, Integer projectId, Instant timestamp,
                             Integer issueId, String jiraKey, String summary, String issueType,
                             String priority, String status, Integer storyPoints,
                             Integer assigneeId, Instant updatedAt) {
        super(eventId, companyId, projectId, "issue.updated", timestamp);
        this.issueId = issueId;
        this.jiraKey = jiraKey;
        this.summary = summary;
        this.issueType = issueType;
        this.priority = priority;
        this.status = status;
        this.storyPoints = storyPoints;
        this.assigneeId = assigneeId;
        this.updatedAt = updatedAt;
    }

    public Integer getIssueId() { return issueId; }
    public void setIssueId(Integer issueId) { this.issueId = issueId; }

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

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
