package com.incidentplatform.incident.service;

import com.incidentplatform.incident.dto.CreateServiceRequest;
import com.incidentplatform.incident.dto.ServiceResponse;
import com.incidentplatform.incident.entity.MonitoredService;
import com.incidentplatform.incident.exception.DuplicateResourceException;
import com.incidentplatform.incident.exception.ResourceNotFoundException;
import com.incidentplatform.incident.repository.MonitoredServiceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MonitoredServiceService {

    private final MonitoredServiceRepository repository;

    public MonitoredServiceService(MonitoredServiceRepository repository) {
        this.repository = repository;
    }

    public ServiceResponse create(CreateServiceRequest request) {
        if (repository.existsByName(request.name())) {
            throw new DuplicateResourceException("A service named '" + request.name() + "' already exists");
        }
        MonitoredService saved = repository.save(new MonitoredService(request.name(), request.description()));
        return ServiceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> findAll() {
        return repository.findAll().stream().map(ServiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse findById(UUID id) {
        return ServiceResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public MonitoredService getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
    }

    /**
     * Resolves a service by name, registering it if the platform has not seen it before. Events
     * arrive from the log stream keyed by service name, and a service must not have to be
     * pre-registered for its telemetry to be usable.
     */
    public MonitoredService findOrCreateByName(String name) {
        return repository.findByName(name).orElseGet(() -> {
            try {
                return repository.save(new MonitoredService(name, "Auto-registered from the event stream"));
            } catch (DataIntegrityViolationException ex) {
                // Another consumer registered the same service concurrently; its row is the winner.
                return repository.findByName(name)
                        .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + name));
            }
        });
    }
}
