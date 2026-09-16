package com.incidentplatform.ai.analysis;

/**
 * One citable piece of evidence placed in the prompt.
 *
 * @param ref the exact reference the model must quote to cite this item; answers citing a reference
 *            that is not in the evidence are rejected
 */
public record EvidenceItem(String ref, Category category, String title, String text) {

    public enum Category {
        TIMELINE,
        RUNBOOK,
        HISTORICAL_INCIDENT
    }
}
