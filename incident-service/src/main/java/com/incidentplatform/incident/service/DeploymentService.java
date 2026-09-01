package com.incidentplatform.incident.service;

import com.incidentplatform.incident.dto.CreateDeploymentRequest;
import com.incidentplatform.incident.dto.DeploymentResponse;
import com.incidentplatform.incident.entity.Deployment;
import com.incidentplatform.incident.entity.DeploymentStatus;
import com.incidentplatform.incident.entity.MonitoredService;
import com.incidentplatform.incident.repository.DeploymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
