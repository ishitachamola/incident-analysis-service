package com.incidentplatform.ai.analysis;

import static com.incidentplatform.ai.analysis.AnalysisFixtures.incident;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.timeline;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.validModelJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.incidentplatform.ai.AbstractKnowledgeIntegrationTest;
import com.incidentplatform.ai.analysis.AnalysisResult.AnalysisStatus;
import com.incidentplatform.ai.incident.IncidentNotFoundException;
import com.incidentplatform.ai.incident.IncidentServiceClient;
import com.incidentplatform.ai.incident.IncidentTimeline;
import com.incidentplatform.ai.knowledge.KnowledgeIngestionService;
import com.incidentplatform.ai.llm.LlmCallFailedException;
import com.incidentplatform.ai.llm.LlmQuotaExceededException;
import com.incidentplatform.ai.llm.ScriptedChatModel;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(AbstractKnowledgeIntegrationTest.OfflineModelConfiguration.class)
class IncidentAnalysisServiceIntegrationTest extends AbstractKnowledgeIntegrationTest {

    @MockitoBean
    private IncidentServiceClient incidentClient;

    @Autowired
    private IncidentAnalysisService service;

    @Autowired
    private ScriptedChatModel model;

    @Autowired
    private KnowledgeIngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        model.reset();
        jdbcTemplate.update("DELETE FROM incident_analyses");
        jdbcTemplate.update("DELETE FROM llm_daily_usage");
        jdbcTemplate.update("DELETE FROM llm_call_log");
        ingestionService.ingestFrom(Path.of("..", "sample-data"));

