package com.incidentplatform.incident.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.incidentplatform.events.DeploymentEvent;
import com.incidentplatform.events.IncidentDetectedEvent;
import com.incidentplatform.events.KafkaTopics;
import com.incidentplatform.incident.AbstractIntegrationTest;
import com.incidentplatform.incident.entity.IncidentSeverity;
import com.incidentplatform.incident.repository.DeploymentRepository;
import com.incidentplatform.incident.repository.IncidentEventRepository;
import com.incidentplatform.incident.repository.IncidentRepository;
import com.incidentplatform.incident.repository.MonitoredServiceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

class EventConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private IncidentEventRepository incidentEventRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    private static IncidentDetectedEvent detection(UUID eventId, String service) {
        Instant now = Instant.now();
        return new IncidentDetectedEvent(eventId, service, "Elevated error rate on " + service,
                "CRITICAL", now, now.minus(Duration.ofMinutes(10)), now, 30, 40, 0.75,
                "error-rate-threshold");
    }

    @Test
    void detectionEventOpensAnIncidentAndAutoRegistersTheService() {
        String service = "payment-service-" + UUID.randomUUID();

        kafkaTemplate.send(KafkaTopics.INCIDENT_EVENTS, service, detection(UUID.randomUUID(), service));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(serviceRepository.findByName(service)).isPresent();
            var incidents = incidentsFor(service);
            assertThat(incidents).hasSize(1);
            assertThat(incidents.getFirst().getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
            assertThat(incidents.getFirst().getDetectionRule()).isEqualTo("error-rate-threshold");
        });
    }

    @Test
    void detectionEventRecordsTheEvidenceThatOpenedTheIncident() {
        String service = "order-service-" + UUID.randomUUID();

        kafkaTemplate.send(KafkaTopics.INCIDENT_EVENTS, service, detection(UUID.randomUUID(), service));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var incidents = incidentsFor(service);
            assertThat(incidents).hasSize(1);
            var events = incidentEventRepository.findByIncidentIdOrderByOccurredAtAsc(incidents.getFirst().getId());
            assertThat(events).hasSize(1);
            assertThat(events.getFirst().getType()).isEqualTo("DETECTION");
            assertThat(events.getFirst().getDescription()).contains("30 errors out of 40");
        });
    }

    @Test
    void redeliveredDetectionDoesNotOpenASecondIncident() {
        String service = "inventory-service-" + UUID.randomUUID();
        IncidentDetectedEvent event = detection(UUID.randomUUID(), service);

        kafkaTemplate.send(KafkaTopics.INCIDENT_EVENTS, service, event);
        kafkaTemplate.send(KafkaTopics.INCIDENT_EVENTS, service, event);
        kafkaTemplate.send(KafkaTopics.INCIDENT_EVENTS, service, event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(incidentsFor(service)).hasSize(1));
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(incidentsFor(service)).hasSize(1));
    }

    @Test
    void furtherDetectionsExtendTheOpenIncidentInsteadOfOpeningAnother() {
        String service = "user-service-" + UUID.randomUUID();

        // Distinct event ids: the rule firing again for a problem that is still ongoing.
        kafkaTemplate.send(KafkaTopics.INCIDENT_EVENTS, service, detection(UUID.randomUUID(), service));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(incidentsFor(service)).hasSize(1));

        kafkaTemplate.send(KafkaTopics.INCIDENT_EVENTS, service, detection(UUID.randomUUID(), service));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(incidentsFor(service)).hasSize(1);
            var incidentId = incidentsFor(service).getFirst().getId();
            assertThat(incidentEventRepository.findByIncidentIdOrderByOccurredAtAsc(incidentId)).hasSize(2);
        });
    }

    @Test
    void deploymentEventIsRecordedOnceEvenWhenRedelivered() {
        String service = "checkout-service-" + UUID.randomUUID();
        DeploymentEvent event = new DeploymentEvent(
                UUID.randomUUID(), service, "v2.4.1", Instant.now(), "SUCCESS");

        kafkaTemplate.send(KafkaTopics.DEPLOYMENT_EVENTS, service, event);
        kafkaTemplate.send(KafkaTopics.DEPLOYMENT_EVENTS, service, event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var registered = serviceRepository.findByName(service);
            assertThat(registered).isPresent();
            assertThat(deploymentRepository.findByServiceIdOrderByDeployedAtDesc(registered.get().getId()))
                    .hasSize(1)
                    .allSatisfy(deployment -> assertThat(deployment.getVersion()).isEqualTo("v2.4.1"));
        });
    }

    /**
     * Looks incidents up by service id rather than walking the lazy service association, which
     * would need an open persistence context.
     */
    private List<com.incidentplatform.incident.entity.Incident> incidentsFor(String service) {
        return serviceRepository.findByName(service)
                .map(registered -> incidentRepository.findByServiceId(registered.getId()))
                .orElseGet(List::of);
    }
}
