package com.incidentplatform.ai.knowledge;

/**
 * Metadata keys stored alongside every chunk. Centralised because these strings are written during
 * ingestion and read during retrieval filtering, and a typo in either place fails silently by simply
 * matching nothing.
 */
public final class MetadataKeys {

    public static final String DOCUMENT_TYPE = "documentType";
    public static final String SERVICE = "service";
    public static final String SOURCE = "source";
    public static final String TITLE = "title";
    public static final String SECTION = "section";
    public static final String CHUNK_INDEX = "chunkIndex";

    private MetadataKeys() {
    }
}
