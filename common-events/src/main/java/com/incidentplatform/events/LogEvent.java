package com.incidentplatform.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A single application log line emitted by a (simulated) microservice.
 *
 * <p>{@code eventId} is assigned by the producer and is the idempotency key: consumers use it to
 * make redelivery a no-op, which matters because Kafka guarantees at-least-once delivery.
 */
public record LogEvent(
        UUID eventId,
        Instant timestamp,
        String service,
        String level,
        String traceId,
        String message,
        String exception
) {
}
