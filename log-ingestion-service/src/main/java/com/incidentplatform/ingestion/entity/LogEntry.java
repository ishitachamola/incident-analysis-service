package com.incidentplatform.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "logs")
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Producer-assigned identifier; the unique constraint on this column enforces idempotency. */
    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String level;

    @Column(name = "trace_id")
    private String traceId;

    @Column(nullable = false, length = 4000)
    private String message;

    @Column(length = 500)
    private String exception;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    protected LogEntry() {
    }

    public LogEntry(UUID eventId, String serviceName, String level, String traceId, String message,
                     String exception, Instant occurredAt) {
        this.eventId = eventId;
        this.serviceName = serviceName;
        this.level = level;
        this.traceId = traceId;
        this.message = message;
        this.exception = exception;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    void onCreate() {
        this.ingestedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getLevel() {
        return level;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getMessage() {
        return message;
    }

    public String getException() {
        return exception;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
