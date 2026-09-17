package com.incidentplatform.ai.api;

import com.incidentplatform.ai.analysis.AnalysisResponse;
import com.incidentplatform.ai.analysis.IncidentAnalysisService;
import com.incidentplatform.ai.chat.ChatMessage;
import com.incidentplatform.ai.chat.ChatQuestion;
import com.incidentplatform.ai.chat.ChatReply;
import com.incidentplatform.ai.chat.IncidentChatService;
import com.incidentplatform.ai.llm.LlmCallGuard;
import com.incidentplatform.ai.llm.LlmUsageSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalysisController {

    private final IncidentAnalysisService analysisService;
    private final IncidentChatService chatService;
    private final LlmCallGuard callGuard;

    public AnalysisController(IncidentAnalysisService analysisService, IncidentChatService chatService,
                              LlmCallGuard callGuard) {
        this.analysisService = analysisService;
        this.chatService = chatService;
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

    /** Asks a follow-up question about an incident. Costs exactly one model call. */
    @PostMapping("/api/incidents/{incidentId}/chat")
    public ChatReply chat(@PathVariable UUID incidentId, @RequestBody ChatQuestion question) {
        return chatService.ask(incidentId, question.message());
    }

    @GetMapping("/api/incidents/{incidentId}/chat")
    public List<ChatMessage> chatHistory(@PathVariable UUID incidentId) {
        return chatService.history(incidentId);
    }

    /** Current consumption against every model call limit. Reading it never calls the model. */
    @GetMapping("/api/ai/usage")
    public LlmUsageSnapshot usage() {
        return callGuard.snapshot();
    }
}
