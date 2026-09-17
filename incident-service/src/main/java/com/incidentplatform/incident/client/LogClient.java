package com.incidentplatform.incident.client;

import com.incidentplatform.security.ServiceTokenInterceptor;
import com.incidentplatform.security.ServiceTokenProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads logs from the service that owns them.
 *
 * <p>Failures are deliberately non-fatal: a timeline is still useful when it shows deployments and
 * incident events, so an unreachable log service degrades the timeline rather than failing the
 * request. Callers can tell the difference via the empty {@link Optional}.
 */
@Component
public class LogClient {

    private static final Logger log = LoggerFactory.getLogger(LogClient.class);

    private final RestClient restClient;

    public LogClient(RestClient.Builder builder, ServiceTokenProvider serviceTokenProvider,
                     @Value("${platform.log-service.base-url}") String baseUrl) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestInterceptor(new ServiceTokenInterceptor(serviceTokenProvider))
                .build();
    }

    /**
     * @return the logs in the window, or empty if the log service could not be reached
     */
    public Optional<List<LogEntryDto>> findLogs(String service, Instant from, Instant to, int limit) {
        try {
            List<LogEntryDto> logs = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/logs")
                            .queryParam("service", service)
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<LogEntryDto>>() {
                    });
            return Optional.ofNullable(logs).or(() -> Optional.of(List.of()));
        } catch (Exception ex) {
            log.warn("Could not fetch logs for {} from the log service: {}", service, ex.getMessage());
            return Optional.empty();
        }
    }
}
