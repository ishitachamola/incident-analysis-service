package com.incidentplatform.ai.analysis;

/** No valid analysis could be produced within the permitted number of model calls. */
public class AnalysisFailedException extends RuntimeException {

    public AnalysisFailedException(String message) {
        super(message);
    }
}
