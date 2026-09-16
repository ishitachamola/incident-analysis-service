package com.incidentplatform.ai.config;

import java.time.ZoneId;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every limit that governs calls to the hosted model.
 *
 * <p>Limits are configured per model because providers quota each model separately and the
 * allowances differ by an order of magnitude (20 versus 500 requests a day on the free tier).
 * Configured values should sit below the provider's own limits so the service refuses a request
 * itself, cheaply and with a clear message, rather than spending it only to be rejected upstream.
 *
 * @param enabled          master switch; when false no request is ever sent
 * @param model            the model every analysis call uses
 * @param maxOutputTokens  cap on generated tokens per call, including a thinking model's reasoning
 * @param maxConcurrentCalls calls allowed in flight at once; extra requests are refused, not queued
 * @param maxRepairAttempts follow-up calls allowed to correct invalid output; each counts against quota
 * @param quotaZone        time zone in which the provider's daily quota resets
 * @param defaultLimits    applied to any model without an explicit entry, deliberately conservative
 * @param models           per-model limits; in YAML, keys containing dots must be bracketed,
 *                         for example {@code "[gemini-3.5-flash-lite]"}, or Spring splits them
 */
@ConfigurationProperties(prefix = "ai.llm")
public record LlmProperties(
        boolean enabled,
        String model,
        int maxOutputTokens,
        double temperature,
        int maxConcurrentCalls,
        int maxRepairAttempts,
        ZoneId quotaZone,
        ModelLimits defaultLimits,
        Map<String, ModelLimits> models
) {

    public LlmProperties {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.llm.model must be set");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("ai.llm.max-output-tokens must be positive");
        }
        if (maxConcurrentCalls <= 0) {
            throw new IllegalArgumentException("ai.llm.max-concurrent-calls must be positive");
        }
        if (maxRepairAttempts < 0) {
            throw new IllegalArgumentException("ai.llm.max-repair-attempts cannot be negative");
        }
        quotaZone = quotaZone != null ? quotaZone : ZoneId.of("America/Los_Angeles");
        defaultLimits = defaultLimits != null ? defaultLimits : new ModelLimits(2, 10);
        models = models != null ? Map.copyOf(models) : Map.of();
    }

    public ModelLimits limitsFor(String modelName) {
        return models.getOrDefault(modelName, defaultLimits);
    }

    public record ModelLimits(int requestsPerMinute, int requestsPerDay) {

        public ModelLimits {
            if (requestsPerMinute <= 0 || requestsPerDay <= 0) {
                throw new IllegalArgumentException("model limits must be positive");
            }
        }
    }
}
