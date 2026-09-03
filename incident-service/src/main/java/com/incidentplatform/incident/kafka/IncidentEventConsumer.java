package com.incidentplatform.incident.kafka;

import com.incidentplatform.events.IncidentDetectedEvent;
import com.incidentplatform.events.KafkaTopics;
import com.incidentplatform.incident.exception.InvalidEventException;
import com.incidentplatform.incident.service.IncidentService;
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

@Component
public class IncidentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventConsumer.class);

    private final IncidentService incidentService;

    public IncidentEventConsumer(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            exclude = InvalidEventException.class,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true")
    @KafkaListener(topics = KafkaTopics.INCIDENT_EVENTS, groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "incidentDetectedListenerContainerFactory")
    public void consume(ConsumerRecord<String, IncidentDetectedEvent> record) {
        IncidentDetectedEvent event = record.value();
        if (event == null) {
            throw new InvalidEventException("Undeserializable detection event at "
                    + record.topic() + "-" + record.partition() + "@" + record.offset());
        }
        incidentService.openFromDetection(event);
    }

    @DltHandler
    public void handleDeadLetter(ConsumerRecord<String, IncidentDetectedEvent> record,
                                  @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String reason) {
        log.error("Detection event sent to dead-letter topic {} (partition {}, offset {}): {}",
                record.topic(), record.partition(), record.offset(), reason);
    }
}
