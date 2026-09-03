package com.incidentplatform.incident.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Set when the incident was opened from a detection event; null for manually raised incidents. */
    @Column(name = "event_id", unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "detection_rule", updatable = false)
    private String detectionRule;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Incident() {
    }

    public Incident(MonitoredService service, String title, IncidentSeverity severity, IncidentStatus status,
                     Instant detectedAt) {
        this.service = service;
        this.title = title;
        this.severity = severity;
        this.status = status;
        this.detectedAt = detectedAt;
    }

    public Incident(MonitoredService service, String title, IncidentSeverity severity, IncidentStatus status,
                     Instant detectedAt, UUID eventId, String detectionRule) {
        this(service, title, severity, status, detectedAt);
        this.eventId = eventId;
        this.detectionRule = detectionRule;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getDetectionRule() {
        return detectionRule;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public MonitoredService getService() {
        return service;
    }

    public String getTitle() {
        return title;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IncidentSeverity severity) {
        this.severity = severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
