package com.incidentplatform.incident.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record CreateIncidentEventRequest(
        @NotBlank String type,
        @NotBlank String description,
        Instant occurredAt,
        String sourceRef
) {
}
