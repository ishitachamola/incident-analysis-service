package com.incidentplatform.incident.service;

import com.incidentplatform.incident.dto.CreateIncidentEventRequest;
import com.incidentplatform.incident.dto.CreateIncidentRequest;
import com.incidentplatform.incident.dto.IncidentEventResponse;
import com.incidentplatform.incident.dto.IncidentResponse;
import com.incidentplatform.incident.entity.Incident;
import com.incidentplatform.incident.entity.IncidentEvent;
import com.incidentplatform.incident.entity.IncidentSeverity;
import com.incidentplatform.incident.entity.IncidentStatus;
import com.incidentplatform.incident.entity.MonitoredService;
import com.incidentplatform.incident.exception.ResourceNotFoundException;
import com.incidentplatform.incident.repository.IncidentEventRepository;
import com.incidentplatform.incident.repository.IncidentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IncidentService {

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
