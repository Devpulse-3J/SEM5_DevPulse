package com.devpulse.contracts.events;

import java.time.Instant;

/**
 * Published by auth-service when an admin creates a project.
 * <p>
 * When {@code githubRepoUrl} is set, integration-service reacts by resolving
 * the URL to an owner/repo pair and running a historical GitHub sync for it.
 * The URL is carried raw: auth-service does not parse GitHub URLs.
 */
public class ProjectCreatedEvent extends BaseEvent {
    private String projectName;
    private String githubRepoUrl;
    private Integer createdByUserId;

    public ProjectCreatedEvent() {
        super();
    }

    public ProjectCreatedEvent(String eventId, Integer companyId, Integer projectId, Instant timestamp,
                               String projectName, String githubRepoUrl, Integer createdByUserId) {
        super(eventId, companyId, projectId, "project.created", timestamp);
        this.projectName = projectName;
        this.githubRepoUrl = githubRepoUrl;
        this.createdByUserId = createdByUserId;
    }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getGithubRepoUrl() { return githubRepoUrl; }
    public void setGithubRepoUrl(String githubRepoUrl) { this.githubRepoUrl = githubRepoUrl; }

    public Integer getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Integer createdByUserId) { this.createdByUserId = createdByUserId; }
}
