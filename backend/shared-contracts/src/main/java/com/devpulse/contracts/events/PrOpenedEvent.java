package com.devpulse.contracts.events;

import java.time.Instant;

public class PrOpenedEvent extends BaseEvent {
    private Integer prId;
    private Integer repoId;
    private Integer githubPrNumber;
    private String title;
    private Integer authorId;
    private String baseBranch;
    private boolean draft;
    private int linesAdded;
    private int linesDeleted;
    private int filesChanged;
    private String authorAssociation;
    /** The PR description ("body" in GitHub's payload). Null when omitted. */
    private String body;

    public PrOpenedEvent() {
        super();
    }

    public PrOpenedEvent(String eventId, Integer companyId, Integer projectId, Instant timestamp,
                         Integer prId, Integer repoId, Integer githubPrNumber, String title,
                         Integer authorId, String baseBranch, boolean draft,
                         int linesAdded, int linesDeleted, int filesChanged) {
        this(eventId, companyId, projectId, timestamp, prId, repoId, githubPrNumber, title,
                authorId, baseBranch, draft, linesAdded, linesDeleted, filesChanged, null);
    }

    public PrOpenedEvent(String eventId, Integer companyId, Integer projectId, Instant timestamp,
                         Integer prId, Integer repoId, Integer githubPrNumber, String title,
                         Integer authorId, String baseBranch, boolean draft,
                         int linesAdded, int linesDeleted, int filesChanged,
                         String authorAssociation) {
        super(eventId, companyId, projectId, "pr.opened", timestamp);
        this.prId = prId;
        this.repoId = repoId;
        this.githubPrNumber = githubPrNumber;
        this.title = title;
        this.authorId = authorId;
        this.baseBranch = baseBranch;
        this.draft = draft;
        this.linesAdded = linesAdded;
        this.linesDeleted = linesDeleted;
        this.filesChanged = filesChanged;
        this.authorAssociation = authorAssociation;
    }

    public Integer getPrId() { return prId; }
    public void setPrId(Integer prId) { this.prId = prId; }

    public Integer getRepoId() { return repoId; }
    public void setRepoId(Integer repoId) { this.repoId = repoId; }

    public Integer getGithubPrNumber() { return githubPrNumber; }
    public void setGithubPrNumber(Integer githubPrNumber) { this.githubPrNumber = githubPrNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getAuthorId() { return authorId; }
    public void setAuthorId(Integer authorId) { this.authorId = authorId; }

    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }

    public boolean isDraft() { return draft; }
    public void setDraft(boolean draft) { this.draft = draft; }

    public int getLinesAdded() { return linesAdded; }
    public void setLinesAdded(int linesAdded) { this.linesAdded = linesAdded; }

    public int getLinesDeleted() { return linesDeleted; }
    public void setLinesDeleted(int linesDeleted) { this.linesDeleted = linesDeleted; }

    public int getFilesChanged() { return filesChanged; }
    public void setFilesChanged(int filesChanged) { this.filesChanged = filesChanged; }

    public String getAuthorAssociation() { return authorAssociation; }
    public void setAuthorAssociation(String authorAssociation) { this.authorAssociation = authorAssociation; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
