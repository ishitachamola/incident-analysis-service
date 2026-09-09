package com.incidentplatform.ai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker(1500, 200);

    private static KnowledgeDocument document(String content) {
        return new KnowledgeDocument(DocumentType.RUNBOOK, "Connection Pool Exhaustion",
                "payment-service", "runbooks/pool.md", content);
    }

    @Test
    void splitsOnSectionHeadings() {
        List<DocumentChunk> chunks = chunker.chunk(document("""
                # Connection Pool Exhaustion

                ## Symptoms

                HTTP 500 responses and connection timeouts.

                ## Resolution

                Roll back the deployment.
                """));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).section()).isEqualTo("Symptoms");
        assertThat(chunks.get(1).section()).isEqualTo("Resolution");
        assertThat(chunks.get(0).text()).contains("HTTP 500 responses");
        assertThat(chunks.get(1).text()).contains("Roll back the deployment");
    }

    @Test
    void keepsRelatedContentTogetherRatherThanCuttingMidSection() {
        List<DocumentChunk> chunks = chunker.chunk(document("""
                ## Resolution

                1. Roll back the deployment.
                2. Optimise the slow query.
                3. Fix the connection leak.
                """));

        // The whole procedure must survive in one piece; half a numbered list is not useful evidence.
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().text())
                .contains("1. Roll back")
                .contains("2. Optimise")
                .contains("3. Fix");
    }

    @Test
    void prefixesEachChunkWithDocumentAndSectionContext() {
        List<DocumentChunk> chunks = chunker.chunk(document("""
                ## Symptoms

                HTTP 500 responses.
                """));

        // Without this a retrieved chunk cannot be identified or cited on its own.
        assertThat(chunks.getFirst().text())
                .startsWith("Connection Pool Exhaustion (payment-service) — Symptoms");
    }

    @Test
    void splitsOversizedSectionsAndNumbersChunksSequentially() {
        String longParagraph = "Connection pool saturation detail. ".repeat(60);
        List<DocumentChunk> chunks = chunker.chunk(document("""
                ## Symptoms

                %s

                %s
                """.formatted(longParagraph, longParagraph)));

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text().length()).isLessThanOrEqualTo(1500));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.section()).isEqualTo("Symptoms"));

        // Indexes must run 0..n-1 without gaps, whatever the section happens to split into.
        assertThat(chunks).extracting(DocumentChunk::index)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void everyChunkCarriesContextEvenAfterSplitting() {
        String longParagraph = "Connection pool saturation detail. ".repeat(60);
        List<DocumentChunk> chunks = chunker.chunk(document("""
                ## Symptoms

                %s

                %s
                """.formatted(longParagraph, longParagraph)));

        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.text()).startsWith("Connection Pool Exhaustion (payment-service) — Symptoms"));
    }

    @Test
    void producesNoEmptyChunks() {
        List<DocumentChunk> chunks = chunker.chunk(document("""
                # Title Only

                ## Symptoms


                ## Resolution

                Roll back.
                """));

        assertThat(chunks).hasSize(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text()).isNotBlank());
    }

    @Test
    void handlesDocumentWithNoHeadings() {
        List<DocumentChunk> chunks = chunker.chunk(document("Just a plain body with no headings."));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().section()).isNull();
        assertThat(chunks.getFirst().text()).contains("Just a plain body");
    }

    @Test
    void omitsServiceFromContextHeaderWhenAbsent() {
        KnowledgeDocument general = new KnowledgeDocument(
                DocumentType.RUNBOOK, "General Guidance", null, "runbooks/general.md",
                "## Symptoms\n\nSomething happened.");

        assertThat(chunker.chunk(general).getFirst().text()).startsWith("General Guidance — Symptoms");
    }

    @Test
    void rejectsOverlapLargerThanChunkSize() {
        assertThatThrownBy(() -> new DocumentChunker(500, 500))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }
}
