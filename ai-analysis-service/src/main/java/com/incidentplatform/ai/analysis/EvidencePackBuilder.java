package com.incidentplatform.ai.analysis;

import com.incidentplatform.ai.config.AnalysisProperties;
import com.incidentplatform.ai.incident.IncidentSummary;
import com.incidentplatform.ai.incident.IncidentTimeline;
import com.incidentplatform.ai.knowledge.DocumentType;
import com.incidentplatform.ai.knowledge.KnowledgeRetrievalService;
import com.incidentplatform.ai.knowledge.RetrievedChunk;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Selects the evidence for one analysis and keeps it inside a fixed size budget.
 *
 * <p>The budget is enforced by dropping the least useful evidence first, in this order: surplus
 * runbooks, then surplus historical incidents, then the lowest-signal log lines. At least two
 * historical incidents are kept for as long as possible, because telling apart two precedents with
 * similar symptoms is exactly where the model needs more than one to compare. Deployments and
 * incident events are never dropped: they are few and carry the timing that separates correlation
 * from cause.
 */
@Component
public class EvidencePackBuilder {

    private static final Set<String> STRUCTURAL_TYPES = Set.of("DEPLOYMENT", "INCIDENT_EVENT");
    private static final int MAX_QUERY_CHARS = 1000;
    private static final int MIN_RUNBOOKS_KEPT = 1;
    private static final int MIN_INCIDENTS_KEPT = 2;

    private final KnowledgeRetrievalService retrievalService;
    private final AnalysisProperties properties;

    public EvidencePackBuilder(KnowledgeRetrievalService retrievalService, AnalysisProperties properties) {
        this.retrievalService = retrievalService;
        this.properties = properties;
    }

    public EvidencePack build(IncidentSummary incident, IncidentTimeline timeline) {
        List<IncidentTimeline.Entry> entries = timeline.entries().stream()
                .filter(entry -> entry.timestamp() != null && entry.sourceRef() != null)
                .toList();

        List<IncidentTimeline.Entry> selected = selectTimeline(entries);
        int timelineOmitted = entries.size() - selected.size();

        String query = retrievalQuery(incident, entries);
        List<EvidenceItem> runbooks = toDocuments(
                retrievalService.search(query, DocumentType.RUNBOOK, incident.serviceName(), properties.runbookTopK()),
                EvidenceItem.Category.RUNBOOK);
        List<EvidenceItem> incidents = toDocuments(
                retrievalService.search(query, DocumentType.HISTORICAL_INCIDENT, incident.serviceName(),
                        properties.incidentTopK()),
                EvidenceItem.Category.HISTORICAL_INCIDENT);

        List<EvidenceItem> timelineItems = new ArrayList<>(selected.stream().map(this::toTimelineItem).toList());
        List<IncidentTimeline.Entry> selectedEntries = new ArrayList<>(selected);

        int documentsOmitted = 0;
        while (totalChars(timelineItems, runbooks, incidents) > properties.maxEvidenceChars()) {
            if (runbooks.size() > MIN_RUNBOOKS_KEPT) {
                runbooks.removeLast();
                documentsOmitted++;
            } else if (incidents.size() > MIN_INCIDENTS_KEPT) {
                incidents.removeLast();
                documentsOmitted++;
            } else if (removeLowestSignalLog(selectedEntries, timelineItems)) {
                timelineOmitted++;
            } else if (!runbooks.isEmpty()) {
                runbooks.removeLast();
                documentsOmitted++;
            } else if (!incidents.isEmpty()) {
                incidents.removeLast();
                documentsOmitted++;
            } else {
                break;
            }
        }

        return new EvidencePack(
                incident,
                "INCIDENT#" + incident.id(),
                timeline.windowStart(),
                timeline.logsAvailable(),
                correlateDeployment(incident, entries, timeline),
                timelineItems,
                runbooks,
                incidents,
                timelineOmitted,
                documentsOmitted);
    }

