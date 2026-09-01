package com.incidentplatform.incident.dto;

import com.incidentplatform.incident.entity.IncidentEvent;
import java.time.Instant;
import java.util.UUID;

public record IncidentEventResponse(
        UUID id,
        UUID incidentId,
        String type,
        String description,
        Instant occurredAt,
        String sourceRef
) {

    public static IncidentEventResponse from(IncidentEvent event) {
        return new IncidentEventResponse(
                event.getId(),
                event.getIncident().getId(),
                event.getType(),
                event.getDescription(),
                event.getOccurredAt(),
                event.getSourceRef());
    }
}
