package com.devpulse.contracts.events;

import java.time.Instant;

public class PrMergedEvent extends BaseEvent {
    private Integer prId;
    private Integer repoId;
    private Instant mergedAt;

    public PrMergedEvent() {
        super();
    }

    public PrMergedEvent(String eventId, Integer companyId, Integer projectId, Instant timestamp,
                         Integer prId, Integer repoId, Instant mergedAt) {
        super(eventId, companyId, projectId, "pr.merged", timestamp);
        this.prId = prId;
        this.repoId = repoId;
        this.mergedAt = mergedAt;
    }

    public Integer getPrId() { return prId; }
    public void setPrId(Integer prId) { this.prId = prId; }

    public Integer getRepoId() { return repoId; }
    public void setRepoId(Integer repoId) { this.repoId = repoId; }

    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }
}
