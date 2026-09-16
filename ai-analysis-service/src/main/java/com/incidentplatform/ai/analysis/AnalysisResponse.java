package com.incidentplatform.ai.analysis;

import java.time.Instant;
import java.util.UUID;

/**
 * @param cached     true when this analysis was served from storage without calling the model
 * @param modelCalls how many provider requests producing it cost, including any repair call
 */
public record AnalysisResponse(
        UUID id,
        UUID incidentId,
        String model,
        boolean cached,
        int modelCalls,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs,
        Instant createdAt,
        AnalysisResult analysis
) {

    public static AnalysisResponse from(StoredAnalysis stored, boolean cached) {
        return new AnalysisResponse(
                stored.id(), stored.incidentId(), stored.model(), cached, stored.modelCalls(),
                stored.promptTokens(), stored.completionTokens(), stored.latencyMs(), stored.createdAt(),
                stored.result());
    }
}
