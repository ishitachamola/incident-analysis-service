package com.incidentplatform.simulator;

import com.incidentplatform.events.KafkaTopics;
import com.incidentplatform.events.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes a scenario's events to Kafka. Messages are keyed by service name so all events for one
 * service land on the same partition, which keeps their relative ordering intact for timeline
 * reconstruction downstream.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ScenarioLibrary scenarioLibrary;

    public SimulationService(KafkaTemplate<String, Object> kafkaTemplate, ScenarioLibrary scenarioLibrary) {
        this.kafkaTemplate = kafkaTemplate;
        this.scenarioLibrary = scenarioLibrary;
    }

    public SimulationResult run(ScenarioType type) {
        ScenarioScript script = scenarioLibrary.build(type);

        boolean deploymentPublished = false;
        if (script.deployment() != null) {
            kafkaTemplate.send(KafkaTopics.DEPLOYMENT_EVENTS, script.service(), script.deployment());
            deploymentPublished = true;
        }

        for (LogEvent logEvent : script.logs()) {
            kafkaTemplate.send(KafkaTopics.LOGS, logEvent.service(), logEvent);
        }
        kafkaTemplate.flush();

        log.info("Published scenario {} for service {}: {} log events, deployment={}",
                type, script.service(), script.logs().size(), deploymentPublished);

        return new SimulationResult(type, script.service(), script.logs().size(), deploymentPublished);
    }
}
