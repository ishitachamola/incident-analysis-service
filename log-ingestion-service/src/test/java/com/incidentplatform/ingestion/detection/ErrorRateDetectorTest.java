package com.incidentplatform.ingestion.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.incidentplatform.events.IncidentDetectedEvent;
import com.incidentplatform.ingestion.repository.LogEntryRepository;
import com.incidentplatform.ingestion.repository.LogEntryRepository.ServiceErrorStats;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ErrorRateDetectorTest {

    @Mock
    private LogEntryRepository repository;

    @Mock
    private IncidentDetectionPublisher publisher;

    private ErrorRateDetector detector;

    @BeforeEach
    void setUp() {
        DetectionProperties properties = new DetectionProperties(
                true, Duration.ofMinutes(10), 10, 0.10, Duration.ofMinutes(30));
        detector = new ErrorRateDetector(repository, properties, publisher);
    }

    private static ServiceErrorStats stats(String service, long errors, long total) {
        return new ServiceErrorStats() {
            @Override
            public String getServiceName() {
                return service;
            }

            @Override
            public long getErrorCount() {
                return errors;
            }

            @Override
            public long getTotalCount() {
                return total;
            }
        };
    }

    private void given(ServiceErrorStats... stats) {
        when(repository.findErrorStatsBetween(any(Instant.class), any(Instant.class))).thenReturn(List.of(stats));
    }

    @Test
    void firesWhenBothErrorCountAndRateBreachThresholds() {
        given(stats("payment-service", 30, 40));

        List<IncidentDetectedEvent> detections = detector.evaluate();

        assertThat(detections).hasSize(1);
        IncidentDetectedEvent event = detections.getFirst();
        assertThat(event.service()).isEqualTo("payment-service");
        assertThat(event.errorCount()).isEqualTo(30);
        assertThat(event.totalCount()).isEqualTo(40);
        assertThat(event.errorRate()).isEqualTo(0.75);
        assertThat(event.severity()).isEqualTo("CRITICAL");
        assertThat(event.detectionRule()).isEqualTo(ErrorRateDetector.RULE_NAME);
        verify(publisher).publish(event);
    }

    @Test
    void staysQuietWhenErrorRateIsHighButVolumeIsTiny() {
        // 100% error rate, but only three errors: too little evidence to open an incident.
        given(stats("order-service", 3, 3));

        assertThat(detector.evaluate()).isEmpty();
        verify(publisher, never()).publish(any());
    }

    @Test
    void staysQuietWhenVolumeIsHighButErrorRateIsLow() {
        given(stats("order-service", 12, 1000));

        assertThat(detector.evaluate()).isEmpty();
        verify(publisher, never()).publish(any());
    }

    @Test
    void doesNotRefireForTheSameServiceWithinCooldown() {
        given(stats("payment-service", 30, 40));

        assertThat(detector.evaluate()).hasSize(1);
        assertThat(detector.evaluate()).isEmpty();
        assertThat(detector.evaluate()).isEmpty();

        verify(publisher).publish(any());
    }

    @Test
    void gradesSeverityByErrorRate() {
        given(stats("a", 12, 100));   // 12%  -> MEDIUM
        assertThat(detector.evaluate().getFirst().severity()).isEqualTo("MEDIUM");

        given(stats("b", 20, 100));   // 20%  -> HIGH
        assertThat(detector.evaluate().getFirst().severity()).isEqualTo("HIGH");

        given(stats("c", 50, 100));   // 50%  -> CRITICAL
        assertThat(detector.evaluate().getFirst().severity()).isEqualTo("CRITICAL");
    }

    @Test
    void evaluatesEachServiceIndependently() {
        given(stats("healthy-service", 1, 500), stats("failing-service", 40, 60));

        List<IncidentDetectedEvent> detections = detector.evaluate();

        assertThat(detections).hasSize(1);
        assertThat(detections.getFirst().service()).isEqualTo("failing-service");
    }

    @Test
    void doesNothingWhenDisabled() {
        DetectionProperties disabled = new DetectionProperties(
                false, Duration.ofMinutes(10), 10, 0.10, Duration.ofMinutes(30));
        ErrorRateDetector offDetector = new ErrorRateDetector(repository, disabled, publisher);

        assertThat(offDetector.evaluate()).isEmpty();
        verify(publisher, never()).publish(any());
    }
}
