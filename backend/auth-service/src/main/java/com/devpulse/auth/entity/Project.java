package com.devpulse.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * JPA entity mapped to the {@code projects} table.
 * <p>
 * A project belongs to exactly one company and may be linked to a GitHub
 * repository ({@code github_repo_url}) and/or a Jira project
 * ({@code jira_project_key}). The GitHub URL is stored verbatim — resolving it
 * to an owner/repo pair is integration-service's job, not auth-service's.
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Integer projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "project_name", nullable = false, length = 255)
    private String projectName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "jira_project_key", length = 50)
    private String jiraProjectKey;

    @Column(name = "github_repo_url", length = 512)
    private String githubRepoUrl;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    // -- constructors --------------------------------------------------------

    public Project() {
    }

    public Project(Company company, String projectName, String description,
                   String jiraProjectKey, String githubRepoUrl) {
        this.company = company;
        this.projectName = projectName;
        this.description = description;
        this.jiraProjectKey = jiraProjectKey;
        this.githubRepoUrl = githubRepoUrl;
        this.createdAt = OffsetDateTime.now();
    }

    // -- getters / setters ---------------------------------------------------

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
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

    public String getJiraProjectKey() {
        return jiraProjectKey;
    }

    public void setJiraProjectKey(String jiraProjectKey) {
        this.jiraProjectKey = jiraProjectKey;
    }

    public String getGithubRepoUrl() {
        return githubRepoUrl;
    }

    public void setGithubRepoUrl(String githubRepoUrl) {
        this.githubRepoUrl = githubRepoUrl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
