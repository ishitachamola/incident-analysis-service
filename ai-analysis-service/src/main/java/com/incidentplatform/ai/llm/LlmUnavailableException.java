package com.incidentplatform.ai.llm;

/** Model calls are switched off or not configured; nothing was sent to the provider. */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }
}
