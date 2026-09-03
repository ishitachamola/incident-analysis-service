package com.incidentplatform.incident.timeline;

import java.time.Instant;

/**
 * One line of an incident timeline.
 *
 * @param occurrences how many times a repeating log line occurred in the window; 1 for one-off
 *                    entries such as deployments
 * @param sourceRef   a stable reference back to the underlying record, so every timeline line can
 *                    be traced to its evidence
 */
public record TimelineEntry(
        Instant timestamp,
        String type,
        String summary,
        String level,
        String sourceRef,
        int occurrences
) {
}
