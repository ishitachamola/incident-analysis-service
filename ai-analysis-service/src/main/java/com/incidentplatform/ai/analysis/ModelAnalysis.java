package com.incidentplatform.ai.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The raw shape the model is asked to return. Nothing here is trusted: it is only a parse target,
 * and {@link AnalysisOutputParser} validates and grounds it into an {@link AnalysisResult}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelAnalysis(
        String status,
        String rootCause,
        Double confidence,
        List<String> affectedServices,
        List<Claim> evidence,
        List<Alternative> alternativeHypotheses,
        List<String> contributingFactors,
        List<String> recommendations,
        List<Related> relatedIncidents
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Claim(String claim, List<String> sourceRefs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alternative(String hypothesis, String assessment, String reason, List<String> sourceRefs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Related(String sourceRef, String relevance) {
    }
}
