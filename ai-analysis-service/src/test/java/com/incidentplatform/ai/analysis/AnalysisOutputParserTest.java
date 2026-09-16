package com.incidentplatform.ai.analysis;

import static com.incidentplatform.ai.analysis.AnalysisFixtures.DEPLOY_REF;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.ERROR_TIMEOUT_REF;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.INC003_REF;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.RUNBOOK_REF;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.pack;
import static com.incidentplatform.ai.analysis.AnalysisFixtures.validModelJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentplatform.ai.analysis.AnalysisResult.AnalysisStatus;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnalysisOutputParserTest {

    private final AnalysisOutputParser parser = new AnalysisOutputParser(new ObjectMapper());

    private static String answer(String status, double confidence, String evidenceJson, String relatedJson) {
        return """
                {"status": "%s", "rootCause": "Pool exhaustion", "confidence": %s,
                 "affectedServices": ["payment-service"], "evidence": %s, "relatedIncidents": %s}
                """.formatted(status, confidence, evidenceJson, relatedJson);
    }

    @Test
    void parsesAValidAnswerAndVerifiesItsCitations() {
        AnalysisResult result = parser.parse(validModelJson(), pack());

        assertThat(result.status()).isEqualTo(AnalysisStatus.ROOT_CAUSE_IDENTIFIED);
        assertThat(result.confidence()).isEqualTo(0.82);
        assertThat(result.evidence()).hasSize(2).allSatisfy(claim -> assertThat(claim.verified()).isTrue());
        assertThat(result.alternativeHypotheses()).singleElement()
                .satisfies(alt -> assertThat(alt.assessment()).isEqualTo("LESS_LIKELY"));
        assertThat(result.relatedIncidents()).singleElement().satisfies(related -> {
            assertThat(related.sourceRef()).isEqualTo(INC003_REF);
            assertThat(related.title()).startsWith("INC-003");
        });
        assertThat(result.grounding().invalidCitationsRemoved()).isZero();
        assertThat(result.grounding().downgradedToInsufficientEvidence()).isFalse();
        assertThat(result.deploymentCorrelation().minutesBeforeDetection()).isEqualTo(13L);
        assertThat(result.sources()).extracting(AnalysisResult.SourceRef::ref).contains(DEPLOY_REF, RUNBOOK_REF);
    }

    @Test
    void acceptsJsonWrappedInMarkdownFences() {
        AnalysisResult result = parser.parse("```json\n" + validModelJson() + "\n```", pack());

        assertThat(result.status()).isEqualTo(AnalysisStatus.ROOT_CAUSE_IDENTIFIED);
    }

    @Test
    void rejectsAResponseWithNoJsonObject() {
        assertThatThrownBy(() -> parser.parse("The root cause is pool exhaustion.", pack()))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("did not contain a JSON object");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{\"status\": \"ROOT_CAUSE_IDENTIFIED\", \"rootCause\": }", pack()))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void rejectsAnUnknownStatus() {
        assertThatThrownBy(() -> parser.parse(answer("PROBABLY_THE_DATABASE", 0.5, "[]", "[]"), pack()))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("status must be");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.5, 87})
    void rejectsConfidenceOutsideZeroToOne(double confidence) {
        assertThatThrownBy(() -> parser.parse(answer("ROOT_CAUSE_IDENTIFIED", confidence, "[]", "[]"), pack()))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void rejectsAMissingRootCause() {
        assertThatThrownBy(() -> parser.parse(
                "{\"status\": \"INSUFFICIENT_EVIDENCE\", \"rootCause\": \"  \", \"confidence\": 0.2}", pack()))
                .isInstanceOf(InvalidModelOutputException.class)
                .hasMessageContaining("rootCause");
    }

    @Test
    void removesCitationsOfEvidenceThatWasNeverProvided() {
        String evidence = "[{\"claim\": \"Timeouts repeated\", \"sourceRefs\": [\"%s\", \"LOG#invented-by-model\"]}]"
                .formatted(ERROR_TIMEOUT_REF);

        AnalysisResult result = parser.parse(answer("ROOT_CAUSE_IDENTIFIED", 0.8, evidence, "[]"), pack());

        assertThat(result.evidence()).singleElement().satisfies(claim -> {
            assertThat(claim.sourceRefs()).containsExactly(ERROR_TIMEOUT_REF);
            assertThat(claim.verified()).isTrue();
        });
        assertThat(result.grounding().invalidCitationsRemoved()).isEqualTo(1);
        assertThat(result.grounding().notes()).anyMatch(note -> note.contains("were removed"));
    }

    @Test
    void rootCauseBackedOnlyByInventedCitationsIsDowngradedAndCapped() {
        String evidence = "[{\"claim\": \"The cache was full\", \"sourceRefs\": [\"LOG#does-not-exist\"]}]";

        AnalysisResult result = parser.parse(answer("ROOT_CAUSE_IDENTIFIED", 0.95, evidence, "[]"), pack());

        assertThat(result.status()).isEqualTo(AnalysisStatus.INSUFFICIENT_EVIDENCE);
        assertThat(result.confidence()).isEqualTo(AnalysisOutputParser.UNVERIFIED_ROOT_CAUSE_MAX_CONFIDENCE);
        assertThat(result.evidence()).singleElement().satisfies(claim -> assertThat(claim.verified()).isFalse());
        assertThat(result.grounding().downgradedToInsufficientEvidence()).isTrue();
        assertThat(result.grounding().confidenceCapped()).isTrue();
    }

    @Test
    void insufficientEvidenceCannotClaimHighConfidence() {
        AnalysisResult result = parser.parse(answer("INSUFFICIENT_EVIDENCE", 0.8, "[]", "[]"), pack());

        assertThat(result.confidence()).isEqualTo(AnalysisOutputParser.INSUFFICIENT_EVIDENCE_MAX_CONFIDENCE);
        assertThat(result.grounding().confidenceCapped()).isTrue();
    }

    @Test
    void relatedIncidentsMustBeHistoricalIncidentsThatWereShown() {
        String related = """
                [{"sourceRef": "%s", "relevance": "not an incident"},
                 {"sourceRef": "HISTORICAL_INCIDENT#incidents/INC-999.md", "relevance": "invented"},
                 {"sourceRef": "%s", "relevance": "same pattern"},
                 {"sourceRef": "%s", "relevance": "duplicate"}]
                """.formatted(RUNBOOK_REF, INC003_REF, INC003_REF);
        String evidence = "[{\"claim\": \"Timeouts\", \"sourceRefs\": [\"%s\"]}]".formatted(ERROR_TIMEOUT_REF);

        AnalysisResult result = parser.parse(answer("ROOT_CAUSE_IDENTIFIED", 0.7, evidence, related), pack());

        assertThat(result.relatedIncidents()).singleElement()
                .satisfies(incident -> assertThat(incident.sourceRef()).isEqualTo(INC003_REF));
        assertThat(result.grounding().invalidCitationsRemoved()).isEqualTo(2);
    }

    @Test
    void unrecognisedAssessmentIsNormalisedRatherThanTrusted() {
        String json = """
                {"status": "INSUFFICIENT_EVIDENCE", "rootCause": "Unclear", "confidence": 0.2,
                 "alternativeHypotheses": [{"hypothesis": "Failover", "assessment": "DEFINITELY", "reason": "?"}]}
                """;

        AnalysisResult result = parser.parse(json, pack());

        assertThat(result.alternativeHypotheses()).singleElement()
                .satisfies(alt -> assertThat(alt.assessment()).isEqualTo("NOT_EVALUABLE"));
    }

    @Test
    void listsAreCleanedDeduplicatedAndBounded() {
        String recommendations = IntStream.range(0, 15)
                .mapToObj(i -> "\"Action " + (i % 12) + "\"")
                .collect(Collectors.joining(",", "[\"  \",", "]"));
        String json = """
                {"status": "INSUFFICIENT_EVIDENCE", "rootCause": "Unclear", "confidence": 0.2,
                 "recommendations": %s}
                """.formatted(recommendations);

        AnalysisResult result = parser.parse(json, pack());

        assertThat(result.recommendations()).hasSize(10).doesNotHaveDuplicates().noneMatch(String::isBlank);
    }

    @Test
    void reportsMissingLogsAndTrimmedEvidence() {
        AnalysisResult result = parser.parse(validModelJson(), pack(false, 3, 2));

        assertThat(result.grounding().evidenceTruncated()).isTrue();
        assertThat(result.grounding().notes())
                .anyMatch(note -> note.contains("Log evidence was unavailable"))
                .anyMatch(note -> note.contains("3 timeline entries and 2 documents omitted"));
    }
}
