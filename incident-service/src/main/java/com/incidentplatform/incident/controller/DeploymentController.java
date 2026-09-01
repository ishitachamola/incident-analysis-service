package com.incidentplatform.incident.controller;

import com.incidentplatform.incident.dto.CreateDeploymentRequest;
import com.incidentplatform.incident.dto.DeploymentResponse;
import com.incidentplatform.incident.service.DeploymentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentService service;

    public DeploymentController(DeploymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeploymentResponse> create(@Valid @RequestBody CreateDeploymentRequest request) {
        DeploymentResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/deployments/" + created.id())).body(created);
    }

    @GetMapping
    public List<DeploymentResponse> findAll(@RequestParam(name = "service", required = false) UUID serviceId) {
        return serviceId != null ? service.findByService(serviceId) : service.findAll();
    }
}
