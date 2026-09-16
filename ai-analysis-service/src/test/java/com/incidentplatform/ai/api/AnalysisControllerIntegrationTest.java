package com.incidentplatform.ai.api;

import static com.incidentplatform.ai.analysis.AnalysisFixtures.incident;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.timeline;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.validModelJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.incidentplatform.ai.AbstractKnowledgeIntegrationTest;
import com.incidentplatform.ai.incident.IncidentNotFoundException;
import com.incidentplatform.ai.incident.IncidentServiceClient;
import com.incidentplatform.ai.incident.UpstreamServiceException;
import com.incidentplatform.ai.llm.ScriptedChatModel;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(AbstractKnowledgeIntegrationTest.OfflineModelConfiguration.class)
class AnalysisControllerIntegrationTest extends AbstractKnowledgeIntegrationTest {

    @MockitoBean
    private IncidentServiceClient incidentClient;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ScriptedChatModel model;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private UUID incidentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        model.reset();
        jdbcTemplate.update("DELETE FROM incident_analyses");
        jdbcTemplate.update("DELETE FROM llm_daily_usage");
        jdbcTemplate.update("DELETE FROM llm_call_log");

        incidentId = UUID.randomUUID();
        when(incidentClient.getIncident(incidentId)).thenReturn(incident(incidentId));
        when(incidentClient.getTimeline(incidentId)).thenReturn(timeline(incidentId));
    }

    @Test
    void analyzeReturnsTheGroundedAnalysis() throws Exception {
        model.respond(validModelJson());

        mockMvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.modelCalls").value(1))
                .andExpect(jsonPath("$.analysis.status").value("ROOT_CAUSE_IDENTIFIED"))
                .andExpect(jsonPath("$.analysis.evidence[0].verified").value(true))
                .andExpect(jsonPath("$.analysis.deploymentCorrelation.minutesBeforeDetection").value(13));
    }

    @Test
    void unknownIncidentReturns404WithoutCallingTheModel() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(incidentClient.getIncident(unknown)).thenThrow(new IncidentNotFoundException(unknown));

        mockMvc.perform(post("/api/incidents/{id}/analyze", unknown))
                .andExpect(status().isNotFound());
        assertThat(model.callCount()).isZero();
    }

    @Test
    void spentDailyBudgetReturns429WithRetryAfter() throws Exception {
        jdbcTemplate.update("INSERT INTO llm_daily_usage (usage_date, model, request_count) VALUES (?, ?, ?)",
                LocalDate.now(ZoneId.of("America/Los_Angeles")), TEST_MODEL, 10_000);

        mockMvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(containsString("Daily limit")));
        assertThat(model.callCount()).isZero();
    }

    @Test
    void unavailableIncidentServiceReturns503() throws Exception {
        when(incidentClient.getIncident(incidentId))
                .thenThrow(new UpstreamServiceException("Incident service is unavailable", null));

        mockMvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void providerFailureReturns502WithoutLeakingProviderDetail() throws Exception {
        model.fail(new RuntimeException("upstream said secret-provider-detail"));

        mockMvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("secret-provider-detail"))));
    }

    @Test
    void latestAnalysisIs404UntilOneExists() throws Exception {
        mockMvc.perform(get("/api/incidents/{id}/analysis", incidentId))
                .andExpect(status().isNotFound());

        model.respond(validModelJson());
        mockMvc.perform(post("/api/incidents/{id}/analyze", incidentId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/incidents/{id}/analysis", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.status").value("ROOT_CAUSE_IDENTIFIED"));
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void usageEndpointReportsLimitsAndNeverCallsTheModel() throws Exception {
        mockMvc.perform(get("/api/ai/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeModel").value(TEST_MODEL))
                .andExpect(jsonPath("$.quotaZone").value("America/Los_Angeles"))
                .andExpect(jsonPath("$.maxConcurrentCalls").value(1))
                .andExpect(jsonPath("$.models[?(@.model == 'gemini-3.8-flash')].requestsPerDayLimit").value(15));
        assertThat(model.callCount()).isZero();
    }

    @Test
    void malformedIncidentIdReturns400() throws Exception {
        mockMvc.perform(post("/api/incidents/{id}/analyze", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
