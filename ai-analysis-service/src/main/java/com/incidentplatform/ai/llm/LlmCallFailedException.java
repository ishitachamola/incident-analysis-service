package com.incidentplatform.ai.llm;

/**
 * The request reached the provider and failed. It has already been counted against quota. The
 * message is deliberately generic; provider error detail is kept in the call log, not returned to
 * API clients.
 */
public class LlmCallFailedException extends RuntimeException {

    public LlmCallFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
