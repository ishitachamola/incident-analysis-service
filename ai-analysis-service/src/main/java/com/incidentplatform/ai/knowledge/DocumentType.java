package com.incidentplatform.ai.knowledge;

/**
 * The kinds of knowledge the platform retrieves over. Retrieval filters on this so a question about
 * "how do I fix X" can be steered towards procedures, and "have we seen this before" towards past
 * incidents, rather than letting one type crowd out the other by similarity alone.
 */
public enum DocumentType {
    RUNBOOK,
    HISTORICAL_INCIDENT
}
