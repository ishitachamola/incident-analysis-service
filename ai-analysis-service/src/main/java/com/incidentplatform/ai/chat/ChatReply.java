package com.incidentplatform.ai.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param sources          references the reply cited that exist in the evidence
 * @param removedCitations references it cited that do not exist, replaced in the reply text
 */
public record ChatReply(
        UUID id,
        UUID incidentId,
        String reply,
        List<String> sources,
        List<String> removedCitations,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs,
        Instant createdAt
) {
}
