package com.incidentplatform.ai.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentplatform.ai.analysis.AnalysisResult.AlternativeHypothesis;
import com.incidentplatform.ai.analysis.AnalysisResult.AnalysisStatus;
import com.incidentplatform.ai.analysis.AnalysisResult.EvidenceClaim;
import com.incidentplatform.ai.analysis.AnalysisResult.GroundingReport;
import com.incidentplatform.ai.analysis.AnalysisResult.RelatedIncident;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns the model's raw text into a trustworthy {@link AnalysisResult}.
 *
 * <p>Two kinds of problem are handled differently. <strong>Structural</strong> problems — not JSON,
 * a missing root cause, an out-of-range confidence — mean the answer cannot be used at all, so they
 * throw and the caller may spend one repair call. <strong>Grounding</strong> problems are corrected
 * in place rather than rejected: a citation to a reference the model was never shown is removed, a
 * claim left with no valid citation is marked unverified, and an answer that names a root cause
 * without a single verified claim is downgraded to insufficient evidence. Every correction is
 * reported, so validation never silently rewrites what the model said.
 */
@Component
public class AnalysisOutputParser {

    static final double INSUFFICIENT_EVIDENCE_MAX_CONFIDENCE = 0.4;
    static final double UNVERIFIED_ROOT_CAUSE_MAX_CONFIDENCE = 0.3;

    private static final int MAX_CLAIMS = 15;
    private static final int MAX_ALTERNATIVES = 5;
    private static final int MAX_LIST_ITEMS = 10;
    private static final int MAX_RELATED = 5;
    private static final int MAX_TEXT = 500;
    private static final int MAX_ROOT_CAUSE = 1000;
    private static final Set<String> ASSESSMENTS = Set.of("RULED_OUT", "LESS_LIKELY", "NOT_EVALUABLE");

    private final ObjectMapper objectMapper;

    public AnalysisOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisResult parse(String rawOutput, EvidencePack pack) {
        ModelAnalysis raw = readJson(rawOutput);

        AnalysisStatus status = parseStatus(raw.status());
        double confidence = parseConfidence(raw.confidence());
        String rootCause = raw.rootCause() == null ? "" : raw.rootCause().strip();
        if (rootCause.isEmpty()) {
            throw new InvalidModelOutputException("rootCause must be a non-empty string");
        }

        Set<String> citable = pack.citableRefs();
        List<String> notes = new ArrayList<>();
        int[] invalidCitations = {0};

        List<EvidenceClaim> claims = new ArrayList<>();
        for (ModelAnalysis.Claim claim : nullToEmpty(raw.evidence())) {
            if (claim == null || isBlank(claim.claim()) || claims.size() >= MAX_CLAIMS) {
                continue;
            }
            List<String> refs = keepCitable(claim.sourceRefs(), citable, invalidCitations);
            claims.add(new EvidenceClaim(truncate(claim.claim(), MAX_TEXT), refs, !refs.isEmpty()));
        }

        List<AlternativeHypothesis> alternatives = new ArrayList<>();
        for (ModelAnalysis.Alternative alternative : nullToEmpty(raw.alternativeHypotheses())) {
            if (alternative == null || isBlank(alternative.hypothesis()) || alternatives.size() >= MAX_ALTERNATIVES) {
                continue;
            }
            String assessment = alternative.assessment() == null ? "" : alternative.assessment().strip().toUpperCase(Locale.ROOT);
            alternatives.add(new AlternativeHypothesis(
                    truncate(alternative.hypothesis(), MAX_TEXT),
                    ASSESSMENTS.contains(assessment) ? assessment : "NOT_EVALUABLE",
                    truncate(alternative.reason(), MAX_TEXT),
                    keepCitable(alternative.sourceRefs(), citable, invalidCitations)));
        }

        Set<String> incidentRefs = pack.historicalIncidentRefs();
        List<RelatedIncident> related = new ArrayList<>();
        Set<String> seenRelated = new LinkedHashSet<>();
        for (ModelAnalysis.Related candidate : nullToEmpty(raw.relatedIncidents())) {
            if (candidate == null || candidate.sourceRef() == null) {
                continue;
            }
            String ref = candidate.sourceRef().strip();
            if (!incidentRefs.contains(ref)) {
                invalidCitations[0]++;
                continue;
            }
            if (seenRelated.add(ref) && related.size() < MAX_RELATED) {
                related.add(new RelatedIncident(ref, pack.titleOf(ref), truncate(candidate.relevance(), MAX_TEXT)));
            }
        }

        long verified = claims.stream().filter(EvidenceClaim::verified).count();
        boolean downgraded = false;
        boolean confidenceCapped = false;

        if (status == AnalysisStatus.ROOT_CAUSE_IDENTIFIED && verified == 0) {
            status = AnalysisStatus.INSUFFICIENT_EVIDENCE;
            downgraded = true;
            notes.add("A root cause was proposed but no claim cited evidence that was actually provided, "
                    + "so the analysis was downgraded to insufficient evidence.");
            if (confidence > UNVERIFIED_ROOT_CAUSE_MAX_CONFIDENCE) {
                confidence = UNVERIFIED_ROOT_CAUSE_MAX_CONFIDENCE;
                confidenceCapped = true;
            }
        }
        if (status == AnalysisStatus.INSUFFICIENT_EVIDENCE && confidence > INSUFFICIENT_EVIDENCE_MAX_CONFIDENCE) {
            confidence = INSUFFICIENT_EVIDENCE_MAX_CONFIDENCE;
            confidenceCapped = true;
            notes.add("Confidence was capped at %.1f because the evidence is insufficient."
                    .formatted(INSUFFICIENT_EVIDENCE_MAX_CONFIDENCE));
        }
        if (invalidCitations[0] > 0) {
            notes.add("%d citation(s) referred to evidence that was not provided and were removed."
                    .formatted(invalidCitations[0]));
        }
        if (!pack.logsAvailable()) {
            notes.add("Log evidence was unavailable when this analysis was produced.");
        }
        if (pack.truncated()) {
            notes.add("Evidence was trimmed to fit the size budget: %d timeline entries and %d documents omitted."
                    .formatted(pack.timelineEntriesOmitted(), pack.documentsOmitted()));
        }

        return new AnalysisResult(
                status,
                truncate(rootCause, MAX_ROOT_CAUSE),
                confidence,
                cleanList(raw.affectedServices(), 100),
                claims,
                alternatives,
                cleanList(raw.contributingFactors(), MAX_TEXT),
                cleanList(raw.recommendations(), MAX_TEXT),
                related,
                pack.deploymentCorrelation(),
                pack.sources(),
                new GroundingReport(claims.size(), (int) verified, invalidCitations[0], downgraded,
                        confidenceCapped, pack.truncated(), notes));
    }

