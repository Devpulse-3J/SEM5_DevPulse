package com.devpulse.contracts.events;

import java.time.Instant;

/**
 * Base metadata included with all events published to RabbitMQ.
 */
public abstract class BaseEvent {
    private String eventId;
    private Integer companyId;
    private Integer projectId;
    private String eventType;
    private Instant timestamp;

    public BaseEvent() {}

    public BaseEvent(String eventId, Integer companyId, Integer projectId, String eventType, Instant timestamp) {
        this.eventId = eventId;
        this.companyId = companyId;
        this.projectId = projectId;
        this.eventType = eventType;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public Integer getProjectId() { return projectId; }
    public void setProjectId(Integer projectId) { this.projectId = projectId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
