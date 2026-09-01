package com.incidentplatform.incident.controller;

import com.incidentplatform.incident.dto.CreateServiceRequest;
import com.incidentplatform.incident.dto.ServiceResponse;
import com.incidentplatform.incident.service.MonitoredServiceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final MonitoredServiceService service;

    public ServiceController(MonitoredServiceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/services/" + created.id())).body(created);
    }

    @GetMapping
    public List<ServiceResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ServiceResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }
}
