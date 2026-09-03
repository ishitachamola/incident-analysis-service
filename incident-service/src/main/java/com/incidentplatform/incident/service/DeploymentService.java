package com.incidentplatform.incident.service;

import com.incidentplatform.events.DeploymentEvent;
import com.incidentplatform.incident.dto.CreateDeploymentRequest;
import com.incidentplatform.incident.dto.DeploymentResponse;
import com.incidentplatform.incident.entity.Deployment;
import com.incidentplatform.incident.entity.DeploymentStatus;
import com.incidentplatform.incident.entity.MonitoredService;
import com.incidentplatform.incident.exception.InvalidEventException;
import com.incidentplatform.incident.repository.DeploymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeploymentService {

    private final DeploymentRepository repository;
    private final MonitoredServiceService monitoredServiceService;

    public DeploymentService(DeploymentRepository repository, MonitoredServiceService monitoredServiceService) {
        this.repository = repository;
        this.monitoredServiceService = monitoredServiceService;
    }

    public DeploymentResponse create(CreateDeploymentRequest request) {
        MonitoredService service = monitoredServiceService.getOrThrow(request.serviceId());
        Instant deployedAt = request.deployedAt() != null ? request.deployedAt() : Instant.now();
        DeploymentStatus status = request.status() != null ? request.status() : DeploymentStatus.SUCCESS;
        Deployment saved = repository.save(new Deployment(service, request.version(), deployedAt, status));
        return DeploymentResponse.from(saved);
    }

    /**
     * Records a deployment announced on the event stream.
     *
     * @return {@code true} if it was stored, {@code false} if this event was already processed
     */
    public boolean recordFromEvent(DeploymentEvent event) {
        if (event.eventId() != null && repository.existsByEventId(event.eventId())) {
            return false;
        }
        MonitoredService service = monitoredServiceService.findOrCreateByName(event.service());
        DeploymentStatus status = parseStatus(event.status());
        Instant deployedAt = event.deployedAt() != null ? event.deployedAt() : Instant.now();

        try {
            repository.save(new Deployment(service, event.version(), deployedAt, status, event.eventId()));
            return true;
        } catch (DataIntegrityViolationException ex) {
            // Lost a race against a concurrent delivery of the same event; the constraint decides.
            return false;
        }
    }

    private static DeploymentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return DeploymentStatus.SUCCESS;
        }
        try {
            return DeploymentStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidEventException("Unsupported deployment status: " + status);
        }
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> findByService(UUID serviceId) {
        return repository.findByServiceIdOrderByDeployedAtDesc(serviceId).stream()
                .map(DeploymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> findAll() {
        return repository.findAll().stream().map(DeploymentResponse::from).toList();
    }
}
