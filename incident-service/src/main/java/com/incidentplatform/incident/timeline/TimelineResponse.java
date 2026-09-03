package com.incidentplatform.incident.timeline;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param logsAvailable false when the log service could not be reached, so a caller can tell an
 *                      incident with no logs apart from a timeline that is missing its log evidence
 */
public record TimelineResponse(
        UUID incidentId,
        String service,
        Instant windowStart,
        Instant windowEnd,
        boolean logsAvailable,
        List<TimelineEntry> entries
) {
}
