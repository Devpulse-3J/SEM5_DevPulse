package com.devpulse.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "pull_requests")
public class PullRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pr_id")
    private Integer id;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "repo_id", nullable = false)
    private Integer repoId;

    @Column(name = "github_pr_id")
    private Long githubPrId;

    @Column(name = "github_pr_number", nullable = false)
    private Integer githubPrNumber;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "author_id")
    private Integer authorId;

    @Column(name = "base_branch", nullable = false)
    private String baseBranch = "main";

    @Column(name = "is_draft", nullable = false)
    private boolean draft;

    @Column(nullable = false)
    private String state = "open";

    @Column(name = "lines_added", nullable = false)
    private int linesAdded;

    @Column(name = "lines_deleted", nullable = false)
    private int linesDeleted;

    @Column(name = "files_changed", nullable = false)
    private int filesChanged;

    @Column(name = "author_association", length = 30)
    private String authorAssociation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PullRequestEntity() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Integer getId() { return id; }
    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }
    public Integer getRepoId() { return repoId; }
    public void setRepoId(Integer repoId) { this.repoId = repoId; }
    public Long getGithubPrId() { return githubPrId; }
    public void setGithubPrId(Long githubPrId) { this.githubPrId = githubPrId; }
    public Integer getGithubPrNumber() { return githubPrNumber; }
    public void setGithubPrNumber(Integer githubPrNumber) { this.githubPrNumber = githubPrNumber; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public void setAuthorId(Integer authorId) { this.authorId = authorId; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
    public void setDraft(boolean draft) { this.draft = draft; }
    public void setState(String state) { this.state = state; }
    public void setLinesAdded(int linesAdded) { this.linesAdded = linesAdded; }
    public void setLinesDeleted(int linesDeleted) { this.linesDeleted = linesDeleted; }
    public void setFilesChanged(int filesChanged) { this.filesChanged = filesChanged; }
    public String getAuthorAssociation() { return authorAssociation; }
    public void setAuthorAssociation(String authorAssociation) { this.authorAssociation = authorAssociation; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}
