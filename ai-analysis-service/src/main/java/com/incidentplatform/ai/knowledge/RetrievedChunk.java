package com.incidentplatform.ai.knowledge;

import org.springframework.ai.document.Document;

/**
 * A retrieved chunk together with the identity needed to cite it.
 *
 * @param score similarity score as reported by the vector store, retained so retrieval quality can
 *              be measured rather than assumed
 */
public record RetrievedChunk(
        String text,
        DocumentType documentType,
        String title,
        String service,
        String source,
        String section,
        Double score
) {

    public static RetrievedChunk from(Document document) {
        var metadata = document.getMetadata();
        return new RetrievedChunk(
                document.getText(),
                DocumentType.valueOf(String.valueOf(metadata.get(MetadataKeys.DOCUMENT_TYPE))),
                asString(metadata.get(MetadataKeys.TITLE)),
                asString(metadata.get(MetadataKeys.SERVICE)),
                asString(metadata.get(MetadataKeys.SOURCE)),
                asString(metadata.get(MetadataKeys.SECTION)),
                document.getScore());
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /** A short, stable reference an answer can cite, for example {@code RUNBOOK#…}. */
    public String citation() {
        return documentType().name() + "#" + source();
    }
}
