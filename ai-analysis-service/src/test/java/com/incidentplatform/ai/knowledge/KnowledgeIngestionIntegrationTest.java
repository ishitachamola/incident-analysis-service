package com.incidentplatform.ai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.incidentplatform.ai.AbstractKnowledgeIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(AbstractKnowledgeIntegrationTest.OfflineModelConfiguration.class)
class KnowledgeIngestionIntegrationTest extends AbstractKnowledgeIntegrationTest {

    /** The real knowledge base, so the tests exercise the documents the platform actually ships. */
    private static final Path KNOWLEDGE_BASE = Path.of("..", "sample-data");

    @Autowired
    private KnowledgeIngestionService ingestionService;

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetAndIngest() {
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM ingested_documents");
        ingestionService.ingestFrom(KNOWLEDGE_BASE);
    }

    @Test
    void knowledgeBaseFilesAreAllIngested() throws Exception {
        long markdownFiles;
        try (var files = Files.walk(KNOWLEDGE_BASE)) {
            markdownFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .count();
        }

        Integer ingested = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ingested_documents", Integer.class);
        assertThat(ingested).isEqualTo((int) markdownFiles);
    }

    @Test
    void everyStoredChunkHasAnEmbeddingAndTheMetadataRetrievalFiltersOn() {
        Integer missingEmbedding = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE embedding IS NULL", Integer.class);
        assertThat(missingEmbedding).isZero();

        Integer missingMetadata = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM vector_store
                WHERE metadata->>'documentType' IS NULL
                   OR metadata->>'source' IS NULL
                   OR metadata->>'title' IS NULL
                """, Integer.class);
        assertThat(missingMetadata).isZero();
    }

    @Test
    void bothDocumentTypesArePresent() {
        List<String> types = jdbcTemplate.queryForList(
                "SELECT DISTINCT metadata->>'documentType' FROM vector_store", String.class);

        assertThat(types).containsExactlyInAnyOrder("RUNBOOK", "HISTORICAL_INCIDENT");
    }

    @Test
    void reIngestingUnchangedDocumentsSkipsThemInsteadOfReEmbedding() {
        IngestionResult second = ingestionService.ingestFrom(KNOWLEDGE_BASE);

        // Embedding is the expensive step; an unchanged corpus must not pay for it again.
        assertThat(second.documentsProcessed()).isZero();
        assertThat(second.chunksEmbedded()).isZero();
        assertThat(second.documentsSkippedUnchanged()).isPositive();
    }

    @Test
    void changedDocumentReplacesItsChunksRatherThanDuplicatingThem() {
        Integer before = chunkCountFor("runbooks/database-connection-pool-exhaustion.md");
        assertThat(before).isPositive();

        KnowledgeDocument edited = new KnowledgeDocument(
                DocumentType.RUNBOOK, "Database Connection Pool Exhaustion", "payment-service",
                "runbooks/database-connection-pool-exhaustion.md",
                "## Symptoms\n\nA much shorter replacement body.");

        ingestionService.ingestFrom(writeTemporaryDocument(edited));

        Integer after = chunkCountFor("runbooks/database-connection-pool-exhaustion.md");
        assertThat(after).isEqualTo(1);
    }

    @Test
    void retrievalFindsTheRunbookMatchingAnIncidentDescription() {
        List<RetrievedChunk> results = retrievalService.search(
                "HikariPool connection is not available request timed out, connection pool saturated",
                DocumentType.RUNBOOK, null, 3);

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(chunk -> chunk.title().contains("Connection Pool Exhaustion"));
        assertThat(results).allSatisfy(chunk ->
                assertThat(chunk.documentType()).isEqualTo(DocumentType.RUNBOOK));
    }

    @Test
    void retrievalFindsThePastIncidentMatchingAnIncidentDescription() {
        List<RetrievedChunk> results = retrievalService.search(
                "consumer group lag rebalance CommitFailedException batch processing slow",
                DocumentType.HISTORICAL_INCIDENT, null, 3);

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(chunk -> chunk.title().contains("INC-002"));
    }

    @Test
    void documentTypeFilterExcludesTheOtherType() {
        List<RetrievedChunk> incidents = retrievalService.search(
                "database connection pool exhaustion", DocumentType.HISTORICAL_INCIDENT, null, 5);

        assertThat(incidents).isNotEmpty();
        assertThat(incidents).allSatisfy(chunk ->
                assertThat(chunk.documentType()).isEqualTo(DocumentType.HISTORICAL_INCIDENT));
    }

    @Test
    void serviceFilterRestrictsResultsToThatService() {
        List<RetrievedChunk> results = retrievalService.search(
                "errors and timeouts", DocumentType.HISTORICAL_INCIDENT, "order-service", 5);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(chunk ->
                assertThat(chunk.service()).isEqualTo("order-service"));
    }

    @Test
    void unknownServiceFallsBackToUnfilteredRetrievalRatherThanReturningNothing() {
        List<RetrievedChunk> results = retrievalService.search(
                "database connection pool exhaustion", DocumentType.RUNBOOK, "service-with-no-docs", 3);

        // A service without its own documents should still benefit from general knowledge.
        assertThat(results).isNotEmpty();
    }

    @Test
    void searchAcrossTypesReturnsBothProcedureAndPrecedent() {
        List<RetrievedChunk> results = retrievalService.searchAcrossTypes(
                "database connection pool exhaustion after deployment", null, 3);

        assertThat(results).extracting(RetrievedChunk::documentType)
                .contains(DocumentType.RUNBOOK, DocumentType.HISTORICAL_INCIDENT);
    }

    @Test
    void retrievedChunksCarryEverythingNeededToCiteThem() {
        List<RetrievedChunk> results = retrievalService.search(
                "connection pool exhaustion", DocumentType.RUNBOOK, null, 2);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(chunk -> {
            assertThat(chunk.title()).isNotBlank();
            assertThat(chunk.source()).isNotBlank();
            assertThat(chunk.text()).isNotBlank();
            assertThat(chunk.citation()).startsWith("RUNBOOK#");
        });
    }

    private Integer chunkCountFor(String source) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'source' = ?", Integer.class, source);
    }

    private Path writeTemporaryDocument(KnowledgeDocument document) {
        try {
            Path directory = Files.createTempDirectory("knowledge-edit");
            String front = """
                    ---
                    documentType: %s
                    title: %s
                    service: %s
                    source: %s
                    ---

                    %s
                    """.formatted(document.documentType(), document.title(), document.service(),
                    document.source(), document.content());
            Files.writeString(directory.resolve("edited.md"), front);
            return directory;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
