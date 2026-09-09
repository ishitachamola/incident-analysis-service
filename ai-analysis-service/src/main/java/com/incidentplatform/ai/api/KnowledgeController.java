package com.incidentplatform.ai.api;

import com.incidentplatform.ai.knowledge.DocumentType;
import com.incidentplatform.ai.knowledge.IngestionResult;
import com.incidentplatform.ai.knowledge.KnowledgeIngestionService;
import com.incidentplatform.ai.knowledge.KnowledgeRetrievalService;
import com.incidentplatform.ai.knowledge.RetrievedChunk;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational endpoints for the knowledge base. Retrieval is exposed directly so retrieval quality
 * can be inspected on its own, separately from anything the language model later does with it.
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeRetrievalService retrievalService;

    public KnowledgeController(KnowledgeIngestionService ingestionService,
                                KnowledgeRetrievalService retrievalService) {
        this.ingestionService = ingestionService;
        this.retrievalService = retrievalService;
    }

    @PostMapping("/ingest")
    public IngestionResult ingest() {
        return ingestionService.ingestAll();
    }

    @GetMapping("/search")
    public List<RetrievedChunk> search(
            @RequestParam String query,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "4") int topK) {

        return documentType != null
                ? retrievalService.search(query, documentType, service, topK)
                : retrievalService.searchAcrossTypes(query, service, topK);
    }
}
