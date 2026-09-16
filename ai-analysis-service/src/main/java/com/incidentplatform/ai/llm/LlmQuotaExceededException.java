package com.incidentplatform.ai.llm;

/** A call was refused by one of the service's own limits; nothing was sent to the provider. */
public class LlmQuotaExceededException extends RuntimeException {

    public enum LimitType {
        CONCURRENCY,
        PER_MINUTE,
        PER_DAY
    }

    private final LimitType limitType;
    private final String model;
    private final long retryAfterSeconds;

    public LlmQuotaExceededException(LimitType limitType, String model, long retryAfterSeconds, String message) {
        super(message);
        this.limitType = limitType;
        this.model = model;
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public LimitType limitType() {
        return limitType;
    }

    public String model() {
        return model;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
