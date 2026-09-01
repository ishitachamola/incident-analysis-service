package com.incidentplatform.incident.dto;

import com.incidentplatform.incident.entity.Incident;
import com.incidentplatform.incident.entity.IncidentSeverity;
import com.incidentplatform.incident.entity.IncidentStatus;
import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        UUID serviceId,
        String serviceName,
        String title,
        IncidentSeverity severity,
        IncidentStatus status,
        Instant detectedAt,
        Instant resolvedAt
) {

    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getService().getId(),
                incident.getService().getName(),
                incident.getTitle(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getDetectedAt(),
                incident.getResolvedAt());
    }
}
