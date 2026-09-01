package com.incidentplatform.ingestion.kafka;

import com.incidentplatform.events.KafkaTopics;
import com.incidentplatform.events.LogEvent;
import com.incidentplatform.ingestion.exception.InvalidLogEventException;
import com.incidentplatform.ingestion.service.LogIngestionService;
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
public class LogEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogEventConsumer.class);

    private final LogIngestionService ingestionService;

    public LogEventConsumer(LogIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Transient failures (a database blip, for instance) are retried with exponential backoff on
     * dedicated retry topics, so a slow failure never blocks the main partition. Structurally
     * invalid events are excluded from retries and routed straight to the dead-letter topic.
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            exclude = InvalidLogEventException.class,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true")
    @KafkaListener(topics = KafkaTopics.LOGS, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ConsumerRecord<String, LogEvent> record) {
        LogEvent event = record.value();
        if (event == null) {
            // ErrorHandlingDeserializer yields a null payload when the bytes could not be parsed.
            throw new InvalidLogEventException(
                    "Undeserializable record at " + record.topic() + "-" + record.partition() + "@" + record.offset());
        }
        boolean stored = ingestionService.ingest(event);
        if (log.isDebugEnabled()) {
            log.debug("Log event {} {}", event.eventId(), stored ? "ingested" : "skipped as duplicate");
        }
    }

    @DltHandler
    public void handleDeadLetter(ConsumerRecord<String, LogEvent> record,
                                  @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String reason) {
        log.error("Log event sent to dead-letter topic {} (partition {}, offset {}): {}",
                record.topic(), record.partition(), record.offset(), reason);
    }
}
