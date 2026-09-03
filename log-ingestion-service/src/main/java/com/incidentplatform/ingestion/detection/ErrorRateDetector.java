package com.incidentplatform.ingestion.detection;

import com.incidentplatform.events.IncidentDetectedEvent;
import com.incidentplatform.ingestion.repository.LogEntryRepository;
import com.incidentplatform.ingestion.repository.LogEntryRepository.ServiceErrorStats;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Evaluates a threshold rule over the ingested log stream on a fixed schedule, the way an alerting
 * engine evaluates rules, rather than re-checking on every consumed message.
 *
 * <p>The rule is intentionally simple and explainable: a service is considered to be in an incident
 * when it produces both enough errors and a high enough error rate within the evaluation window.
 * This service only decides <em>that</em> something is wrong; deciding <em>why</em> belongs to the
 * analysis pipeline.
 */
@Component
public class ErrorRateDetector {

    private static final Logger log = LoggerFactory.getLogger(ErrorRateDetector.class);
    static final String RULE_NAME = "error-rate-threshold";

    private final LogEntryRepository repository;
    private final DetectionProperties properties;
    private final IncidentDetectionPublisher publisher;

    /**
     * Last emission per service, used to suppress repeat alerts for an incident that is already
     * known. The incident service independently refuses to open a duplicate incident, so losing
     * this state on restart cannot produce duplicate incidents.
     */
    private final Map<String, Instant> lastEmission = new ConcurrentHashMap<>();

    public ErrorRateDetector(LogEntryRepository repository, DetectionProperties properties,
                              IncidentDetectionPublisher publisher) {
        this.repository = repository;
        this.properties = properties;
        this.publisher = publisher;
    }

    public List<IncidentDetectedEvent> evaluate() {
        if (!properties.enabled()) {
            return List.of();
        }
        Instant now = Instant.now();
        Instant windowStart = now.minus(properties.window());

        return repository.findErrorStatsBetween(windowStart, now).stream()
                .map(stats -> assess(stats, windowStart, now))
                .flatMap(Optional::stream)
                .peek(publisher::publish)
                .toList();
    }

    private Optional<IncidentDetectedEvent> assess(ServiceErrorStats stats, Instant windowStart, Instant now) {
        if (stats.getTotalCount() == 0 || stats.getErrorCount() < properties.minErrorCount()) {
            return Optional.empty();
        }
        double errorRate = (double) stats.getErrorCount() / stats.getTotalCount();
        if (errorRate < properties.errorRateThreshold()) {
            return Optional.empty();
        }
        if (withinCooldown(stats.getServiceName(), now)) {
            return Optional.empty();
        }

        lastEmission.put(stats.getServiceName(), now);
        String severity = severityFor(errorRate);
        IncidentDetectedEvent event = new IncidentDetectedEvent(
                UUID.randomUUID(),
                stats.getServiceName(),
                "Elevated error rate on " + stats.getServiceName(),
                severity,
                now,
                windowStart,
                now,
                stats.getErrorCount(),
                stats.getTotalCount(),
                round(errorRate),
                RULE_NAME);

        log.info("Detected incident on {}: {} errors of {} logs ({}%), severity {}",
                stats.getServiceName(), stats.getErrorCount(), stats.getTotalCount(),
                Math.round(errorRate * 100), severity);
        return Optional.of(event);
    }

    private boolean withinCooldown(String service, Instant now) {
        Instant last = lastEmission.get(service);
        return last != null && last.isAfter(now.minus(properties.cooldown()));
    }

    private static String severityFor(double errorRate) {
        if (errorRate >= 0.30) {
            return "CRITICAL";
        }
        if (errorRate >= 0.15) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
