package com.incidentplatform.ai.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Semantic retrieval over the knowledge base.
 *
 * <p>Retrieval filters by document type rather than taking the globally most similar chunks. With a
 * single ranked list, a strongly-worded runbook can occupy every slot and push out the historical
 * incident that would answer "have we seen this before", or the reverse. Asking for each type
 * separately guarantees the eventual prompt sees both procedure and precedent.
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final VectorStore vectorStore;

    public KnowledgeRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * @param service when set, restricts results to that service's documents; retrieval falls back
     *                to an unfiltered search if the filter yields nothing, so a service with no
     *                documents of its own can still benefit from generally applicable knowledge
     */
    public List<RetrievedChunk> search(String query, DocumentType documentType, String service, int topK) {
        List<RetrievedChunk> filtered = execute(query, filterFor(documentType, service), topK);
        if (!filtered.isEmpty() || service == null) {
            return filtered;
        }
        log.debug("No {} chunks for service {}; retrying without the service filter", documentType, service);
        return execute(query, filterFor(documentType, null), topK);
    }

    /** Retrieves both runbooks and past incidents, so neither type can crowd the other out. */
    public List<RetrievedChunk> searchAcrossTypes(String query, String service, int perType) {
        List<RetrievedChunk> results = new ArrayList<>();
        for (DocumentType type : DocumentType.values()) {
            results.addAll(search(query, type, service, perType));
        }
        return results;
    }

    private List<RetrievedChunk> execute(String query, String filterExpression, int topK) {
        SearchRequest.Builder request = SearchRequest.builder().query(query).topK(topK);
        if (filterExpression != null) {
            request.filterExpression(filterExpression);
        }
        List<org.springframework.ai.document.Document> documents = vectorStore.similaritySearch(request.build());
        if (documents == null) {
            return List.of();
        }
        return documents.stream().map(RetrievedChunk::from).toList();
    }

    private String filterFor(DocumentType documentType, String service) {
        List<String> clauses = new ArrayList<>();
        if (documentType != null) {
            clauses.add("%s == '%s'".formatted(MetadataKeys.DOCUMENT_TYPE, documentType.name()));
        }
        if (service != null && !service.isBlank()) {
            clauses.add("%s == '%s'".formatted(MetadataKeys.SERVICE, service));
        }
        return clauses.isEmpty() ? null : String.join(" && ", clauses);
    }
}
