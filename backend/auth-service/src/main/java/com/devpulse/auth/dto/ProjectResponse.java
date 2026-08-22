package com.devpulse.auth.dto;

import com.devpulse.auth.entity.Project;
import java.time.OffsetDateTime;

/**
 * API view of a {@link Project}. Entities are never returned directly.
 */
public class ProjectResponse {

    private Integer projectId;
    private Integer companyId;
    private String projectName;
    private String description;
    private String githubRepoUrl;
    private String jiraProjectKey;
    private OffsetDateTime createdAt;

    public ProjectResponse() {
    }

    public static ProjectResponse from(Project project) {
        ProjectResponse response = new ProjectResponse();
        response.projectId = project.getProjectId();
        response.companyId = project.getCompany() != null ? project.getCompany().getCompanyId() : null;
        response.projectName = project.getProjectName();
        response.description = project.getDescription();
        response.githubRepoUrl = project.getGithubRepoUrl();
        response.jiraProjectKey = project.getJiraProjectKey();
        response.createdAt = project.getCreatedAt();
        return response;
    }

    // -- getters / setters ---------------------------------------------------

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public Integer getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Integer companyId) {
        this.companyId = companyId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGithubRepoUrl() {
        return githubRepoUrl;
    }

    public void setGithubRepoUrl(String githubRepoUrl) {
        this.githubRepoUrl = githubRepoUrl;
    }

    public String getJiraProjectKey() {
        return jiraProjectKey;
    }

    public void setJiraProjectKey(String jiraProjectKey) {
        this.jiraProjectKey = jiraProjectKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
