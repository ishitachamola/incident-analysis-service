package com.incidentplatform.ai.llm;

import com.incidentplatform.ai.config.LlmProperties;
import com.incidentplatform.ai.config.LlmProperties.ModelLimits;
import com.incidentplatform.ai.llm.LlmQuotaExceededException.LimitType;
import com.incidentplatform.ai.llm.LlmUsageRepository.CallOutcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The single gate every model call must pass before anything is sent to the provider.
 *
 * <p>Checks run cheapest first, and a request that fails any of them is refused locally, logged,
 * and never reaches the provider:
 * <ol>
 *   <li>calls are enabled and an API key is configured, so no request is sent that is certain to fail;</li>
 *   <li>a concurrency slot is free, so repeated clicks cannot burst past the per-minute limit;</li>
 *   <li>the model's per-minute limit has room;</li>
 *   <li>a slot can be taken from the model's daily budget, held in the database so it survives restarts.</li>
 * </ol>
 *
 * <p>A daily slot is spent when the call is permitted, not when it succeeds. The provider does not
 * document whether failed requests count against its quota, so the guard assumes they do.
 *
 * <p>The per-minute window is held in memory, which is correct for a single instance. Running several
 * instances would need it moved to shared storage; the daily budget already is shared.
 */
@Component
public class LlmCallGuard {

    private static final Logger log = LoggerFactory.getLogger(LlmCallGuard.class);
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Set<String> PLACEHOLDER_KEYS = Set.of("changeme", "not-configured", "sk-changeme");

    private final LlmProperties properties;
    private final LlmUsageRepository repository;
    private final Clock clock;
    private final boolean apiKeyConfigured;
    private final Semaphore inFlight;
    private final Map<String, Deque<Instant>> recentCalls = new HashMap<>();

    public LlmCallGuard(LlmProperties properties, LlmUsageRepository repository, Clock clock,
                        @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.properties = properties;
        this.repository = repository;
        this.clock = clock;
        this.apiKeyConfigured = isConfigured(apiKey);
        this.inFlight = new Semaphore(properties.maxConcurrentCalls());
    }

    /**
     * @throws LlmUnavailableException   calls are disabled or no API key is configured
     * @throws LlmQuotaExceededException a concurrency, per-minute or daily limit would be exceeded
     */
    public LlmPermit acquire(String model, String purpose) {
        if (!properties.enabled()) {
            refuse(model, purpose, CallOutcome.REJECTED_DISABLED, "ai.llm.enabled is false");
            throw new LlmUnavailableException("AI analysis is switched off (ai.llm.enabled=false).");
        }
        if (!apiKeyConfigured) {
            refuse(model, purpose, CallOutcome.REJECTED_NOT_CONFIGURED, "no API key configured");
            throw new LlmUnavailableException("No model API key is configured. Set GEMINI_API_KEY and restart.");
        }
        if (!inFlight.tryAcquire()) {
            refuse(model, purpose, CallOutcome.REJECTED_CONCURRENCY, "all concurrency slots in use");
            throw new LlmQuotaExceededException(LimitType.CONCURRENCY, model, 5,
                    "Another analysis is already running. Retry in a few seconds.");
        }

        boolean permitIssued = false;
        try {
            reserveRateAndDailySlots(model, purpose);
            permitIssued = true;
            return new LlmPermit(model, purpose, repository, inFlight::release);
        } finally {
            if (!permitIssued) {
                inFlight.release();
            }
        }
    }

    private synchronized void reserveRateAndDailySlots(String model, String purpose) {
        ModelLimits limits = properties.limitsFor(model);
        Instant now = clock.instant();

        Deque<Instant> window = recentCalls.computeIfAbsent(model, key -> new ArrayDeque<>());
        pruneOlderThanOneMinute(window, now);
        if (window.size() >= limits.requestsPerMinute()) {
            long retryAfter = Duration.between(now, window.peekFirst().plus(ONE_MINUTE)).toSeconds() + 1;
            refuse(model, purpose, CallOutcome.REJECTED_PER_MINUTE,
                    "per-minute limit " + limits.requestsPerMinute() + " reached");
            throw new LlmQuotaExceededException(LimitType.PER_MINUTE, model, retryAfter,
                    "Per-minute limit of %d requests for %s reached. Retry in %d seconds."
                            .formatted(limits.requestsPerMinute(), model, retryAfter));
        }

        if (!repository.tryReserveDailySlot(quotaDay(now), model, limits.requestsPerDay())) {
            long retryAfter = Duration.between(now, nextReset(now)).toSeconds();
            refuse(model, purpose, CallOutcome.REJECTED_PER_DAY,
                    "daily limit " + limits.requestsPerDay() + " reached");
            throw new LlmQuotaExceededException(LimitType.PER_DAY, model, retryAfter,
                    "Daily limit of %d requests for %s reached. It resets at midnight %s."
                            .formatted(limits.requestsPerDay(), model, properties.quotaZone()));
        }

        // Only a call that cleared every check occupies the per-minute window.
        window.addLast(now);
    }

    public synchronized LlmUsageSnapshot snapshot() {
        Instant now = clock.instant();
        LocalDate day = quotaDay(now);

        Set<String> modelNames = new LinkedHashSet<>();
        modelNames.add(properties.model());
        modelNames.addAll(properties.models().keySet());

        List<LlmUsageSnapshot.ModelUsage> models = new ArrayList<>();
        for (String model : modelNames) {
            ModelLimits limits = properties.limitsFor(model);
            Deque<Instant> window = recentCalls.getOrDefault(model, new ArrayDeque<>());
            pruneOlderThanOneMinute(window, now);
            models.add(new LlmUsageSnapshot.ModelUsage(
                    model, repository.dailyCount(day, model), limits.requestsPerDay(),
                    window.size(), limits.requestsPerMinute()));
        }

        return new LlmUsageSnapshot(
                properties.enabled(), apiKeyConfigured, properties.model(),
                properties.maxConcurrentCalls(), properties.maxConcurrentCalls() - inFlight.availablePermits(),
                day, properties.quotaZone().getId(), nextReset(now), models);
    }

    private void refuse(String model, String purpose, CallOutcome outcome, String detail) {
        log.warn("Refused model call for {} ({}): {}", model, purpose, detail);
        repository.logCall(model, purpose, outcome, false, detail, null, null, null);
    }

    private static void pruneOlderThanOneMinute(Deque<Instant> window, Instant now) {
        Instant cutoff = now.minus(ONE_MINUTE);
        while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) {
            window.pollFirst();
        }
    }

    private LocalDate quotaDay(Instant now) {
        return now.atZone(properties.quotaZone()).toLocalDate();
    }

    private Instant nextReset(Instant now) {
        ZonedDateTime localNow = now.atZone(properties.quotaZone());
        return localNow.toLocalDate().plusDays(1).atStartOfDay(properties.quotaZone()).toInstant();
    }

    private static boolean isConfigured(String apiKey) {
        return apiKey != null
                && !apiKey.isBlank()
                && !PLACEHOLDER_KEYS.contains(apiKey.trim().toLowerCase(Locale.ROOT));
    }
}
