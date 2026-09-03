package com.incidentplatform.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a threshold rule fires against the ingested log stream.
 *
 * <p>This is deliberately a statement of <em>observation</em>, not of cause: it reports what
 * breached and over which window. Determining why is the analysis service's job.
 */
public record IncidentDetectedEvent(
        UUID eventId,
        String service,
        String title,
        String severity,
        Instant detectedAt,
        Instant windowStart,
        Instant windowEnd,
        long errorCount,
        long totalCount,
        double errorRate,
        String detectionRule
) {
}
