package com.incidentplatform.ingestion.repository;

import com.incidentplatform.ingestion.entity.LogEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogEntryRepository extends JpaRepository<LogEntry, UUID> {

    boolean existsByEventId(UUID eventId);

    long countByServiceName(String serviceName);
}
