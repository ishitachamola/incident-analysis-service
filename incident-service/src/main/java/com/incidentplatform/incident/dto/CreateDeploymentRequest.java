package com.incidentplatform.incident.dto;

import com.incidentplatform.incident.entity.DeploymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateDeploymentRequest(
        @NotNull UUID serviceId,
        @NotBlank String version,
        Instant deployedAt,
        DeploymentStatus status
) {
}
