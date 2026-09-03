package com.incidentplatform.incident.repository;

import com.incidentplatform.incident.entity.Incident;
import com.incidentplatform.incident.entity.IncidentStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    boolean existsByEventId(UUID eventId);

    java.util.List<Incident> findByServiceId(UUID serviceId);

    Optional<Incident> findFirstByServiceIdAndStatusInOrderByDetectedAtDesc(
            UUID serviceId, Collection<IncidentStatus> statuses);
}
