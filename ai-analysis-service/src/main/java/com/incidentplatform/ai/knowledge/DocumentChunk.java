package com.incidentplatform.ai.knowledge;

/**
 * One embeddable unit of a document.
 *
 * @param text    the content that gets embedded, including its context header
 * @param section the markdown heading this chunk came from, kept as metadata for citations
 */
public record DocumentChunk(String text, String section, int index) {
}
