package com.incidentplatform.ai.llm;

import com.incidentplatform.ai.llm.LlmUsageRepository.CallOutcome;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Proof that a call cleared every limit. It holds a concurrency slot until closed, so it must be used
 * in a try-with-resources block; the daily slot it represents has already been spent.
 */
public final class LlmPermit implements AutoCloseable {

    private final String model;
    private final String purpose;
    private final LlmUsageRepository repository;
    private final Runnable releaseSlot;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    LlmPermit(String model, String purpose, LlmUsageRepository repository, Runnable releaseSlot) {
        this.model = model;
        this.purpose = purpose;
        this.repository = repository;
        this.releaseSlot = releaseSlot;
    }

    public String model() {
        return model;
    }

    public void recordSuccess(Integer promptTokens, Integer completionTokens, long latencyMs) {
        repository.logCall(model, purpose, CallOutcome.SUCCEEDED, true, null,
                promptTokens, completionTokens, latencyMs);
    }

    public void recordFailure(String detail, long latencyMs) {
        repository.logCall(model, purpose, CallOutcome.FAILED, true, detail, null, null, latencyMs);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            releaseSlot.run();
        }
    }
}
