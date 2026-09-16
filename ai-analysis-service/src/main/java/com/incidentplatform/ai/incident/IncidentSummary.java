package com.incidentplatform.ai.incident;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IncidentSummary(
        UUID id,
        UUID serviceId,
        String serviceName,
        String title,
        String severity,
        String status,
        Instant detectedAt,
        Instant resolvedAt
) {
}
