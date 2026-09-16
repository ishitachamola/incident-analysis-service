package com.incidentplatform.ai.analysis;

import static com.incidentplatform.ai.analysis.AnalysisFixtures.DEPLOY_REF;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.DETECTED_AT;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.DETECTION_REF;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.ERROR_TIMEOUT_REF;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.entry;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.incident;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.incidentplatform.ai.config.AnalysisProperties;
import com.incidentplatform.ai.incident.IncidentTimeline;
import com.incidentplatform.ai.knowledge.DocumentType;
import com.incidentplatform.ai.knowledge.KnowledgeRetrievalService;
import com.incidentplatform.ai.knowledge.RetrievedChunk;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EvidencePackBuilderTest {

    private static final UUID INCIDENT_ID = UUID.fromString("f5ac4a8a-7e05-46b7-9fdd-48b4a4ba7dbd");

    private KnowledgeRetrievalService retrieval;

    @BeforeEach
    void setUp() {
        retrieval = mock(KnowledgeRetrievalService.class);
        when(retrieval.search(anyString(), any(), any(), anyInt())).thenReturn(List.of());
    }

    private EvidencePackBuilder builder(int maxTimelineEntries, int maxEvidenceChars) {
        return new EvidencePackBuilder(retrieval,
                new AnalysisProperties(maxTimelineEntries, 300, 2500, maxEvidenceChars, 3, 4));
    }

    private static IncidentTimeline timelineOf(List<IncidentTimeline.Entry> entries, boolean logsAvailable) {
        return new IncidentTimeline(INCIDENT_ID, "payment-service", DETECTED_AT.minus(Duration.ofMinutes(30)),
                DETECTED_AT, logsAvailable, entries);
    }

    private static RetrievedChunk chunk(DocumentType type, String source, String title, String section, String body) {
        return new RetrievedChunk(title + " (payment-service) — " + section + "\n\n" + body, type, title,
                "payment-service", source, section, 0.8);
    }

    @Test
    void keepsDeploymentsAndEventsThenTheStrongestLogsInTimeOrder() {
        List<IncidentTimeline.Entry> entries = AnalysisFixtures.timelineEntries();

        EvidencePack pack = builder(3, 24_000).build(incident(INCIDENT_ID), timelineOf(entries, true));

        assertThat(pack.timeline()).extracting(EvidenceItem::ref)
                .containsExactly(DEPLOY_REF, ERROR_TIMEOUT_REF, DETECTION_REF);
        assertThat(pack.timelineEntriesOmitted()).isEqualTo(2);
        assertThat(pack.truncated()).isTrue();
    }

    @Test
    void calculatesDeploymentTimingRelativeToDetectionAndFirstError() {
        EvidencePack pack = builder(40, 24_000)
                .build(incident(INCIDENT_ID), timelineOf(AnalysisFixtures.timelineEntries(), true));

        DeploymentCorrelation correlation = pack.deploymentCorrelation();
        assertThat(correlation.deploymentInWindow()).isTrue();
        assertThat(correlation.deploymentRef()).isEqualTo(DEPLOY_REF);
        assertThat(correlation.minutesBeforeDetection()).isEqualTo(13L);
        assertThat(correlation.minutesFromDeploymentToFirstError()).isEqualTo(5L);
        assertThat(correlation.note()).contains("timing correlation only");
    }

    @Test
    void flagsErrorsThatBeganBeforeTheDeploymentAsEvidenceAgainstIt() {
        List<IncidentTimeline.Entry> entries = new ArrayList<>(AnalysisFixtures.timelineEntries());
        entries.add(entry(-20, "LOG", "ERROR", "Connection refused by database", "LOG#early-error", 3));

        EvidencePack pack = builder(40, 24_000).build(incident(INCIDENT_ID), timelineOf(entries, true));

        assertThat(pack.deploymentCorrelation().minutesFromDeploymentToFirstError()).isEqualTo(-7L);
        assertThat(pack.deploymentCorrelation().note()).contains("BEFORE this deployment");
    }

    @Test
    void reportsExplicitlyWhenNoDeploymentWasRecorded() {
        List<IncidentTimeline.Entry> entries = AnalysisFixtures.timelineEntries().stream()
                .filter(entry -> !"DEPLOYMENT".equals(entry.type()))
                .toList();

        EvidencePack pack = builder(40, 24_000).build(incident(INCIDENT_ID), timelineOf(entries, true));

        assertThat(pack.deploymentCorrelation().deploymentInWindow()).isFalse();
        assertThat(pack.deploymentCorrelation().note()).startsWith("No deployment was recorded");
    }

    @Test
    void mergesChunksOfOneDocumentIntoASingleCitableItem() {
        when(retrieval.search(anyString(), eq(DocumentType.RUNBOOK), any(), anyInt())).thenReturn(List.of(
                chunk(DocumentType.RUNBOOK, "runbooks/pool.md", "Pool Exhaustion", "Symptoms", "HTTP 500s."),
                chunk(DocumentType.RUNBOOK, "runbooks/pool.md", "Pool Exhaustion", "Resolution", "Roll back first.")));

        EvidencePack pack = builder(40, 24_000)
                .build(incident(INCIDENT_ID), timelineOf(AnalysisFixtures.timelineEntries(), true));

        assertThat(pack.runbooks()).singleElement().satisfies(item -> {
            assertThat(item.ref()).isEqualTo("RUNBOOK#runbooks/pool.md");
            assertThat(item.text()).contains("## Symptoms\nHTTP 500s.").contains("## Resolution\nRoll back first.");
            assertThat(item.text()).doesNotContain("(payment-service) —");
        });
    }

    @Test
    void enforcesTheSizeBudgetByDroppingSurplusDocumentsBeforeAnyLogs() {
        String body = "x".repeat(1500);
        when(retrieval.search(anyString(), eq(DocumentType.RUNBOOK), any(), anyInt())).thenReturn(
                IntStream.range(0, 3).mapToObj(i -> chunk(DocumentType.RUNBOOK, "runbooks/r" + i + ".md",
                        "Runbook " + i, "Body", body)).toList());
        when(retrieval.search(anyString(), eq(DocumentType.HISTORICAL_INCIDENT), any(), anyInt())).thenReturn(
                IntStream.range(0, 4).mapToObj(i -> chunk(DocumentType.HISTORICAL_INCIDENT, "incidents/i" + i + ".md",
                        "Incident " + i, "Body", body)).toList());

        // Room for the timeline plus roughly three documents.
        EvidencePack pack = builder(40, 5500)
                .build(incident(INCIDENT_ID), timelineOf(AnalysisFixtures.timelineEntries(), true));

        assertThat(pack.runbooks()).hasSize(1);
        assertThat(pack.historicalIncidents()).hasSize(2);
        assertThat(pack.documentsOmitted()).isEqualTo(4);
        assertThat(pack.timelineEntriesOmitted()).isZero();
    }

    @Test
    void retrievalQueryUsesWhatWentWrongAndFiltersToTheIncidentsService() {
        builder(40, 24_000).build(incident(INCIDENT_ID), timelineOf(AnalysisFixtures.timelineEntries(), true));

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(retrieval).search(query.capture(), eq(DocumentType.RUNBOOK), eq("payment-service"), eq(3));
        assertThat(query.getValue())
                .contains("Database connection timeout while acquiring connection from pool")
                .doesNotContain("(x18)");
        verify(retrieval).search(anyString(), eq(DocumentType.HISTORICAL_INCIDENT), eq("payment-service"), eq(4));
    }

    @Test
    void identicalEvidenceProducesAnIdenticalPack() {
        EvidencePackBuilder builder = builder(40, 24_000);

        EvidencePack first = builder.build(incident(INCIDENT_ID), timelineOf(AnalysisFixtures.timelineEntries(), true));
        EvidencePack second = builder.build(incident(INCIDENT_ID), timelineOf(AnalysisFixtures.timelineEntries(), true));

        // Reusing a stored analysis depends on this: any nondeterminism would make every call billable.
        assertThat(second).isEqualTo(first);
    }
}
