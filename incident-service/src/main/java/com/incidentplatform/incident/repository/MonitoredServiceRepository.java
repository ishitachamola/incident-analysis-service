package com.incidentplatform.incident.repository;

import com.incidentplatform.incident.entity.MonitoredService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, UUID> {

    Optional<MonitoredService> findByName(String name);

    boolean existsByName(String name);
}
