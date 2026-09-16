package com.incidentplatform.ai.llm;

import java.util.Locale;

public record LlmResult(
        String text,
        String model,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs
) {

    /** True when generation stopped at the output token cap, so the text is incomplete. */
    public boolean truncated() {
        if (finishReason == null) {
            return false;
        }
        String reason = finishReason.toLowerCase(Locale.ROOT);
        return reason.equals("length") || reason.equals("max_tokens");
    }
}