    private ModelAnalysis readJson(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new InvalidModelOutputException("the response was empty");
        }
        String text = rawOutput.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidModelOutputException("the response did not contain a JSON object");
        }
        try {
            ModelAnalysis parsed = objectMapper.readValue(text.substring(start, end + 1), ModelAnalysis.class);
            if (parsed == null) {
                throw new InvalidModelOutputException("the JSON object was empty");
            }
            return parsed;
        } catch (JsonProcessingException ex) {
            throw new InvalidModelOutputException("the response was not valid JSON: " + ex.getOriginalMessage());
        }
    }

    private static AnalysisStatus parseStatus(String status) {
        if (status == null) {
            throw new InvalidModelOutputException("status is required");
        }
        try {
            return AnalysisStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidModelOutputException(
                    "status must be ROOT_CAUSE_IDENTIFIED or INSUFFICIENT_EVIDENCE, got: " + status);
        }
    }

    private static double parseConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN() || confidence < 0 || confidence > 1) {
            throw new InvalidModelOutputException("confidence must be a number between 0 and 1, got: " + confidence);
        }
        return confidence;
    }

    private static List<String> keepCitable(List<String> refs, Set<String> citable, int[] invalidCount) {
        List<String> kept = new ArrayList<>();
        for (String ref : nullToEmpty(refs)) {
            if (ref == null) {
                continue;
            }
            String trimmed = ref.strip();
            if (citable.contains(trimmed)) {
                if (!kept.contains(trimmed)) {
                    kept.add(trimmed);
                }
            } else {
                invalidCount[0]++;
            }
        }
        return kept;
    }

    private static List<String> cleanList(List<String> values, int maxLength) {
        return nullToEmpty(values).stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> truncate(value, maxLength))
                .distinct()
                .limit(MAX_LIST_ITEMS)
                .toList();
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values != null ? values : List.of();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }
}
