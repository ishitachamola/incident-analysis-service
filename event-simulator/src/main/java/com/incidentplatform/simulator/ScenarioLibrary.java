package com.incidentplatform.simulator;

import com.incidentplatform.events.DeploymentEvent;
import com.incidentplatform.events.LogEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Builds the event streams for each failure scenario.
 *
 * <p>Every scenario follows the same shape: a stretch of healthy baseline traffic, then a
 * deterioration whose log signature points at one specific root cause. The timeline is compressed
 * into the ~20 minutes preceding "now" so a freshly started demo has recent, queryable evidence.
 */
@Component
public class ScenarioLibrary {

    private static final Duration WINDOW = Duration.ofMinutes(20);

    public ScenarioScript build(ScenarioType type) {
        Instant start = Instant.now().minus(WINDOW);
        return switch (type) {
            case DB_POOL_EXHAUSTION -> dbPoolExhaustion(start);
            case KAFKA_CONSUMER_LAG -> kafkaConsumerLag(start);
            case DOWNSTREAM_TIMEOUT -> downstreamTimeout(start);
            case DEPLOYMENT_REGRESSION -> deploymentRegression(start);
            case SLOW_QUERY -> slowQuery(start);
        };
    }

    private ScenarioScript dbPoolExhaustion(Instant start) {
        String service = "payment-service";
        Script script = new Script(service, start);

        script.baseline(12, "Payment request processed successfully");
        script.at(5, "INFO", "Deployment v2.4.1 completed, application context refreshed", null);
        script.repeat(4, 7, "INFO", "Payment request processed successfully", null);
        script.repeat(6, 8, "WARN",
                "HikariPool-1 - Connection is not available, request timed out after 5000ms", null);
        script.repeat(10, 10, "WARN", "HikariPool-1 - Pool stats (total=20, active=20, idle=0, waiting=14)", null);
        script.repeat(18, 12, "ERROR", "Database connection timeout while acquiring connection from pool",
                "SQLTransientConnectionException");
        script.repeat(14, 15, "ERROR", "Unhandled exception processing payment request, returning HTTP 500",
                "SQLTransientConnectionException");

        DeploymentEvent deployment = new DeploymentEvent(
                UUID.randomUUID(), service, "v2.4.1", start.plus(Duration.ofMinutes(5)), "SUCCESS");
        return new ScenarioScript(ScenarioType.DB_POOL_EXHAUSTION, service, deployment, script.logs());
    }

    private ScenarioScript kafkaConsumerLag(Instant start) {
        String service = "order-service";
        Script script = new Script(service, start);

        script.baseline(12, "Order event consumed and persisted");
        script.repeat(5, 6, "WARN", "Consumer group order-processing-group lag is 1200 messages and rising", null);
        script.repeat(8, 9, "WARN", "Batch processing took 8400ms, exceeding max.poll.interval budget", null);
        script.repeat(10, 12, "ERROR", "Consumer group rebalanced, partitions revoked mid-batch",
                "CommitFailedException");
        script.repeat(12, 15, "ERROR", "Failed to commit offsets, records will be reprocessed",
                "CommitFailedException");
        script.repeat(6, 17, "WARN", "Consumer group order-processing-group lag is 18400 messages", null);

        return new ScenarioScript(ScenarioType.KAFKA_CONSUMER_LAG, service, null, script.logs());
    }

    private ScenarioScript downstreamTimeout(Instant start) {
        String service = "payment-service";
        Script script = new Script(service, start);

        script.baseline(12, "Payment authorised via upstream gateway");
        script.repeat(6, 7, "WARN", "Call to payment-gateway exceeded 2000ms, retrying (attempt 1 of 3)", null);
        script.repeat(10, 9, "WARN", "Call to payment-gateway exceeded 2000ms, retrying (attempt 2 of 3)", null);
        script.repeat(16, 11, "ERROR", "Read timed out calling https://payment-gateway.internal/v1/authorise",
                "SocketTimeoutException");
        script.repeat(8, 14, "ERROR", "Circuit breaker 'payment-gateway' transitioned from CLOSED to OPEN",
                "CallNotPermittedException");
        script.repeat(12, 16, "ERROR", "Payment rejected, downstream dependency unavailable",
                "CallNotPermittedException");

        return new ScenarioScript(ScenarioType.DOWNSTREAM_TIMEOUT, service, null, script.logs());
    }

