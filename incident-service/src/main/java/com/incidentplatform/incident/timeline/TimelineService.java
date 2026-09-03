package com.incidentplatform.incident.timeline;

import com.incidentplatform.incident.client.LogClient;
import com.incidentplatform.incident.client.LogEntryDto;
import com.incidentplatform.incident.entity.Deployment;
import com.incidentplatform.incident.entity.Incident;
import com.incidentplatform.incident.entity.IncidentEvent;
import com.incidentplatform.incident.repository.DeploymentRepository;
import com.incidentplatform.incident.repository.IncidentEventRepository;
import com.incidentplatform.incident.service.IncidentService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles an incident timeline by merging deployments, incident events and logs onto one
 * chronological axis.
 *
 * <p>Raw logs are far too voluminous to show line by line, so repeated log lines are collapsed into
 * a single entry carrying the first occurrence and a count. That turns hundreds of near-identical
 * errors into the handful of distinct facts an engineer actually reads.
 */
@Service
public class TimelineService {

    private static final Set<String> NOTEWORTHY_LEVELS = Set.of("WARN", "ERROR", "FATAL");
    private static final int LOG_FETCH_LIMIT = 1000;

    private final IncidentService incidentService;
    private final DeploymentRepository deploymentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final LogClient logClient;
    private final Duration lookback;

    public TimelineService(IncidentService incidentService,
                            DeploymentRepository deploymentRepository,
                            IncidentEventRepository incidentEventRepository,
                            LogClient logClient,
                            @Value("${platform.timeline.lookback:30m}") Duration lookback) {
        this.incidentService = incidentService;
        this.deploymentRepository = deploymentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.logClient = logClient;
        this.lookback = lookback;
    }

    @Transactional(readOnly = true)
    public TimelineResponse buildTimeline(UUID incidentId) {
        Incident incident = incidentService.getOrThrow(incidentId);
        String serviceName = incident.getService().getName();

        Instant windowStart = incident.getDetectedAt().minus(lookback);
        Instant windowEnd = incident.getResolvedAt() != null ? incident.getResolvedAt() : Instant.now();

        List<TimelineEntry> entries = new ArrayList<>();
        entries.addAll(deploymentEntries(incident.getService().getId(), windowStart, windowEnd));
        entries.addAll(incidentEventEntries(incidentId));

        Optional<List<LogEntryDto>> logs = logClient.findLogs(serviceName, windowStart, windowEnd, LOG_FETCH_LIMIT);
        logs.ifPresent(entryList -> entries.addAll(collapseLogs(entryList)));

        entries.sort(Comparator.comparing(TimelineEntry::timestamp));

        return new TimelineResponse(
                incidentId, serviceName, windowStart, windowEnd, logs.isPresent(), List.copyOf(entries));
    }

    private List<TimelineEntry> deploymentEntries(UUID serviceId, Instant from, Instant to) {
        return deploymentRepository.findByServiceIdAndDeployedAtBetweenOrderByDeployedAtAsc(serviceId, from, to)
                .stream()
                .map(this::toEntry)
                .toList();
    }

    private TimelineEntry toEntry(Deployment deployment) {
        return new TimelineEntry(
                deployment.getDeployedAt(),
                "DEPLOYMENT",
                "Deployed %s (%s)".formatted(deployment.getVersion(), deployment.getStatus()),
                null,
                "DEPLOY#" + deployment.getId(),
                1);
    }

    private List<TimelineEntry> incidentEventEntries(UUID incidentId) {
        return incidentEventRepository.findByIncidentIdOrderByOccurredAtAsc(incidentId).stream()
                .map(this::toEntry)
                .toList();
    }

    private TimelineEntry toEntry(IncidentEvent event) {
        return new TimelineEntry(
                event.getOccurredAt(),
                "INCIDENT_EVENT",
                event.getDescription(),
                null,
                event.getSourceRef() != null ? event.getSourceRef() : "EVENT#" + event.getId(),
                1);
    }

    /**
     * Collapses repeated log lines into one entry per distinct level and message, anchored at the
     * first occurrence. Only WARN and above make the timeline; healthy baseline traffic is noise here.
     */
    private List<TimelineEntry> collapseLogs(List<LogEntryDto> logs) {
        record Signature(String level, String message) {
        }

        Map<Signature, List<LogEntryDto>> grouped = new LinkedHashMap<>();
        logs.stream()
                .filter(entry -> entry.level() != null && NOTEWORTHY_LEVELS.contains(entry.level()))
                .sorted(Comparator.comparing(LogEntryDto::occurredAt))
                .forEach(entry -> grouped
                        .computeIfAbsent(new Signature(entry.level(), entry.message()), key -> new ArrayList<>())
                        .add(entry));

        return grouped.entrySet().stream()
                .map(group -> {
                    List<LogEntryDto> occurrences = group.getValue();
                    LogEntryDto first = occurrences.getFirst();
                    String summary = occurrences.size() == 1
                            ? first.message()
                            : "%s (x%d)".formatted(first.message(), occurrences.size());
                    return new TimelineEntry(
                            first.occurredAt(),
                            "LOG",
                            summary,
                            first.level(),
                            "LOG#" + first.id(),
                            occurrences.size());
                })
                .toList();
    }
}
