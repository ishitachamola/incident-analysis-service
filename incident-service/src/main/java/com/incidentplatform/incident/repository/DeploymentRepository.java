package com.incidentplatform.incident.repository;

import com.incidentplatform.incident.entity.Deployment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    List<Deployment> findByServiceIdOrderByDeployedAtDesc(UUID serviceId);

    boolean existsByEventId(UUID eventId);

    List<Deployment> findByServiceIdAndDeployedAtBetweenOrderByDeployedAtAsc(
            UUID serviceId, java.time.Instant from, java.time.Instant to);
}
