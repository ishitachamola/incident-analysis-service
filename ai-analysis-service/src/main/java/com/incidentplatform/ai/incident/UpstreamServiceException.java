package com.incidentplatform.ai.incident;

/** A service this one depends on could not be reached or answered with an unexpected error. */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
