package com.incidentplatform.ai.llm;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Current consumption against every configured limit, for operators to check before spending. */
public record LlmUsageSnapshot(
        boolean enabled,
        boolean apiKeyConfigured,
        String activeModel,
        int maxConcurrentCalls,
        int callsInFlight,
        LocalDate quotaDay,
        String quotaZone,
        Instant dailyResetAt,
        List<ModelUsage> models
) {

    public record ModelUsage(
            String model,
            int requestsToday,
            int requestsPerDayLimit,
            int requestsLastMinute,
            int requestsPerMinuteLimit
    ) {
    }
}
