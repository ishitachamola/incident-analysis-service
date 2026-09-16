package com.incidentplatform.ai.analysis;

import com.incidentplatform.ai.incident.IncidentSummary;
import com.incidentplatform.ai.incident.IncidentTimeline;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Evidence modelled on the connection pool exhaustion scenario the simulator produces. */
public final class AnalysisFixtures {

    public static final Instant DETECTED_AT = Instant.parse("2026-09-16T10:30:00Z");
    public static final String DEPLOY_REF = "DEPLOY#7ee15582-c89b-4925-ad80-7142ef4536de";
    public static final String WARN_POOL_REF = "LOG#a13ae8b8-2805-44f8-816d-50f397e1022f";
    public static final String ERROR_TIMEOUT_REF = "LOG#168fef4a-97ed-4aaa-89c3-33a37c13114b";
    public static final String ERROR_500_REF = "LOG#6c5a36b6-4870-4ed4-a10b-64f591f1698c";
    public static final String DETECTION_REF = "RULE#error-rate-threshold";
    public static final String RUNBOOK_REF = "RUNBOOK#runbooks/database-connection-pool-exhaustion.md";
    public static final String INC003_REF = "HISTORICAL_INCIDENT#incidents/INC-003-payment-db-pool-exhaustion.md";
    public static final String INC006_REF = "HISTORICAL_INCIDENT#incidents/INC-006-payment-db-failover.md";

    private AnalysisFixtures() {
    }

    public static IncidentSummary incident(UUID id) {
        return new IncidentSummary(id, UUID.fromString("0a628752-83e1-4b3c-bd8c-c11f677a4e46"), "payment-service",
                "Elevated error rate on payment-service", "CRITICAL", "DETECTED", DETECTED_AT, null);
    }

    public static IncidentTimeline timeline(UUID id) {
        return new IncidentTimeline(id, "payment-service", DETECTED_AT.minus(Duration.ofMinutes(30)),
                DETECTED_AT, true, timelineEntries());
    }

    public static List<IncidentTimeline.Entry> timelineEntries() {
        return new ArrayList<>(List.of(
                entry(-13, "DEPLOYMENT", null, "Deployed v2.4.1 (SUCCESS)", DEPLOY_REF, 1),
                entry(-10, "LOG", "WARN",
                        "HikariPool-1 - Connection is not available, request timed out after 5000ms (x6)",
                        WARN_POOL_REF, 6),
                entry(-8, "LOG", "ERROR",
                        "Database connection timeout while acquiring connection from pool (x18)",
                        ERROR_TIMEOUT_REF, 18),
                entry(-5, "LOG", "ERROR",
                        "Unhandled exception processing payment request, returning HTTP 500 (x14)",
                        ERROR_500_REF, 14),
                entry(0, "INCIDENT_EVENT", null,
                        "Incident opened by detection rule: 68 errors out of 78 log entries (87.2%)",
                        DETECTION_REF, 1)));
    }

    public static IncidentTimeline.Entry entry(int minutesFromDetection, String type, String level, String summary,
                                               String ref, int occurrences) {
        return new IncidentTimeline.Entry(DETECTED_AT.plus(Duration.ofMinutes(minutesFromDetection)), type, summary,
                level, ref, occurrences);
    }

    /** A pack built without retrieval, for tests of prompt building and output parsing. */
    public static EvidencePack pack() {
        return pack(true, 0, 0);
    }

    public static EvidencePack pack(boolean logsAvailable, int timelineOmitted, int documentsOmitted) {
        UUID id = UUID.fromString("f5ac4a8a-7e05-46b7-9fdd-48b4a4ba7dbd");
        List<EvidenceItem> timeline = timelineEntries().stream()
                .map(entry -> new EvidenceItem(entry.sourceRef(), EvidenceItem.Category.TIMELINE, null,
                        entry.timestamp() + " " + entry.type() + ": " + entry.summary()))
                .toList();
        return new EvidencePack(
                incident(id),
                "INCIDENT#" + id,
                DETECTED_AT.minus(Duration.ofMinutes(30)),
                logsAvailable,
                new DeploymentCorrelation(true, DEPLOY_REF, "Deployed v2.4.1 (SUCCESS)",
                        DETECTED_AT.minus(Duration.ofMinutes(13)), 13L, DETECTED_AT.minus(Duration.ofMinutes(8)), 5L,
                        "The first error appeared 5 minutes after this deployment. This is timing correlation only."),
                timeline,
                List.of(new EvidenceItem(RUNBOOK_REF, EvidenceItem.Category.RUNBOOK,
                        "Database Connection Pool Exhaustion", "## Resolution\nRoll back first.")),
                List.of(
                        new EvidenceItem(INC003_REF, EvidenceItem.Category.HISTORICAL_INCIDENT,
                                "INC-003: Database connection pool exhaustion in payment-service",
                                "## Root cause\nDeployment v2.4.1 doubled connections per request."),
                        new EvidenceItem(INC006_REF, EvidenceItem.Category.HISTORICAL_INCIDENT,
                                "INC-006: Payment errors during an unplanned database failover",
                                "## Root cause\nAn unplanned failover invalidated every pooled connection.")),
                timelineOmitted,
                documentsOmitted);
    }

    /** A well-formed answer citing only evidence that the fixtures provide. */
    public static String validModelJson() {
        return """
                {
                  "status": "ROOT_CAUSE_IDENTIFIED",
                  "rootCause": "Database connection pool exhaustion following deployment v2.4.1.",
                  "confidence": 0.82,
                  "affectedServices": ["payment-service"],
                  "evidence": [
                    {"claim": "Connection timeouts repeated 18 times after the deployment.", "sourceRefs": ["%s"]},
                    {"claim": "Deployment v2.4.1 preceded the first error by 5 minutes.", "sourceRefs": ["%s"]}
                  ],
                  "alternativeHypotheses": [
                    {"hypothesis": "Database failover", "assessment": "LESS_LIKELY",
                     "reason": "Errors built gradually after a deployment rather than starting instantly.",
                     "sourceRefs": ["%s"]}
                  ],
                  "contributingFactors": ["Pool sized with little headroom"],
                  "recommendations": ["Roll back v2.4.1", "Review connection usage in the release"],
                  "relatedIncidents": [{"sourceRef": "%s", "relevance": "Same failure after a deployment"}]
                }
                """.formatted(ERROR_TIMEOUT_REF, DEPLOY_REF, WARN_POOL_REF, INC003_REF);
    }
}
