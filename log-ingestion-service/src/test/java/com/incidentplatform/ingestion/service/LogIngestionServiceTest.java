package com.incidentplatform.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.incidentplatform.events.LogEvent;
import com.incidentplatform.ingestion.entity.LogEntry;
import com.incidentplatform.ingestion.exception.InvalidLogEventException;
import com.incidentplatform.ingestion.repository.LogEntryRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LogIngestionServiceTest {

    @Mock
    private LogEntryRepository repository;

    @InjectMocks
    private LogIngestionService service;

    private static LogEvent validEvent() {
        return new LogEvent(UUID.randomUUID(), Instant.now(), "payment-service", "ERROR",
                "trace-1", "Database connection timeout", "SQLTransientConnectionException");
    }

    @Test
    void storesValidEvent() {
        LogEvent event = validEvent();
        when(repository.existsByEventId(event.eventId())).thenReturn(false);

        assertThat(service.ingest(event)).isTrue();
        verify(repository).save(any(LogEntry.class));
    }

    @Test
    void skipsDuplicateEventWithoutSaving() {
        LogEvent event = validEvent();
        when(repository.existsByEventId(event.eventId())).thenReturn(true);

        assertThat(service.ingest(event)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void treatsConcurrentDuplicateAsNoOpRatherThanFailure() {
        LogEvent event = validEvent();
        when(repository.existsByEventId(event.eventId())).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThat(service.ingest(event)).isFalse();
    }

    @Test
    void normalizesLevelAndTrimsFields() {
        LogEvent event = new LogEvent(UUID.randomUUID(), Instant.now(), "  payment-service  ", "error",
                "   ", "  boom  ", null);
        when(repository.existsByEventId(event.eventId())).thenReturn(false);

        service.ingest(event);

        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(repository).save(captor.capture());
        LogEntry saved = captor.getValue();
        assertThat(saved.getLevel()).isEqualTo("ERROR");
        assertThat(saved.getServiceName()).isEqualTo("payment-service");
        assertThat(saved.getMessage()).isEqualTo("boom");
        assertThat(saved.getTraceId()).isNull();
    }

    @Test
    void truncatesOverlongMessage() {
        String longMessage = "x".repeat(5000);
        LogEvent event = new LogEvent(UUID.randomUUID(), Instant.now(), "svc", "INFO", null, longMessage, null);
        when(repository.existsByEventId(event.eventId())).thenReturn(false);

        service.ingest(event);

        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).hasSize(4000);
    }

    @Test
    void rejectsNullPayload() {
        assertThatThrownBy(() -> service.ingest(null))
                .isInstanceOf(InvalidLogEventException.class);
    }

    @Test
    void rejectsMissingEventId() {
        LogEvent event = new LogEvent(null, Instant.now(), "svc", "INFO", null, "msg", null);

        assertThatThrownBy(() -> service.ingest(event))
                .isInstanceOf(InvalidLogEventException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsMissingServiceAndMessageAndTimestamp() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.ingest(new LogEvent(id, null, "svc", "INFO", null, "msg", null)))
                .isInstanceOf(InvalidLogEventException.class)
                .hasMessageContaining("timestamp");
        assertThatThrownBy(() -> service.ingest(new LogEvent(id, Instant.now(), " ", "INFO", null, "msg", null)))
                .isInstanceOf(InvalidLogEventException.class)
                .hasMessageContaining("service");
        assertThatThrownBy(() -> service.ingest(new LogEvent(id, Instant.now(), "svc", "INFO", null, " ", null)))
                .isInstanceOf(InvalidLogEventException.class)
                .hasMessageContaining("message");
    }

    @Test
    void rejectsUnsupportedLevel() {
        LogEvent event = new LogEvent(UUID.randomUUID(), Instant.now(), "svc", "CATASTROPHE", null, "msg", null);

        assertThatThrownBy(() -> service.ingest(event))
                .isInstanceOf(InvalidLogEventException.class)
                .hasMessageContaining("level");
    }
}
