package com.incidentplatform.ai.knowledge;

/**
 * A source document before chunking: its front-matter metadata plus its markdown body.
 *
 * @param service the service this document concerns, or null when it applies generally
 */
public record KnowledgeDocument(
        DocumentType documentType,
        String title,
        String service,
        String source,
        String content
) {
}
