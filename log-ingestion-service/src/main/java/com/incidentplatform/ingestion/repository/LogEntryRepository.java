package com.incidentplatform.ingestion.repository;

import com.incidentplatform.ingestion.entity.LogEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LogEntryRepository extends JpaRepository<LogEntry, UUID> {

    boolean existsByEventId(UUID eventId);

    long countByServiceName(String serviceName);

    /**
     * Per-service error and total counts over an evaluation window. Aggregating in the database
     * keeps the detector's cost independent of ingest volume.
     */
    @Query("""
            SELECT l.serviceName AS serviceName,
                   SUM(CASE WHEN l.level = 'ERROR' OR l.level = 'FATAL' THEN 1L ELSE 0L END) AS errorCount,
                   COUNT(l) AS totalCount
            FROM LogEntry l
            WHERE l.occurredAt >= :from AND l.occurredAt <= :to
            GROUP BY l.serviceName
            """)
    List<ServiceErrorStats> findErrorStatsBetween(@Param("from") Instant from, @Param("to") Instant to);

    List<LogEntry> findByServiceNameAndOccurredAtBetweenOrderByOccurredAtAsc(
            String serviceName, Instant from, Instant to, Pageable pageable);

    interface ServiceErrorStats {
        String getServiceName();

        long getErrorCount();

        long getTotalCount();
    }
}