        incidentId = UUID.randomUUID();
        when(incidentClient.getIncident(incidentId)).thenReturn(incident(incidentId));
        when(incidentClient.getTimeline(incidentId)).thenReturn(timeline(incidentId));
    }

    private int requestsCountedToday() {
        LocalDate pacificToday = LocalDate.now(ZoneId.of("America/Los_Angeles"));
        Integer count = jdbcTemplate.query(
                "SELECT request_count FROM llm_daily_usage WHERE usage_date = ? AND model = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, pacificToday, TEST_MODEL);
        return count != null ? count : 0;
    }

    private int storedAnalyses() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM incident_analyses", Integer.class);
    }

    @Test
    void producesAndStoresAGroundedAnalysisWithOneModelCall() {
        model.respond(validModelJson());

        AnalysisResponse response = service.analyze(incidentId, false);

        assertThat(response.cached()).isFalse();
        assertThat(response.modelCalls()).isEqualTo(1);
        assertThat(response.promptTokens()).isEqualTo(1200);
        assertThat(response.analysis().status()).isEqualTo(AnalysisStatus.ROOT_CAUSE_IDENTIFIED);
        assertThat(response.analysis().evidence()).allSatisfy(claim -> assertThat(claim.verified()).isTrue());
        assertThat(response.analysis().deploymentCorrelation().minutesBeforeDetection()).isEqualTo(13L);
        assertThat(response.analysis().sources()).isNotEmpty();

        assertThat(storedAnalyses()).isEqualTo(1);
        assertThat(requestsCountedToday()).isEqualTo(1);
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void retrievedKnowledgeReachesThePrompt() {
        model.respond(validModelJson());

        service.analyze(incidentId, false);

        String prompt = model.prompts().getFirst().getInstructions().stream()
                .map(Message::getText)
                .reduce("", String::concat);
        assertThat(prompt).contains("<runbooks>").contains("RUNBOOK#").contains("HISTORICAL_INCIDENT#");
    }

    @Test
    void unchangedEvidenceIsServedFromStorageWithoutCallingTheModel() {
        model.respond(validModelJson());
        AnalysisResponse first = service.analyze(incidentId, false);

        AnalysisResponse second = service.analyze(incidentId, false);

        assertThat(second.cached()).isTrue();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(model.callCount()).isEqualTo(1);
        assertThat(requestsCountedToday()).isEqualTo(1);
    }

    @Test
    void refreshCallsTheModelEvenWhenEvidenceIsUnchanged() {
        model.respond(validModelJson()).respond(validModelJson());
        service.analyze(incidentId, false);

        AnalysisResponse refreshed = service.analyze(incidentId, true);

        assertThat(refreshed.cached()).isFalse();
        assertThat(model.callCount()).isEqualTo(2);
    }

    @Test
    void changedEvidenceCallsTheModelAgain() {
        model.respond(validModelJson()).respond(validModelJson());
        service.analyze(incidentId, false);

        List<IncidentTimeline.Entry> entries = new ArrayList<>(AnalysisFixtures.timelineEntries());
        entries.add(AnalysisFixtures.entry(-2, "LOG", "ERROR", "Payment rejected", "LOG#new-evidence", 4));
        IncidentTimeline updated = timeline(incidentId);
        when(incidentClient.getTimeline(incidentId)).thenReturn(new IncidentTimeline(incidentId, updated.service(),
                updated.windowStart(), updated.windowEnd(), true, entries));

        AnalysisResponse second = service.analyze(incidentId, false);

        assertThat(second.cached()).isFalse();
        assertThat(model.callCount()).isEqualTo(2);
    }

    @Test
    void invalidOutputGetsExactlyOneRepairCallThatExplainsTheProblem() {
        model.respond("I think it is probably the database.").respond(validModelJson());

        AnalysisResponse response = service.analyze(incidentId, false);

        assertThat(response.modelCalls()).isEqualTo(2);
        assertThat(model.callCount()).isEqualTo(2);
        assertThat(requestsCountedToday()).isEqualTo(2);

        List<Message> repairConversation = model.prompts().get(1).getInstructions();
        assertThat(repairConversation.get(repairConversation.size() - 2)).isInstanceOf(AssistantMessage.class);
        assertThat(repairConversation.getLast().getText())
                .contains("rejected because")
                .contains("did not contain a JSON object");
    }

    @Test
    void outputStillInvalidAfterTheRepairFailsWithoutAnyFurtherCall() {
        model.respond("not json").respond("still not json");

        assertThatThrownBy(() -> service.analyze(incidentId, false))
                .isInstanceOf(AnalysisFailedException.class)
                .hasMessageContaining("after 2 call(s)");

        assertThat(model.callCount()).isEqualTo(2);
        assertThat(storedAnalyses()).isZero();
    }

    @Test
    void truncatedOutputFailsImmediatelyWithoutARepairCall() {
        model.respondTruncated("{\"status\": \"ROOT_CAUSE_IDENT");

        assertThatThrownBy(() -> service.analyze(incidentId, false))
                .isInstanceOf(AnalysisFailedException.class)
                .hasMessageContaining("cut off at the output token limit");

        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void providerFailureIsNotRetried() {
        model.fail(new RuntimeException("503 from provider"));

        assertThatThrownBy(() -> service.analyze(incidentId, false)).isInstanceOf(LlmCallFailedException.class);

        assertThat(model.callCount()).isEqualTo(1);
        assertThat(requestsCountedToday()).isEqualTo(1);
    }

    @Test
    void spentDailyBudgetRefusesTheCallBeforeTheModelIsReached() {
        jdbcTemplate.update("INSERT INTO llm_daily_usage (usage_date, model, request_count) VALUES (?, ?, ?)",
                LocalDate.now(ZoneId.of("America/Los_Angeles")), TEST_MODEL, 10_000);

        assertThatThrownBy(() -> service.analyze(incidentId, false))
                .isInstanceOfSatisfying(LlmQuotaExceededException.class,
                        ex -> assertThat(ex.limitType()).isEqualTo(LlmQuotaExceededException.LimitType.PER_DAY));

        assertThat(model.callCount()).isZero();
        Boolean sent = jdbcTemplate.queryForObject(
                "SELECT sent_to_provider FROM llm_call_log WHERE outcome = 'REJECTED_PER_DAY'", Boolean.class);
        assertThat(sent).isFalse();
    }

    @Test
    void inventedCitationsAreRemovedAndReported() {
        model.respond(validModelJson().replace(AnalysisFixtures.DEPLOY_REF, "DEPLOY#made-up-by-the-model"));

        AnalysisResponse response = service.analyze(incidentId, false);

        assertThat(response.analysis().grounding().invalidCitationsRemoved()).isEqualTo(1);
        assertThat(response.analysis().evidence())
                .anySatisfy(claim -> assertThat(claim.verified()).isFalse());
    }

    @Test
    void unknownIncidentFailsBeforeAnyModelCall() {
        UUID unknown = UUID.randomUUID();
        when(incidentClient.getIncident(unknown)).thenThrow(new IncidentNotFoundException(unknown));

        assertThatThrownBy(() -> service.analyze(unknown, false)).isInstanceOf(IncidentNotFoundException.class);
        assertThat(model.callCount()).isZero();
    }

    @Test
    void latestReturnsTheMostRecentStoredAnalysis() {
        assertThatThrownBy(() -> service.latest(incidentId)).isInstanceOf(AnalysisNotFoundException.class);
        model.respond(validModelJson());
        AnalysisResponse produced = service.analyze(incidentId, false);

        AnalysisResponse latest = service.latest(incidentId);

        assertThat(latest.id()).isEqualTo(produced.id());
        assertThat(latest.analysis().rootCause()).isEqualTo(produced.analysis().rootCause());
    }
}
