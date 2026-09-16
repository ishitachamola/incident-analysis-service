package com.incidentplatform.ai.analysis;

import com.incidentplatform.ai.incident.IncidentSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Everything the model is shown for one analysis, and therefore everything it is allowed to cite.
 *
 * <p>It deliberately holds no value that changes between otherwise identical requests, such as the
 * current time. The prompt built from it is fingerprinted to decide whether a stored analysis can be
 * reused, and any volatile value would make every fingerprint unique and every request billable.
 */
public record EvidencePack(
        IncidentSummary incident,
        String incidentRef,
        Instant timelineWindowStart,
        boolean logsAvailable,
        DeploymentCorrelation deploymentCorrelation,
        List<EvidenceItem> timeline,
        List<EvidenceItem> runbooks,
        List<EvidenceItem> historicalIncidents,
        int timelineEntriesOmitted,
        int documentsOmitted
) {

    public EvidencePack {
        timeline = List.copyOf(timeline);
        runbooks = List.copyOf(runbooks);
        historicalIncidents = List.copyOf(historicalIncidents);
    }

    public boolean truncated() {
        return timelineEntriesOmitted > 0 || documentsOmitted > 0;
    }

    /** References a valid answer may cite: the incident itself plus every evidence item shown. */
    public Set<String> citableRefs() {
        Set<String> refs = new LinkedHashSet<>();
        refs.add(incidentRef);
        allItems().forEach(item -> refs.add(item.ref()));
        return refs;
    }

    public Set<String> historicalIncidentRefs() {
        Set<String> refs = new LinkedHashSet<>();
        historicalIncidents.forEach(item -> refs.add(item.ref()));
        return refs;
    }

    public String titleOf(String ref) {
        return allItems().filter(item -> item.ref().equals(ref) && item.title() != null)
                .map(EvidenceItem::title)
                .findFirst()
                .orElse(null);
    }

    public List<AnalysisResult.SourceRef> sources() {
        List<AnalysisResult.SourceRef> sources = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        allItems().forEach(item -> {
            if (seen.add(item.ref())) {
                sources.add(new AnalysisResult.SourceRef(item.ref(), item.category().name(), item.title()));
            }
        });
        return sources;
    }

    private Stream<EvidenceItem> allItems() {
        return Stream.of(timeline, runbooks, historicalIncidents).flatMap(List::stream);
    }
}
