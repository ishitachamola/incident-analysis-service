package com.incidentplatform.incident.controller;

import com.incidentplatform.incident.dto.CreateIncidentEventRequest;
import com.incidentplatform.incident.dto.CreateIncidentRequest;
import com.incidentplatform.incident.dto.IncidentEventResponse;
import com.incidentplatform.incident.dto.IncidentResponse;
import com.incidentplatform.incident.entity.IncidentSeverity;
import com.incidentplatform.incident.entity.IncidentStatus;
import com.incidentplatform.incident.service.IncidentService;
import com.incidentplatform.incident.timeline.TimelineResponse;
import com.incidentplatform.incident.timeline.TimelineService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService service;
    private final TimelineService timelineService;

    public IncidentController(IncidentService service, TimelineService timelineService) {
        this.service = service;
        this.timelineService = timelineService;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request) {
        IncidentResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/incidents/" + created.id())).body(created);
    }

    @GetMapping
    public List<IncidentResponse> findAll(
            @RequestParam(name = "service", required = false) UUID serviceId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity) {
        return service.findAll(serviceId, status, severity);
    }

    @GetMapping("/{id}")
    public IncidentResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/events")
    public ResponseEntity<IncidentEventResponse> addEvent(@PathVariable UUID id,
                                                           @Valid @RequestBody CreateIncidentEventRequest request) {
        IncidentEventResponse created = service.addEvent(id, request);
        return ResponseEntity.created(URI.create("/api/incidents/" + id + "/events/" + created.id())).body(created);
    }

    @GetMapping("/{id}/events")
    public List<IncidentEventResponse> findEvents(@PathVariable UUID id) {
        return service.findEvents(id);
    }

    @GetMapping("/{id}/timeline")
    public TimelineResponse timeline(@PathVariable UUID id) {
        return timelineService.buildTimeline(id);
    }
}
