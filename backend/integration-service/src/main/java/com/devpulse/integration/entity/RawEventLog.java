package com.devpulse.integration.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing an incoming raw webhook event log in PostgreSQL (raw_event_log table).
 */
@Entity
@Table(name = "raw_event_log")
public class RawEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Integer eventId;

    @Column(name = "company_id", nullable = false)
    private Integer companyId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_error")
    private String processingError;

    public RawEventLog() {}

    public RawEventLog(Integer companyId, String provider, String eventType, String payload) {
        this.companyId = companyId;
        this.provider = provider;
        this.eventType = eventType;
        this.payload = payload;
        this.receivedAt = Instant.now();
    }

    public Integer getEventId() { return eventId; }
    public void setEventId(Integer eventId) { this.eventId = eventId; }

    public Integer getCompanyId() { return companyId; }
    public void setCompanyId(Integer companyId) { this.companyId = companyId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getProcessingError() { return processingError; }
    public void setProcessingError(String processingError) { this.processingError = processingError; }
}
