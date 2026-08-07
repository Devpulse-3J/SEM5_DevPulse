package com.devpulse.contracts.events;

import java.time.Instant;

public class CommitPushedEvent extends BaseEvent {
    private String commitSha;
    private Integer repoId;
    private Integer prId;
    private Integer authorId;
    private String message;
    private Instant commitTime;
    private int linesAdded;
    private int linesDeleted;

    public CommitPushedEvent() {
        super();
    }

    public CommitPushedEvent(String eventId, Integer companyId, Integer projectId, Instant timestamp,
                             String commitSha, Integer repoId, Integer prId, Integer authorId,
                             String message, Instant commitTime, int linesAdded, int linesDeleted) {
        super(eventId, companyId, projectId, "commit.pushed", timestamp);
        this.commitSha = commitSha;
        this.repoId = repoId;
        this.prId = prId;
        this.authorId = authorId;
        this.message = message;
        this.commitTime = commitTime;
        this.linesAdded = linesAdded;
        this.linesDeleted = linesDeleted;
    }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

    public Integer getRepoId() { return repoId; }
    public void setRepoId(Integer repoId) { this.repoId = repoId; }

    public Integer getPrId() { return prId; }
    public void setPrId(Integer prId) { this.prId = prId; }

    public Integer getAuthorId() { return authorId; }
    public void setAuthorId(Integer authorId) { this.authorId = authorId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getCommitTime() { return commitTime; }
    public void setCommitTime(Instant commitTime) { this.commitTime = commitTime; }

    public int getLinesAdded() { return linesAdded; }
    public void setLinesAdded(int linesAdded) { this.linesAdded = linesAdded; }

    public int getLinesDeleted() { return linesDeleted; }
    public void setLinesDeleted(int linesDeleted) { this.linesDeleted = linesDeleted; }
}
