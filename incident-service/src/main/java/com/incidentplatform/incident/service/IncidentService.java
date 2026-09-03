package com.incidentplatform.incident.service;

import com.incidentplatform.events.IncidentDetectedEvent;
import com.incidentplatform.incident.dto.CreateIncidentEventRequest;
import com.incidentplatform.incident.dto.CreateIncidentRequest;
import com.incidentplatform.incident.dto.IncidentEventResponse;
import com.incidentplatform.incident.dto.IncidentResponse;
import com.incidentplatform.incident.entity.Incident;
import com.incidentplatform.incident.entity.IncidentEvent;
import com.incidentplatform.incident.entity.IncidentSeverity;
import com.incidentplatform.incident.entity.IncidentStatus;
import com.incidentplatform.incident.entity.MonitoredService;
import com.incidentplatform.incident.exception.InvalidEventException;
import com.incidentplatform.incident.exception.ResourceNotFoundException;
import com.incidentplatform.incident.repository.IncidentEventRepository;
import com.incidentplatform.incident.repository.IncidentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    /** An incident in any of these states is still being worked, so it absorbs new detections. */
    private static final Set<IncidentStatus> ACTIVE_STATUSES =
            Set.of(IncidentStatus.DETECTED, IncidentStatus.INVESTIGATING, IncidentStatus.MITIGATED);

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final MonitoredServiceService monitoredServiceService;

    public IncidentService(IncidentRepository incidentRepository, IncidentEventRepository incidentEventRepository,
                            MonitoredServiceService monitoredServiceService) {
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.monitoredServiceService = monitoredServiceService;
    }

    public IncidentResponse create(CreateIncidentRequest request) {
        MonitoredService service = monitoredServiceService.getOrThrow(request.serviceId());
        IncidentStatus status = request.status() != null ? request.status() : IncidentStatus.DETECTED;
        Instant detectedAt = request.detectedAt() != null ? request.detectedAt() : Instant.now();
        Incident saved = incidentRepository.save(
                new Incident(service, request.title(), request.severity(), status, detectedAt));
        return IncidentResponse.from(saved);
    }

    /**
     * Opens an incident from a detection event, unless one is already open for that service.
     *
     * <p>Two guards apply. The event id makes redelivery a no-op, and the active-incident check
     * means a rule that keeps firing while a problem persists extends the existing investigation
     * rather than fragmenting it into many incidents.
     *
     * @return the opened incident, or empty if the detection was suppressed as a duplicate
     */
    public Optional<IncidentResponse> openFromDetection(IncidentDetectedEvent event) {
        validate(event);

        if (incidentRepository.existsByEventId(event.eventId())) {
            return Optional.empty();
        }

        MonitoredService service = monitoredServiceService.findOrCreateByName(event.service());
        Optional<Incident> active = incidentRepository
                .findFirstByServiceIdAndStatusInOrderByDetectedAtDesc(service.getId(), ACTIVE_STATUSES);
        if (active.isPresent()) {
            recordDetectionEvent(active.get(), event, "Detection rule fired again while this incident was open");
            log.debug("Suppressed duplicate detection for {}: incident {} is already active",
                    event.service(), active.get().getId());
            return Optional.empty();
        }

        Incident incident;
        try {
            incident = incidentRepository.save(new Incident(
                    service,
                    event.title(),
                    parseSeverity(event.severity()),
                    IncidentStatus.DETECTED,
                    event.detectedAt() != null ? event.detectedAt() : Instant.now(),
                    event.eventId(),
                    event.detectionRule()));
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }

        recordDetectionEvent(incident, event, "Incident opened by detection rule");
        log.info("Opened incident {} for service {} ({} errors of {} logs)",
                incident.getId(), event.service(), event.errorCount(), event.totalCount());
        return Optional.of(IncidentResponse.from(incident));
    }

    private void recordDetectionEvent(Incident incident, IncidentDetectedEvent event, String prefix) {
        String description = "%s: %d errors out of %d log entries (%.1f%%) between %s and %s"
                .formatted(prefix, event.errorCount(), event.totalCount(), event.errorRate() * 100,
                        event.windowStart(), event.windowEnd());
        incidentEventRepository.save(new IncidentEvent(
                incident,
                "DETECTION",
                description,
                event.detectedAt() != null ? event.detectedAt() : Instant.now(),
                "RULE#" + event.detectionRule()));
    }

    private static void validate(IncidentDetectedEvent event) {
        if (event == null || event.eventId() == null) {
            throw new InvalidEventException("Detection event is missing eventId");
        }
        if (event.service() == null || event.service().isBlank()) {
            throw new InvalidEventException("Detection event " + event.eventId() + " is missing service");
        }
        if (event.title() == null || event.title().isBlank()) {
            throw new InvalidEventException("Detection event " + event.eventId() + " is missing title");
        }
    }

    private static IncidentSeverity parseSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return IncidentSeverity.MEDIUM;
        }
        try {
            return IncidentSeverity.valueOf(severity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidEventException("Unsupported severity: " + severity);
        }
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> findAll(UUID serviceId, IncidentStatus status, IncidentSeverity severity) {
        return incidentRepository.findAll(IncidentSpecifications.withFilters(serviceId, status, severity)).stream()
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse findById(UUID id) {
        return IncidentResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Incident getOrThrow(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }

    public IncidentEventResponse addEvent(UUID incidentId, CreateIncidentEventRequest request) {
        Incident incident = getOrThrow(incidentId);
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : Instant.now();
        IncidentEvent saved = incidentEventRepository.save(
                new IncidentEvent(incident, request.type(), request.description(), occurredAt, request.sourceRef()));
        return IncidentEventResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<IncidentEventResponse> findEvents(UUID incidentId) {
        getOrThrow(incidentId);
        return incidentEventRepository.findByIncidentIdOrderByOccurredAtAsc(incidentId).stream()
                .map(IncidentEventResponse::from)
                .toList();
    }
}
