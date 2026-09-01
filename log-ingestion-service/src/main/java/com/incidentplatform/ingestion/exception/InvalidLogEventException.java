package com.incidentplatform.ingestion.exception;

/**
 * Signals a permanently malformed event. This is deliberately excluded from the consumer's retry
 * policy: replaying a structurally invalid message cannot make it valid, so it goes straight to the
 * dead-letter topic instead of burning retry attempts.
 */
public class InvalidLogEventException extends RuntimeException {

    public InvalidLogEventException(String message) {
        super(message);
    }
}
