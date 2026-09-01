package com.incidentplatform.ingestion.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.incidentplatform.events.KafkaTopics;
import com.incidentplatform.events.LogEvent;
import com.incidentplatform.ingestion.AbstractIngestionIntegrationTest;
import com.incidentplatform.ingestion.repository.LogEntryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

class LogEventConsumerIntegrationTest extends AbstractIngestionIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private LogEntryRepository repository;

    @BeforeEach
    void clearLogs() {
        repository.deleteAll();
    }

    private static LogEvent event(UUID eventId, String service) {
        return new LogEvent(eventId, Instant.now(), service, "ERROR", "trace-abc",
                "Database connection timeout", "SQLTransientConnectionException");
    }

    @Test
    void validEventIsPersisted() {
        String service = "payment-service-" + UUID.randomUUID();
        kafkaTemplate.send(KafkaTopics.LOGS, service, event(UUID.randomUUID(), service));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(repository.countByServiceName(service)).isEqualTo(1));
    }

    @Test
    void redeliveredEventIsIngestedOnlyOnce() {
        String service = "order-service-" + UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        LogEvent duplicate = event(eventId, service);

        kafkaTemplate.send(KafkaTopics.LOGS, service, duplicate);
        kafkaTemplate.send(KafkaTopics.LOGS, service, duplicate);
        kafkaTemplate.send(KafkaTopics.LOGS, service, duplicate);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(repository.countByServiceName(service)).isEqualTo(1));

        // Hold the assertion briefly so a late duplicate would still be caught.
        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(repository.countByServiceName(service)).isEqualTo(1));
    }

    @Test
    void structurallyInvalidEventIsNotPersistedAndReachesDeadLetterTopic() {
        String service = "inventory-service-" + UUID.randomUUID();
        // Unsupported level: valid JSON, so it deserializes, but fails validation permanently.
        LogEvent invalid = new LogEvent(UUID.randomUUID(), Instant.now(), service, "CATASTROPHE",
                "trace-1", "some message", null);

        kafkaTemplate.send(KafkaTopics.LOGS, service, invalid);

        awaitDeadLetterPayloadContaining(service);
        assertThat(repository.countByServiceName(service)).isZero();
    }

    @Test
    void unparseableMessageIsNotPersistedAndDoesNotBlockTheConsumer() {
        String poisonKey = "poison-" + UUID.randomUUID();
        try (KafkaProducer<String, String> rawProducer = rawProducer()) {
            rawProducer.send(new ProducerRecord<>(KafkaTopics.LOGS, poisonKey, "{ this is not valid json"));
            rawProducer.flush();
        }

        // The partition must keep flowing: a healthy event sent afterwards still gets ingested.
        String service = "user-service-" + UUID.randomUUID();
        kafkaTemplate.send(KafkaTopics.LOGS, service, event(UUID.randomUUID(), service));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(repository.countByServiceName(service)).isEqualTo(1));
    }

    private KafkaProducer<String, String> rawProducer() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(config);
    }

    /**
     * Polls the dead-letter topic until a payload matching this test's own event shows up. The
     * topic is shared across test methods, so asserting on "any record present" would wrongly pass
     * on a sibling test's leftovers.
     */
    private void awaitDeadLetterPayloadContaining(String needle) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-assert-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<String> seen = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(KafkaTopics.LOGS + "-dlt"));
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(record -> seen.add(String.valueOf(record.value())));
                assertThat(seen).anyMatch(payload -> payload.contains(needle));
            });
        }
    }
}
