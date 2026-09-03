package com.incidentplatform.ingestion.detection;

import com.incidentplatform.events.IncidentDetectedEvent;
import com.incidentplatform.events.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentDetectionPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public IncidentDetectionPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(IncidentDetectedEvent event) {
        // Keyed by service so detections for one service stay ordered on a single partition.
        kafkaTemplate.send(KafkaTopics.INCIDENT_EVENTS, event.service(), event);
    }
}
