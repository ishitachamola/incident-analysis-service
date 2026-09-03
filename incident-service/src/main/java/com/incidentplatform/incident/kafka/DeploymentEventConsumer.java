package com.incidentplatform.incident.kafka;

import com.incidentplatform.events.DeploymentEvent;
import com.incidentplatform.events.KafkaTopics;
import com.incidentplatform.incident.exception.InvalidEventException;
import com.incidentplatform.incident.service.DeploymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Deployments are owned by this service, so it consumes the announcements directly rather than
 * having another service write into its tables.
 */
@Component
public class DeploymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeploymentEventConsumer.class);

    private final DeploymentService deploymentService;

    public DeploymentEventConsumer(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            exclude = InvalidEventException.class,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true")
    @KafkaListener(topics = KafkaTopics.DEPLOYMENT_EVENTS, groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "deploymentListenerContainerFactory")
    public void consume(ConsumerRecord<String, DeploymentEvent> record) {
        DeploymentEvent event = record.value();
        if (event == null) {
            throw new InvalidEventException("Undeserializable deployment event at "
                    + record.topic() + "-" + record.partition() + "@" + record.offset());
        }
        if (event.service() == null || event.service().isBlank()) {
            throw new InvalidEventException("Deployment event " + event.eventId() + " is missing service");
        }
        if (event.version() == null || event.version().isBlank()) {
            throw new InvalidEventException("Deployment event " + event.eventId() + " is missing version");
        }
        boolean stored = deploymentService.recordFromEvent(event);
        if (stored) {
            log.info("Recorded deployment {} {} for {}", event.version(), event.status(), event.service());
        }
    }

    @DltHandler
    public void handleDeadLetter(ConsumerRecord<String, DeploymentEvent> record,
                                  @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String reason) {
        log.error("Deployment event sent to dead-letter topic {} (partition {}, offset {}): {}",
                record.topic(), record.partition(), record.offset(), reason);
    }
}