    /** Keeps every deployment and incident event, then the highest-signal logs, in time order. */
    private List<IncidentTimeline.Entry> selectTimeline(List<IncidentTimeline.Entry> entries) {
        List<IncidentTimeline.Entry> structural = entries.stream()
                .filter(entry -> STRUCTURAL_TYPES.contains(entry.type()))
                .toList();
        int logSlots = Math.max(0, properties.maxTimelineEntries() - structural.size());
        List<IncidentTimeline.Entry> logs = entries.stream()
                .filter(entry -> !STRUCTURAL_TYPES.contains(entry.type()))
                .sorted(BY_SIGNAL)
                .limit(logSlots)
                .toList();

        List<IncidentTimeline.Entry> selected = new ArrayList<>(structural);
        selected.addAll(logs);
        selected.sort(CHRONOLOGICAL);
        return selected;
    }

    private boolean removeLowestSignalLog(List<IncidentTimeline.Entry> entries, List<EvidenceItem> items) {
        return entries.stream()
                .filter(entry -> !STRUCTURAL_TYPES.contains(entry.type()))
                .max(BY_SIGNAL)
                .map(weakest -> {
                    int index = entries.indexOf(weakest);
                    entries.remove(index);
                    items.remove(index);
                    return true;
                })
                .orElse(false);
    }

    private DeploymentCorrelation correlateDeployment(IncidentSummary incident, List<IncidentTimeline.Entry> entries,
                                                      IncidentTimeline timeline) {
        Instant detectedAt = incident.detectedAt();
        Instant firstErrorAt = entries.stream()
                .filter(entry -> "LOG".equals(entry.type()))
                .filter(entry -> "ERROR".equals(entry.level()) || "FATAL".equals(entry.level()))
                .map(IncidentTimeline.Entry::timestamp)
                .min(Comparator.naturalOrder())
                .orElse(null);

        IncidentTimeline.Entry latestDeployment = entries.stream()
                .filter(entry -> "DEPLOYMENT".equals(entry.type()))
                .filter(entry -> detectedAt == null || !entry.timestamp().isAfter(detectedAt))
                .max(CHRONOLOGICAL)
                .orElse(null);

        String logNote = timeline.logsAvailable() ? "" : " Log evidence was unavailable, so error timing is unknown.";

        if (latestDeployment == null) {
            return new DeploymentCorrelation(false, null, null, null, null, firstErrorAt, null,
                    "No deployment was recorded between %s and detection.".formatted(timeline.windowStart()) + logNote);
        }

        Long minutesBeforeDetection = detectedAt != null
                ? Duration.between(latestDeployment.timestamp(), detectedAt).toMinutes() : null;
        Long minutesToFirstError = firstErrorAt != null
                ? Duration.between(latestDeployment.timestamp(), firstErrorAt).toMinutes() : null;

        String note;
        if (minutesToFirstError != null && minutesToFirstError < 0) {
            note = "Errors began %d minutes BEFORE this deployment, which is evidence against it being the cause."
                    .formatted(-minutesToFirstError);
        } else if (minutesToFirstError != null) {
            note = "The first error appeared %d minutes after this deployment. This is timing correlation only."
                    .formatted(minutesToFirstError);
        } else {
            note = "No error timing is available to compare with this deployment.";
        }

        return new DeploymentCorrelation(true, latestDeployment.sourceRef(), latestDeployment.summary(),
                latestDeployment.timestamp(), minutesBeforeDetection, firstErrorAt, minutesToFirstError,
                note + logNote);
    }

