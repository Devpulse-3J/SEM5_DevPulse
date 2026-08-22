package com.devpulse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /projects/{id}}.
 *
 * <p>A full replacement, not a patch: an omitted optional field clears the
 * stored value. That keeps "the admin deleted the description" expressible,
 * which a partial update cannot do without a separate null sentinel.
 */
public class UpdateProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(min = 3, max = 100,
            message = "Project name must be between 3 and 100 characters")
    private String projectName;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @Size(max = 50, message = "Jira project key must be at most 50 characters")
    private String jiraProjectKey;

    @Size(max = 512, message = "GitHub repo URL must be at most 512 characters")
    @Pattern(
            regexp = "^$|^(https?://)?(www\\.)?github\\.com/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(\\.git)?/?$",
            message = "GitHub repo URL must look like https://github.com/<owner>/<repo>")
    private String githubRepoUrl;

    // -- constructors --------------------------------------------------------

    public UpdateProjectRequest() {
    }

    public UpdateProjectRequest(String projectName, String description,
                                String jiraProjectKey, String githubRepoUrl) {
        this.projectName = projectName;
        this.description = description;
        this.jiraProjectKey = jiraProjectKey;
        this.githubRepoUrl = githubRepoUrl;
    }

    // -- getters / setters ---------------------------------------------------

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
}
