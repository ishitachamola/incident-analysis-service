package com.incidentplatform.ai.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads knowledge-base markdown files and their YAML-style front matter.
 *
 * <p>A hand-written parser is used rather than a YAML library because the front matter is a flat
 * set of scalar keys under this project's own control. Pulling in a full YAML parser for that would
 * add a dependency, and a permissive one, for no benefit.
 */
@Component
public class MarkdownDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(MarkdownDocumentLoader.class);
    private static final String DELIMITER = "---";

    /** Loads every {@code .md} file beneath the given directory, skipping any that are malformed. */
    public List<KnowledgeDocument> loadAll(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Knowledge base directory does not exist: " + directory);
        }
        try (Stream<Path> files = Files.walk(directory)) {
            List<KnowledgeDocument> documents = new ArrayList<>();
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            documents.add(load(path));
                        } catch (IllegalArgumentException ex) {
                            // One malformed file must not abort ingestion of the whole corpus.
                            log.warn("Skipping {}: {}", path.getFileName(), ex.getMessage());
                        }
                    });
            return documents;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read knowledge base at " + directory, ex);
        }
    }

    public KnowledgeDocument load(Path file) {
        String raw;
        try {
            raw = Files.readString(file);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + file, ex);
        }
        return parse(raw, file.getFileName().toString());
    }

    KnowledgeDocument parse(String raw, String fallbackSource) {
        String normalised = raw.replace("\r\n", "\n").stripLeading();
        if (!normalised.startsWith(DELIMITER)) {
            throw new IllegalArgumentException("missing front matter");
        }

        int end = normalised.indexOf("\n" + DELIMITER, DELIMITER.length());
        if (end < 0) {
            throw new IllegalArgumentException("unterminated front matter");
        }

        String frontMatter = normalised.substring(DELIMITER.length(), end);
        String body = normalised.substring(end + DELIMITER.length() + 1).strip();
        Map<String, String> fields = parseFrontMatter(frontMatter);

        String documentType = fields.get("documentType");
        if (documentType == null) {
            throw new IllegalArgumentException("front matter is missing documentType");
        }
        if (body.isBlank()) {
            throw new IllegalArgumentException("document body is empty");
        }

        DocumentType type;
        try {
            type = DocumentType.valueOf(documentType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported documentType: " + documentType);
        }

        String title = fields.getOrDefault("title", fallbackSource);
        String source = fields.getOrDefault("source", fallbackSource);
        return new KnowledgeDocument(type, title, fields.get("service"), source, body);
    }

    private Map<String, String> parseFrontMatter(String frontMatter) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : frontMatter.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).strip();
            String value = unquote(trimmed.substring(separator + 1).strip());
            if (!value.isEmpty()) {
                fields.put(key, value);
            }
        }
        return fields;
    }

    private String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
