package com.incidentplatform.ai.analysis;

import java.time.Instant;
import java.util.UUID;

public record StoredAnalysis(
        UUID id,
        UUID incidentId,
        String model,
        String evidenceFingerprint,
        AnalysisResult result,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs,
        int modelCalls,
        Instant createdAt
) {
}
