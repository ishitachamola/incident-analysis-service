package com.incidentplatform.incident.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentplatform.events.DeploymentEvent;
import com.incidentplatform.events.IncidentDetectedEvent;
import java.util.Map;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * This service consumes two topics carrying different payload types, so each listener gets its own
 * container factory with a deserializer bound to the type it expects. Every deserializer is wrapped
 * in an {@link ErrorHandlingDeserializer} so an unparseable message surfaces as a null payload the
 * listener can route to the dead-letter topic, instead of a poison pill that stalls the partition.
 */
@Configuration
public class KafkaConfig {

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

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DeploymentEvent> deploymentListenerContainerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        return listenerFactory(kafkaProperties, objectMapper, DeploymentEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, IncidentDetectedEvent>
            incidentDetectedListenerContainerFactory(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        return listenerFactory(kafkaProperties, objectMapper, IncidentDetectedEvent.class);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper, Class<T> payloadType) {

        JsonDeserializer<T> delegate = new JsonDeserializer<>(payloadType, objectMapper, false);
        delegate.addTrustedPackages("com.incidentplatform.events");

        Map<String, Object> config = kafkaProperties.buildConsumerProperties(null);
        ConsumerFactory<String, T> consumerFactory = new DefaultKafkaConsumerFactory<>(
                config,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(delegate));

        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
