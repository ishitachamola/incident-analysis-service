package com.incidentplatform.incident.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.incidentplatform.incident.AbstractIntegrationTest;
import com.incidentplatform.incident.client.LogClient;
import com.incidentplatform.incident.client.LogEntryDto;
import com.incidentplatform.incident.dto.CreateDeploymentRequest;
import com.incidentplatform.incident.dto.CreateIncidentEventRequest;
import com.incidentplatform.incident.dto.CreateIncidentRequest;
import com.incidentplatform.incident.dto.CreateServiceRequest;
import com.incidentplatform.incident.entity.DeploymentStatus;
import com.incidentplatform.incident.entity.IncidentSeverity;
import com.incidentplatform.incident.service.DeploymentService;
import com.incidentplatform.incident.service.IncidentService;
import com.incidentplatform.incident.service.MonitoredServiceService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Exercises timeline assembly against a real database, with the log service stubbed so the merging
 * and collapsing behaviour can be asserted precisely.
 */
class TimelineServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TimelineService timelineService;

    @Autowired
    private MonitoredServiceService monitoredServiceService;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private DeploymentService deploymentService;

    @MockitoBean
    private LogClient logClient;

    private UUID incidentId;
    private String serviceName;
    private Instant detectedAt;

    @BeforeEach
    void setUp() {
        serviceName = "payment-service-" + UUID.randomUUID();
        detectedAt = Instant.now();

        UUID serviceId = monitoredServiceService.create(new CreateServiceRequest(serviceName, null)).id();

        deploymentService.create(new CreateDeploymentRequest(
                serviceId, "v2.4.1", detectedAt.minus(Duration.ofMinutes(13)), DeploymentStatus.SUCCESS));

        incidentId = incidentService.create(new CreateIncidentRequest(
                serviceId, "Elevated error rate", IncidentSeverity.CRITICAL, null, detectedAt)).id();

        incidentService.addEvent(incidentId, new CreateIncidentEventRequest(
                "DETECTION", "Opened by detection rule", detectedAt, "RULE#error-rate-threshold"));
    }

    private LogEntryDto logAt(int minutesBeforeDetection, String level, String message) {
        return new LogEntryDto(UUID.randomUUID(), serviceName, level, "trace-1", message, null,
                detectedAt.minus(Duration.ofMinutes(minutesBeforeDetection)));
    }

    @Test
    void mergesDeploymentsEventsAndLogsIntoOneChronologicalTimeline() {
        when(logClient.findLogs(anyString(), any(), any(), anyInt())).thenReturn(Optional.of(List.of(
                logAt(12, "WARN", "Connection is not available"),
                logAt(8, "ERROR", "Database connection timeout"),
                logAt(20, "INFO", "Payment processed"))));

        TimelineResponse timeline = timelineService.buildTimeline(incidentId);

        assertThat(timeline.logsAvailable()).isTrue();
        assertThat(timeline.service()).isEqualTo(serviceName);
        assertThat(timeline.entries()).isSortedAccordingTo(
                (a, b) -> a.timestamp().compareTo(b.timestamp()));
        assertThat(timeline.entries()).extracting(TimelineEntry::type)
                .containsExactly("DEPLOYMENT", "LOG", "LOG", "INCIDENT_EVENT");
        assertThat(timeline.entries().getFirst().summary()).contains("v2.4.1");
    }

    @Test
    void collapsesRepeatedLogLinesIntoASingleCountedEntry() {
        when(logClient.findLogs(anyString(), any(), any(), anyInt())).thenReturn(Optional.of(List.of(
                logAt(9, "ERROR", "Database connection timeout"),
                logAt(8, "ERROR", "Database connection timeout"),
                logAt(7, "ERROR", "Database connection timeout"))));

        TimelineResponse timeline = timelineService.buildTimeline(incidentId);

        List<TimelineEntry> logEntries = timeline.entries().stream()
                .filter(entry -> entry.type().equals("LOG"))
                .toList();
        assertThat(logEntries).hasSize(1);
        assertThat(logEntries.getFirst().occurrences()).isEqualTo(3);
        assertThat(logEntries.getFirst().summary()).contains("(x3)");
        // Anchored at the first occurrence, not the last.
        assertThat(logEntries.getFirst().timestamp())
                .isEqualTo(detectedAt.minus(Duration.ofMinutes(9)));
    }

    @Test
    void excludesHealthyBaselineTrafficFromTheTimeline() {
        when(logClient.findLogs(anyString(), any(), any(), anyInt())).thenReturn(Optional.of(List.of(
                logAt(15, "INFO", "Payment processed"),
                logAt(14, "INFO", "Payment processed"),
                logAt(9, "ERROR", "Database connection timeout"))));

        TimelineResponse timeline = timelineService.buildTimeline(incidentId);

        assertThat(timeline.entries()).noneMatch(entry -> "INFO".equals(entry.level()));
        assertThat(timeline.entries()).anyMatch(entry -> "ERROR".equals(entry.level()));
    }

    @Test
    void everyTimelineEntryCarriesATraceableSourceReference() {
        when(logClient.findLogs(anyString(), any(), any(), anyInt())).thenReturn(Optional.of(List.of(
                logAt(9, "ERROR", "Database connection timeout"))));

        TimelineResponse timeline = timelineService.buildTimeline(incidentId);

        assertThat(timeline.entries()).isNotEmpty();
        assertThat(timeline.entries()).allSatisfy(entry ->
                assertThat(entry.sourceRef()).isNotBlank());
        assertThat(timeline.entries()).anyMatch(entry -> entry.sourceRef().startsWith("LOG#"));
        assertThat(timeline.entries()).anyMatch(entry -> entry.sourceRef().startsWith("DEPLOY#"));
    }

    @Test
    void degradesGracefullyWhenTheLogServiceIsUnreachable() {
        when(logClient.findLogs(anyString(), any(), any(), anyInt())).thenReturn(Optional.empty());

        TimelineResponse timeline = timelineService.buildTimeline(incidentId);

        // The timeline is still served, flagged so a caller knows log evidence is missing rather
        // than genuinely absent.
        assertThat(timeline.logsAvailable()).isFalse();
        assertThat(timeline.entries()).isNotEmpty();
        assertThat(timeline.entries()).noneMatch(entry -> entry.type().equals("LOG"));
    }
}
