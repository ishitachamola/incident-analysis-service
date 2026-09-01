package com.incidentplatform.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A deployment announcement, as a CI/CD pipeline would emit when it ships a new service version.
 * Consumed downstream to correlate incidents with recent deployments.
 */
public record DeploymentEvent(
        UUID eventId,
        String service,
        String version,
        Instant deployedAt,
        String status
) {
}
