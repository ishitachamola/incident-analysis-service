package com.incidentplatform.ai.knowledge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Loads the knowledge base, chunks it, embeds each chunk and stores the vectors.
 *
 * <p>Ingestion is idempotent by content hash. Re-running it leaves unchanged documents alone, which
 * matters because embedding is the one step here that costs money and time: re-embedding an
 * unchanged corpus on every restart would be pure waste. When a document has changed, its existing
 * chunks are deleted before the new ones are written, since a changed document usually produces a
 * different number of chunks and updating in place would strand the leftovers.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final MarkdownDocumentLoader loader;
    private final DocumentChunker chunker;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final Path knowledgeBasePath;

    public KnowledgeIngestionService(MarkdownDocumentLoader loader,
                                      DocumentChunker chunker,
                                      VectorStore vectorStore,
                                      JdbcTemplate jdbcTemplate,
                                      @Value("${knowledge.source-path}") String knowledgeBasePath) {
        this.loader = loader;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        // Converted here rather than by Spring's Path converter, which treats the value as a
        // resource path and rejects a relative one such as "../sample-data".
        this.knowledgeBasePath = Path.of(knowledgeBasePath);
    }

    public IngestionResult ingestAll() {
        return ingestFrom(knowledgeBasePath);
    }

    public IngestionResult ingestFrom(Path directory) {
        long startedAt = System.currentTimeMillis();
        List<KnowledgeDocument> documents = loader.loadAll(directory);

        int processed = 0;
        int skipped = 0;
        int chunksEmbedded = 0;

        for (KnowledgeDocument document : documents) {
            String hash = sha256(document.content());
            if (isUnchanged(document.source(), hash)) {
                skipped++;
                continue;
            }
            chunksEmbedded += ingestDocument(document, hash);
            processed++;
        }

        long duration = System.currentTimeMillis() - startedAt;
        log.info("Ingested {} documents ({} unchanged, {} chunks embedded) in {}ms",
                processed, skipped, chunksEmbedded, duration);
        return new IngestionResult(processed, skipped, chunksEmbedded, duration);
    }

    /**
     * Deliberately not wrapped in a single transaction. Embedding is a network call to an external
     * API, and holding a database transaction open across it would tie up a connection for the
     * duration of a request outside our control.
     *
     * <p>The ordering is chosen so that any partial failure self-heals: the tracking record is
     * written only after the chunks are stored, so a crash at any point leaves the document looking
     * un-ingested and the next run replaces it wholesale.
     */
    private int ingestDocument(KnowledgeDocument document, String contentHash) {
        deleteExistingChunks(document.source());

        List<DocumentChunk> chunks = chunker.chunk(document);
        List<Document> embeddable = chunks.stream()
                .map(chunk -> new Document(chunk.text(), metadataFor(document, chunk)))
                .toList();

        // The vector store performs the embedding call as part of the write.
        vectorStore.add(embeddable);
        recordIngestion(document, contentHash, chunks.size());

        log.debug("Embedded {} chunks for {}", chunks.size(), document.source());
        return chunks.size();
    }

    private Map<String, Object> metadataFor(KnowledgeDocument document, DocumentChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MetadataKeys.DOCUMENT_TYPE, document.documentType().name());
        metadata.put(MetadataKeys.SOURCE, document.source());
        metadata.put(MetadataKeys.TITLE, document.title());
        metadata.put(MetadataKeys.CHUNK_INDEX, chunk.index());
        if (document.service() != null) {
            metadata.put(MetadataKeys.SERVICE, document.service());
        }
        if (chunk.section() != null) {
            metadata.put(MetadataKeys.SECTION, chunk.section());
        }
        return metadata;
    }

    private boolean isUnchanged(String source, String contentHash) {
        Integer matches = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ingested_documents WHERE source = ? AND content_hash = ?",
                Integer.class, source, contentHash);
        return matches != null && matches > 0;
    }

    private void deleteExistingChunks(String source) {
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'source' = ?", source);
        jdbcTemplate.update("DELETE FROM ingested_documents WHERE source = ?", source);
    }

    private void recordIngestion(KnowledgeDocument document, String contentHash, int chunkCount) {
        jdbcTemplate.update("""
                INSERT INTO ingested_documents
                    (id, source, document_type, title, service_name, content_hash, chunk_count)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), document.source(), document.documentType().name(),
                document.title(), document.service(), contentHash, chunkCount);
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
