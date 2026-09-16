package com.incidentplatform.ai.analysis;

/** The model's output could not be parsed or broke a structural rule. The message says which. */
public class InvalidModelOutputException extends RuntimeException {

    public InvalidModelOutputException(String message) {
        super(message);
    }
}
