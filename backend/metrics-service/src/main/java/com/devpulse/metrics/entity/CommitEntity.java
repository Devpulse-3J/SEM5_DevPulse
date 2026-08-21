package com.devpulse.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "commits")
public class CommitEntity {

    @Id
    @Column(name = "commit_sha", length = 40)
    private String commitSha;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "repo_id", nullable = false)
    private Integer repoId;

    @Column(name = "pr_id")
    private Integer prId;

    @Column(name = "author_id")
    private Integer authorId;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "commit_time", nullable = false)
    private Instant commitTime;

    @Column(name = "lines_added", nullable = false)
    private int linesAdded;

    @Column(name = "lines_deleted", nullable = false)
    private int linesDeleted;

    protected CommitEntity() {
    }

    public CommitEntity(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getCommitSha() { return commitSha; }
    public Integer getRepoId() { return repoId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }
    public void setRepoId(Integer repoId) { this.repoId = repoId; }
    public void setPrId(Integer prId) { this.prId = prId; }
    public void setAuthorId(Integer authorId) { this.authorId = authorId; }
    public void setMessage(String message) { this.message = message; }
    public void setCommitTime(Instant commitTime) { this.commitTime = commitTime; }
    public void setLinesAdded(int linesAdded) { this.linesAdded = linesAdded; }
    public void setLinesDeleted(int linesDeleted) { this.linesDeleted = linesDeleted; }
}
