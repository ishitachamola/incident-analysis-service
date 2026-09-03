package com.incidentplatform.ingestion.detection;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the error-rate detection rule. Externalised because thresholds are exactly the kind
 * of value an operator needs to adjust per environment without a rebuild.
 */
@ConfigurationProperties(prefix = "incident-detection")
public record DetectionProperties(
        boolean enabled,
        Duration window,
        long minErrorCount,
        double errorRateThreshold,
        Duration cooldown
) {

    public DetectionProperties {
        window = window != null ? window : Duration.ofMinutes(10);
        cooldown = cooldown != null ? cooldown : Duration.ofMinutes(30);
        minErrorCount = minErrorCount > 0 ? minErrorCount : 10;
        errorRateThreshold = errorRateThreshold > 0 ? errorRateThreshold : 0.10;
    }
}
