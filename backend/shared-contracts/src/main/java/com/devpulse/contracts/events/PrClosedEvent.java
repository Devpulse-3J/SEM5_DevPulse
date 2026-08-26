package com.devpulse.contracts.events;

import java.time.Instant;

public class PrClosedEvent extends BaseEvent {
    private Integer prId;
    private Integer repoId;
    private Instant closedAt;

    public PrClosedEvent() {
        super();
    }

    public PrClosedEvent(String eventId, Integer companyId, Integer projectId, Instant timestamp,
                         Integer prId, Integer repoId, Instant closedAt) {
        super(eventId, companyId, projectId, "pr.closed", timestamp);
        this.prId = prId;
        this.repoId = repoId;
        this.closedAt = closedAt;
    }

    public Integer getPrId() { return prId; }
    public void setPrId(Integer prId) { this.prId = prId; }

    public Integer getRepoId() { return repoId; }
    public void setRepoId(Integer repoId) { this.repoId = repoId; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}
