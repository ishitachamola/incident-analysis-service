package com.incidentplatform.ingestion.service;

import com.incidentplatform.events.LogEvent;
import com.incidentplatform.ingestion.entity.LogEntry;
import com.incidentplatform.ingestion.exception.InvalidLogEventException;
import com.incidentplatform.ingestion.repository.LogEntryRepository;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LogIngestionService.class);
    private static final Set<String> VALID_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_EXCEPTION_LENGTH = 500;

    private final LogEntryRepository repository;

    public LogIngestionService(LogEntryRepository repository) {
        this.repository = repository;
    }

    /**
     * @return {@code true} if the event was stored, {@code false} if it was a duplicate delivery
     */
    @Transactional
    public boolean ingest(LogEvent event) {
        validate(event);

        if (repository.existsByEventId(event.eventId())) {
            log.debug("Skipping duplicate log event {}", event.eventId());
            return false;
        }

        LogEntry entry = new LogEntry(
                event.eventId(),
                event.service().trim(),
                event.level().trim().toUpperCase(Locale.ROOT),
                trimToNull(event.traceId()),
                truncate(event.message().trim(), MAX_MESSAGE_LENGTH),
                truncate(trimToNull(event.exception()), MAX_EXCEPTION_LENGTH),
                event.timestamp());

        try {
            repository.save(entry);
            return true;
        } catch (DataIntegrityViolationException ex) {
            // Two consumers raced on the same event: the unique constraint is the source of truth,
            // so treat losing the race as a successful no-op rather than a failure to retry.
            log.debug("Concurrent duplicate for log event {}, ignoring", event.eventId());
            return false;
        }
    }

    private void validate(LogEvent event) {
        if (event == null) {
            throw new InvalidLogEventException("Log event payload was null or could not be deserialized");
        }
        if (event.eventId() == null) {
            throw new InvalidLogEventException("Log event is missing eventId");
        }
        if (event.timestamp() == null) {
            throw new InvalidLogEventException("Log event " + event.eventId() + " is missing timestamp");
        }
        if (isBlank(event.service())) {
            throw new InvalidLogEventException("Log event " + event.eventId() + " is missing service");
        }
        if (isBlank(event.message())) {
            throw new InvalidLogEventException("Log event " + event.eventId() + " is missing message");
        }
        if (isBlank(event.level()) || !VALID_LEVELS.contains(event.level().trim().toUpperCase(Locale.ROOT))) {
            throw new InvalidLogEventException(
                    "Log event " + event.eventId() + " has unsupported level: " + event.level());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
