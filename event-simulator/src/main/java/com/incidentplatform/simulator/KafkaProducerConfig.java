package com.incidentplatform.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Builds the producer around Spring Boot's managed {@link ObjectMapper} rather than letting Kafka
 * instantiate a serializer reflectively with its own. The managed mapper writes {@code Instant} as
 * ISO-8601 instead of an epoch decimal, which keeps the published event contract readable to humans
 * inspecting a topic and to consumers written in other languages.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties,
                                                            ObjectMapper objectMapper) {
        Map<String, Object> config = kafkaProperties.buildProducerProperties(null);
        return new DefaultKafkaProducerFactory<>(
                config, new StringSerializer(), new JsonSerializer<>(objectMapper));
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
