package com.incidentplatform.incident.exception;

/**
 * Signals a permanently malformed inbound event. Excluded from consumer retries: replaying a
 * structurally invalid message cannot make it valid, so it belongs on the dead-letter topic.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
