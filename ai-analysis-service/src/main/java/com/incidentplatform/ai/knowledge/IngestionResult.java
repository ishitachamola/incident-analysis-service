package com.incidentplatform.ai.knowledge;

public record IngestionResult(
        int documentsProcessed,
        int documentsSkippedUnchanged,
        int chunksEmbedded,
        long durationMillis
) {
}