    /** Builds the similarity query from what actually went wrong, not the generic incident title. */
    private String retrievalQuery(IncidentSummary incident, List<IncidentTimeline.Entry> entries) {
        Set<String> parts = new LinkedHashSet<>();
        if (incident.title() != null) {
            parts.add(incident.title());
        }
        entries.stream()
                .filter(entry -> "LOG".equals(entry.type()))
                .sorted(BY_SIGNAL)
                .map(entry -> stripOccurrenceSuffix(entry.summary()))
                .filter(Objects::nonNull)
                .limit(8)
                .forEach(parts::add);

        String query = String.join(". ", parts);
        return query.length() <= MAX_QUERY_CHARS ? query : query.substring(0, MAX_QUERY_CHARS);
    }

    /** Merges the retrieved chunks of each source document into one citable item, best match first. */
    private List<EvidenceItem> toDocuments(List<RetrievedChunk> chunks, EvidenceItem.Category category) {
        Map<String, List<RetrievedChunk>> bySource = new LinkedHashMap<>();
        chunks.forEach(chunk -> bySource.computeIfAbsent(chunk.citation(), key -> new ArrayList<>()).add(chunk));

        List<EvidenceItem> items = new ArrayList<>();
        bySource.forEach((ref, sourceChunks) -> {
            StringBuilder text = new StringBuilder();
            Set<String> seenSections = new LinkedHashSet<>();
            for (RetrievedChunk chunk : sourceChunks) {
                String section = chunk.section() != null ? chunk.section() : "Overview";
                if (seenSections.add(section)) {
                    text.append("## ").append(section).append('\n')
                            .append(withoutContextHeader(chunk.text())).append("\n\n");
                }
            }
            items.add(new EvidenceItem(ref, category, sourceChunks.getFirst().title(),
                    truncate(text.toString().strip(), properties.maxDocumentChars())));
        });
        return items;
    }

    private EvidenceItem toTimelineItem(IncidentTimeline.Entry entry) {
        StringBuilder line = new StringBuilder()
                .append(entry.timestamp()).append(' ').append(entry.type());
        if (entry.level() != null) {
            line.append(' ').append(entry.level());
        }
        if (entry.occurrences() > 1) {
            line.append(" x").append(entry.occurrences());
        }
        line.append(": ").append(truncate(entry.summary(), properties.maxEntrySummaryChars()));
        return new EvidenceItem(entry.sourceRef(), EvidenceItem.Category.TIMELINE, null, line.toString());
    }

    /** Chunks carry a "Title (service) — Section" header for retrieval; the item already has both. */
    private static String withoutContextHeader(String text) {
        int boundary = text.indexOf("\n\n");
        return boundary > 0 ? text.substring(boundary + 2).strip() : text.strip();
    }

    private static String stripOccurrenceSuffix(String summary) {
        return summary == null ? null : summary.replaceFirst(" \\(x\\d+\\)$", "");
    }

    private static int totalChars(List<EvidenceItem> timeline, List<EvidenceItem> runbooks,
                                  List<EvidenceItem> incidents) {
        return List.of(timeline, runbooks, incidents).stream()
                .flatMap(List::stream)
                .mapToInt(item -> item.text().length() + item.ref().length())
                .sum();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + " [truncated]";
    }

    private static int levelRank(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case "FATAL" -> 4;
            case "ERROR" -> 3;
            case "WARN" -> 2;
            default -> 1;
        };
    }

    /** Strongest signal first: severity, then how often it repeated, then how early it appeared. */
    private static final Comparator<IncidentTimeline.Entry> BY_SIGNAL =
            Comparator.comparingInt((IncidentTimeline.Entry entry) -> levelRank(entry.level())).reversed()
                    .thenComparing(Comparator.comparingInt(IncidentTimeline.Entry::occurrences).reversed())
                    .thenComparing(IncidentTimeline.Entry::timestamp)
                    .thenComparing(IncidentTimeline.Entry::sourceRef);

    private static final Comparator<IncidentTimeline.Entry> CHRONOLOGICAL =
            Comparator.comparing(IncidentTimeline.Entry::timestamp)
                    .thenComparing(IncidentTimeline.Entry::sourceRef);
}
