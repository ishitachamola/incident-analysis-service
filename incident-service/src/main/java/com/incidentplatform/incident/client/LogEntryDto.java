package com.incidentplatform.incident.client;

import java.time.Instant;
import java.util.UUID;

/** A log entry as returned by the log ingestion service's query API. */
public record LogEntryDto(
        UUID id,
        String service,
        String level,
        String traceId,
        String message,
        String exception,
        Instant occurredAt
) {
}
