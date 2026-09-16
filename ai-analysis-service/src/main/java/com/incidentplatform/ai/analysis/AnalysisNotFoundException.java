package com.incidentplatform.ai.analysis;

import java.util.UUID;

public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(UUID incidentId) {
        super("No analysis exists yet for incident " + incidentId);
    }
}
