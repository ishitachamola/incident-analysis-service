package com.incidentplatform.incident.dto;

import com.incidentplatform.incident.entity.MonitoredService;
import java.time.Instant;
import java.util.UUID;

public record ServiceResponse(UUID id, String name, String description, Instant createdAt) {

    public static ServiceResponse from(MonitoredService service) {
        return new ServiceResponse(service.getId(), service.getName(), service.getDescription(),
                service.getCreatedAt());
    }
}
