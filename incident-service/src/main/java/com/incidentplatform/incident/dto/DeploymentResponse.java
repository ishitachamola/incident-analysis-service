package com.incidentplatform.incident.dto;

import com.incidentplatform.incident.entity.Deployment;
import com.incidentplatform.incident.entity.DeploymentStatus;
import java.time.Instant;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        UUID serviceId,
        String serviceName,
        String version,
        Instant deployedAt,
        DeploymentStatus status
) {

    public static DeploymentResponse from(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getService().getId(),
                deployment.getService().getName(),
                deployment.getVersion(),
                deployment.getDeployedAt(),
                deployment.getStatus());
    }
}
