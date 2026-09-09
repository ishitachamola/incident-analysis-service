package com.incidentplatform.ai.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Splits a document into embeddable chunks.
 *
 * <p>Two decisions drive this implementation.
 *
 * <p><strong>Split on section boundaries first.</strong> A fixed-size splitter would cut through the
 * middle of a "Resolution" list, leaving half a procedure in one chunk and half in another; neither
 * fragment then retrieves well or reads well as evidence. Runbooks and incident reports are already
 * written in meaningful sections, so those headings are the natural seams. Only sections too large
 * to embed are split further, and there by paragraph with an overlap so a fact spanning the cut is
 * not lost.
 *
 * <p><strong>Prefix each chunk with its document and section.</strong> A chunk reading "Roll back
 * first" is nearly useless in isolation: the retriever cannot tell what it concerns and the model
 * cannot cite it. Carrying the title and heading into the embedded text makes each chunk
 * self-describing, which improves both retrieval and the quality of the eventual citation.
 */
@Component
public class DocumentChunker {

    private final int maxChunkChars;
    private final int overlapChars;

    public DocumentChunker(
            @Value("${knowledge.chunking.max-chars:1500}") int maxChunkChars,
            @Value("${knowledge.chunking.overlap-chars:200}") int overlapChars) {
        if (overlapChars >= maxChunkChars) {
            throw new IllegalArgumentException("overlap must be smaller than the maximum chunk size");
        }
        this.maxChunkChars = maxChunkChars;
        this.overlapChars = overlapChars;
    }

    public List<DocumentChunk> chunk(KnowledgeDocument document) {
        List<Section> sections = splitIntoSections(document.content());
        List<DocumentChunk> chunks = new ArrayList<>();

        for (Section section : sections) {
            String header = contextHeader(document, section.heading());
            for (String body : splitToSize(section.body(), maxChunkChars - header.length())) {
                chunks.add(new DocumentChunk(header + body, section.heading(), chunks.size()));
            }
        }
        return chunks;
    }

    private String contextHeader(KnowledgeDocument document, String heading) {
        StringBuilder header = new StringBuilder(document.title());
        if (document.service() != null && !document.service().isBlank()) {
            header.append(" (").append(document.service()).append(")");
        }
        if (heading != null && !heading.isBlank()) {
            header.append(" — ").append(heading);
        }
        return header.append("\n\n").toString();
    }

    private List<Section> splitIntoSections(String content) {
        List<Section> sections = new ArrayList<>();
        String currentHeading = null;
        StringBuilder body = new StringBuilder();

        for (String line : content.split("\n")) {
            if (line.startsWith("## ")) {
                addIfPresent(sections, currentHeading, body);
                currentHeading = line.substring(3).strip();
                body = new StringBuilder();
            } else if (line.startsWith("# ")) {
                // The document's own H1 title is already carried in the context header.
                addIfPresent(sections, currentHeading, body);
                currentHeading = null;
                body = new StringBuilder();
            } else {
                body.append(line).append("\n");
            }
        }
        addIfPresent(sections, currentHeading, body);
        return sections;
    }

    private void addIfPresent(List<Section> sections, String heading, StringBuilder body) {
        String text = body.toString().strip();
        if (!text.isEmpty()) {
            sections.add(new Section(heading, text));
        }
    }

    /** Splits on paragraph boundaries, falling back to a hard cut only for an oversized paragraph. */
    private List<String> splitToSize(String text, int limit) {
        if (text.length() <= limit) {
            return List.of(text);
        }

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : text.split("\n\n")) {
            if (paragraph.length() > limit) {
                if (!current.isEmpty()) {
                    parts.add(current.toString().strip());
                    current = new StringBuilder();
                }
                parts.addAll(hardSplit(paragraph, limit));
                continue;
            }
            if (current.length() + paragraph.length() + 2 > limit && !current.isEmpty()) {
                parts.add(current.toString().strip());
                current = new StringBuilder(tailOverlap(current.toString()));
            }
            current.append(paragraph).append("\n\n");
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString().strip());
        }
        return parts;
    }

    private List<String> hardSplit(String paragraph, int limit) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + limit, paragraph.length());
            parts.add(paragraph.substring(start, end).strip());
            if (end == paragraph.length()) {
                break;
            }
            start = end - overlapChars;
        }
        return parts;
    }

    /** Carries the tail of the previous chunk forward so a fact spanning the seam survives. */
    private String tailOverlap(String previous) {
        if (previous.length() <= overlapChars) {
            return previous;
        }
        String tail = previous.substring(previous.length() - overlapChars);
        int boundary = tail.indexOf(' ');
        return (boundary > 0 ? tail.substring(boundary + 1) : tail) + "\n\n";
    }

    private record Section(String heading, String body) {
    }
}
