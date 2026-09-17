package com.incidentplatform.ai.chat;

import static com.incidentplatform.ai.analysis.AnalysisFixtures.ERROR_TIMEOUT_REF;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.incident;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.timeline;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.validModelJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.incidentplatform.ai.AbstractKnowledgeIntegrationTest;
import com.incidentplatform.ai.analysis.IncidentAnalysisService;
import com.incidentplatform.ai.incident.IncidentServiceClient;
import com.incidentplatform.ai.knowledge.KnowledgeIngestionService;
import com.incidentplatform.ai.llm.LlmQuotaExceededException;
import com.incidentplatform.ai.llm.ScriptedChatModel;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(AbstractKnowledgeIntegrationTest.OfflineModelConfiguration.class)
class IncidentChatServiceIntegrationTest extends AbstractKnowledgeIntegrationTest {

    @MockitoBean
    private IncidentServiceClient incidentClient;

    @Autowired
    private IncidentChatService chatService;

    @Autowired
    private IncidentAnalysisService analysisService;

    @Autowired
    private KnowledgeIngestionService ingestionService;

    @Autowired
    private ScriptedChatModel model;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        model.reset();
        jdbcTemplate.update("DELETE FROM chat_messages");
        jdbcTemplate.update("DELETE FROM incident_analyses");
        jdbcTemplate.update("DELETE FROM llm_daily_usage");
        jdbcTemplate.update("DELETE FROM llm_call_log");
        ingestionService.ingestFrom(Path.of("..", "sample-data"));

        incidentId = UUID.randomUUID();
        when(incidentClient.getIncident(incidentId)).thenReturn(incident(incidentId));
        when(incidentClient.getTimeline(incidentId)).thenReturn(timeline(incidentId));
    }

    @Test
    void answersWithValidatedCitationsAndStoresBothTurns() {
        model.respond("The pool was saturated after the deployment [" + ERROR_TIMEOUT_REF + "].");

        ChatReply reply = chatService.ask(incidentId, "Why did payments fail?");

        assertThat(reply.sources()).containsExactly(ERROR_TIMEOUT_REF);
        assertThat(reply.removedCitations()).isEmpty();
        assertThat(model.callCount()).isEqualTo(1);

        List<ChatMessage> history = chatService.history(incidentId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(history.get(0).content()).isEqualTo("Why did payments fail?");
        assertThat(history.get(1).role()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(history.get(1).sources()).containsExactly(ERROR_TIMEOUT_REF);
    }

    @Test
    void repliesCitingEvidenceNeverShownHaveThoseReferencesRemoved() {
        model.respond("Memory was exhausted [LOG#not-in-the-evidence].");

        ChatReply reply = chatService.ask(incidentId, "Was it memory?");

        assertThat(reply.removedCitations()).containsExactly("LOG#not-in-the-evidence");
        assertThat(reply.reply()).contains(ChatCitationValidator.REMOVED_MARKER);
        assertThat(reply.sources()).isEmpty();
    }

    @Test
    void earlierTurnsAndThePriorAnalysisAreCarriedIntoTheNextQuestion() {
        model.respond(validModelJson());
        analysisService.analyze(incidentId, false);
        model.respond("Yes, connection timeouts dominated.").respond("Roll back the release first.");

        chatService.ask(incidentId, "Was the pool saturated?");
        chatService.ask(incidentId, "What should I do first?");

        List<Message> secondCall = model.prompts().getLast().getInstructions();
        String conversation = secondCall.stream().map(Message::getText).reduce("", String::concat);
        assertThat(conversation)
                .contains("<previous_analysis>")
                .contains("Database connection pool exhaustion")
                .contains("Was the pool saturated?")
                .contains("Yes, connection timeouts dominated.");
    }

    @Test
    void eachQuestionCostsExactlyOneModelCall() {
        model.respond("Answer one.").respond("Answer two.");

        chatService.ask(incidentId, "First question?");
        chatService.ask(incidentId, "Second question?");

        assertThat(model.callCount()).isEqualTo(2);
        Integer requests = jdbcTemplate.queryForObject(
                "SELECT request_count FROM llm_daily_usage WHERE model = ?", Integer.class, TEST_MODEL);
        assertThat(requests).isEqualTo(2);
    }

    @Test
    void spentDailyBudgetRefusesTheQuestionBeforeTheModelIsReached() {
        jdbcTemplate.update("INSERT INTO llm_daily_usage (usage_date, model, request_count) VALUES (?, ?, ?)",
                LocalDate.now(ZoneId.of("America/Los_Angeles")), TEST_MODEL, 10_000);

        assertThatThrownBy(() -> chatService.ask(incidentId, "Why?"))
                .isInstanceOf(LlmQuotaExceededException.class);

        assertThat(model.callCount()).isZero();
        assertThat(chatService.history(incidentId)).isEmpty();
    }

    @Test
    void rejectsAnEmptyQuestionWithoutCallingTheModel() {
        assertThatThrownBy(() -> chatService.ask(incidentId, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(model.callCount()).isZero();
    }
}