    private ScenarioScript deploymentRegression(Instant start) {
        String service = "user-service";
        Script script = new Script(service, start);

        script.baseline(12, "User profile request served");
        script.at(6, "INFO", "Deployment v3.1.0 completed, application context refreshed", null);
        script.repeat(20, 8, "ERROR", "Cannot invoke \"String.trim()\" because \"preferredName\" is null",
                "NullPointerException");
        script.repeat(15, 12, "ERROR", "Request failed with HTTP 500 after deployment v3.1.0",
                "NullPointerException");
        script.repeat(6, 16, "WARN", "Error rate for user-service is 22% over the last 5 minutes", null);

        DeploymentEvent deployment = new DeploymentEvent(
                UUID.randomUUID(), service, "v3.1.0", start.plus(Duration.ofMinutes(6)), "SUCCESS");
        return new ScenarioScript(ScenarioType.DEPLOYMENT_REGRESSION, service, deployment, script.logs());
    }

    private ScenarioScript slowQuery(Instant start) {
        String service = "inventory-service";
        Script script = new Script(service, start);

        script.baseline(12, "Inventory lookup completed in 42ms");
        script.repeat(8, 7, "WARN",
                "Slow query detected (3200ms): SELECT * FROM stock_levels WHERE warehouse_id = ? AND sku LIKE ?", null);
        script.repeat(12, 10, "WARN",
                "Slow query detected (7400ms): SELECT * FROM stock_levels WHERE warehouse_id = ? AND sku LIKE ?", null);
        script.repeat(6, 13, "WARN", "Database CPU utilisation at 94%", null);
        script.repeat(10, 15, "ERROR", "Statement cancelled due to query timeout after 30000ms",
                "QueryTimeoutException");
        script.repeat(8, 17, "ERROR", "Inventory lookup failed, returning HTTP 500", "QueryTimeoutException");

        return new ScenarioScript(ScenarioType.SLOW_QUERY, service, null, script.logs());
    }

    /** Accumulates log events for one scenario, spreading them across the scenario's time window. */
    private static final class Script {

        private final String service;
        private final Instant start;
        private final List<LogEvent> logs = new ArrayList<>();

        private Script(String service, Instant start) {
            this.service = service;
            this.start = start;
        }

        /** Healthy traffic spread across the first five minutes, before anything goes wrong. */
        void baseline(int count, String message) {
            for (int i = 0; i < count; i++) {
                long offsetSeconds = (long) (i * (300.0 / count));
                add(start.plusSeconds(offsetSeconds), "INFO", message, null);
            }
        }

        void at(int minute, String level, String message, String exception) {
            add(start.plus(Duration.ofMinutes(minute)), level, message, exception);
        }

        /** Emits {@code count} events clustered around the given minute, jittered to look organic. */
        void repeat(int count, int minute, String level, String message, String exception) {
            Instant anchor = start.plus(Duration.ofMinutes(minute));
            for (int i = 0; i < count; i++) {
                long jitter = ThreadLocalRandom.current().nextLong(0, 60);
                add(anchor.plusSeconds(jitter), level, message, exception);
            }
        }

        private void add(Instant timestamp, String level, String message, String exception) {
            logs.add(new LogEvent(
                    UUID.randomUUID(), timestamp, service, level, newTraceId(), message, exception));
        }

        private String newTraceId() {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        List<LogEvent> logs() {
            return logs.stream()
                    .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                    .toList();
        }
    }
}
