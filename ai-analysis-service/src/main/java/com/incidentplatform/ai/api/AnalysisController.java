package com.incidentplatform.ai.api;

import com.incidentplatform.ai.analysis.AnalysisResponse;
import com.incidentplatform.ai.analysis.IncidentAnalysisService;
import com.incidentplatform.ai.llm.LlmCallGuard;
import com.incidentplatform.ai.llm.LlmUsageSnapshot;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalysisController {

    private final IncidentAnalysisService analysisService;
    private final LlmCallGuard callGuard;

    public AnalysisController(IncidentAnalysisService analysisService, LlmCallGuard callGuard) {
        this.analysisService = analysisService;
        this.callGuard = callGuard;
    }

    /**
     * Analyses an incident. Returns the stored analysis without calling the model when the evidence
     * is unchanged, unless {@code refresh=true}.
     */
    @PostMapping("/api/incidents/{incidentId}/analyze")
    public AnalysisResponse analyze(@PathVariable UUID incidentId,
                                    @RequestParam(defaultValue = "false") boolean refresh) {
        return analysisService.analyze(incidentId, refresh);
    }

    @GetMapping("/api/incidents/{incidentId}/analysis")
    public AnalysisResponse latestAnalysis(@PathVariable UUID incidentId) {
        return analysisService.latest(incidentId);
    }

    /** Current consumption against every model call limit. Reading it never calls the model. */
    @GetMapping("/api/ai/usage")
    public LlmUsageSnapshot usage() {
        return callGuard.snapshot();
    }
}
