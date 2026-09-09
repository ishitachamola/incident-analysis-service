package com.incidentplatform.ai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MarkdownDocumentLoaderTest {

    private final MarkdownDocumentLoader loader = new MarkdownDocumentLoader();

    @Test
    void parsesFrontMatterAndBody() {
        String raw = """
                ---
                documentType: RUNBOOK
                title: Database Connection Pool Exhaustion
                service: payment-service
                source: runbooks/db-pool.md
                ---

                # Database Connection Pool Exhaustion

                ## Symptoms

                HTTP 500 responses.
                """;

        KnowledgeDocument document = loader.parse(raw, "fallback.md");

        assertThat(document.documentType()).isEqualTo(DocumentType.RUNBOOK);
        assertThat(document.title()).isEqualTo("Database Connection Pool Exhaustion");
        assertThat(document.service()).isEqualTo("payment-service");
        assertThat(document.source()).isEqualTo("runbooks/db-pool.md");
        assertThat(document.content()).startsWith("# Database Connection Pool Exhaustion");
        assertThat(document.content()).contains("HTTP 500 responses.");
    }

    @Test
    void stripsQuotesFromFrontMatterValues() {
        String raw = """
                ---
                documentType: HISTORICAL_INCIDENT
                title: "INC-003: Pool exhaustion"
                ---
                Body text.
                """;

        assertThat(loader.parse(raw, "f.md").title()).isEqualTo("INC-003: Pool exhaustion");
    }

    @Test
    void treatsAbsentServiceAsNull() {
        String raw = """
                ---
                documentType: RUNBOOK
                title: General guidance
                ---
                Body text.
                """;

        assertThat(loader.parse(raw, "f.md").service()).isNull();
    }

    @Test
    void fallsBackToFilenameWhenTitleAndSourceAreAbsent() {
        String raw = """
                ---
                documentType: RUNBOOK
                ---
                Body text.
                """;

        KnowledgeDocument document = loader.parse(raw, "my-runbook.md");

        assertThat(document.title()).isEqualTo("my-runbook.md");
        assertThat(document.source()).isEqualTo("my-runbook.md");
    }

    @Test
    void handlesWindowsLineEndings() {
        String raw = "---\r\ndocumentType: RUNBOOK\r\ntitle: Test\r\n---\r\n\r\nBody text.\r\n";

        KnowledgeDocument document = loader.parse(raw, "f.md");

        assertThat(document.documentType()).isEqualTo(DocumentType.RUNBOOK);
        assertThat(document.content()).isEqualTo("Body text.");
    }

    @Test
    void rejectsDocumentWithoutFrontMatter() {
        assertThatThrownBy(() -> loader.parse("# Just a heading\n\nText.", "f.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("front matter");
    }

    @Test
    void rejectsUnterminatedFrontMatter() {
        assertThatThrownBy(() -> loader.parse("---\ndocumentType: RUNBOOK\nBody", "f.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unterminated");
    }

    @Test
    void rejectsUnknownDocumentType() {
        String raw = """
                ---
                documentType: SOMETHING_ELSE
                ---
                Body.
                """;

        assertThatThrownBy(() -> loader.parse(raw, "f.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported documentType");
    }

    @Test
    void rejectsEmptyBody() {
        String raw = """
                ---
                documentType: RUNBOOK
                title: Empty
                ---
                """;

        assertThatThrownBy(() -> loader.parse(raw, "f.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }
}
