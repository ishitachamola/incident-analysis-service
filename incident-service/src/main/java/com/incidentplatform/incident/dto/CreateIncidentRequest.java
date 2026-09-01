package com.incidentplatform.incident.dto;

import com.incidentplatform.incident.entity.IncidentSeverity;
import com.incidentplatform.incident.entity.IncidentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateIncidentRequest(
        @NotNull UUID serviceId,
        @NotBlank String title,
        @NotNull IncidentSeverity severity,
        IncidentStatus status,
        Instant detectedAt
) {
}
