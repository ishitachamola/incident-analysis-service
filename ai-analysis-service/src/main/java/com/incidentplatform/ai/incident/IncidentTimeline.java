package com.incidentplatform.ai.incident;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An incident timeline as served by the incident service.
 *
 * @param logsAvailable false when the incident service could not reach the log service, meaning log
 *                      evidence is missing rather than genuinely absent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IncidentTimeline(
        UUID incidentId,
        String service,
        Instant windowStart,
        Instant windowEnd,
        boolean logsAvailable,
        List<Entry> entries
) {

    public IncidentTimeline {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            Instant timestamp,
            String type,
            String summary,
            String level,
            String sourceRef,
            int occurrences
    ) {
    }
}
