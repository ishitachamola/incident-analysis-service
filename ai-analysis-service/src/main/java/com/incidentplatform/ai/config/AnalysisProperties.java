package com.incidentplatform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds on how much evidence goes into one prompt. They keep each call's token cost predictable
 * and well under the provider's per-minute token allowance, whatever the incident looks like.
 */
@ConfigurationProperties(prefix = "ai.analysis")
public record AnalysisProperties(
        int maxTimelineEntries,
        int maxEntrySummaryChars,
        int maxDocumentChars,
        int maxEvidenceChars,
        int runbookTopK,
        int incidentTopK
) {

    public AnalysisProperties {
        maxTimelineEntries = positiveOr(maxTimelineEntries, 40);
        maxEntrySummaryChars = positiveOr(maxEntrySummaryChars, 300);
        maxDocumentChars = positiveOr(maxDocumentChars, 2500);
        maxEvidenceChars = positiveOr(maxEvidenceChars, 24_000);
        runbookTopK = positiveOr(runbookTopK, 3);
        incidentTopK = positiveOr(incidentTopK, 4);
    }

    private static int positiveOr(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
