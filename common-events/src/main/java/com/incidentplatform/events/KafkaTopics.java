package com.incidentplatform.events;

/**
 * Topic names shared by producers and consumers. Kept in one place so a rename cannot silently
 * desynchronise a producer from its consumer.
 */
public final class KafkaTopics {

    public static final String LOGS = "logs-topic";
    public static final String DEPLOYMENT_EVENTS = "deployment-events";

    private KafkaTopics() {
    }
}
