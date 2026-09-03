package com.incidentplatform.ingestion.api;

import com.incidentplatform.ingestion.entity.LogEntry;
import java.time.Instant;
import java.util.UUID;

public record LogEntryResponse(
        UUID id,
        String service,
        String level,
        String traceId,
        String message,
        String exception,
        Instant occurredAt
) {

    public static LogEntryResponse from(LogEntry entry) {
        return new LogEntryResponse(
                entry.getId(),
                entry.getServiceName(),
                entry.getLevel(),
                entry.getTraceId(),
                entry.getMessage(),
                entry.getException(),
                entry.getOccurredAt());
    }
}
