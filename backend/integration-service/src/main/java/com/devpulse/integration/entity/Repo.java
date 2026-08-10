package com.devpulse.integration.entity;

import jakarta.persistence.*;

/**
 * Entity representing a GitHub repository in PostgreSQL (repos table).
 */
@Entity
@Table(name = "repos", uniqueConstraints = {
    @UniqueConstraint(name = "uq_repos_company_github", columnNames = {"company_id", "github_repo_id"})
})
public class Repo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repo_id")
    private Integer repoId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "owner_name", nullable = false, length = 255)
    private String ownerName;

    @Column(name = "full_name", nullable = false, length = 511)
    private String fullName;

    @Column(name = "default_branch", nullable = false, length = 255)
    private String defaultBranch = "main";

    public Repo() {}

    public Repo(Integer companyId, Integer projectId, Long githubRepoId, String repoName, String ownerName, String fullName, String defaultBranch) {
        this.companyId = companyId;
        this.projectId = projectId;
        this.githubRepoId = githubRepoId;
        this.repoName = repoName;
        this.ownerName = ownerName;
        this.fullName = fullName;
        this.defaultBranch = defaultBranch != null ? defaultBranch : "main";
    }

    public Integer getRepoId() { return repoId; }
    public void setRepoId(Integer repoId) { this.repoId = repoId; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public Integer getProjectId() { return projectId; }
    public void setProjectId(Integer projectId) { this.projectId = projectId; }

    public Long getGithubRepoId() { return githubRepoId; }
    public void setGithubRepoId(Long githubRepoId) { this.githubRepoId = githubRepoId; }

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
}
