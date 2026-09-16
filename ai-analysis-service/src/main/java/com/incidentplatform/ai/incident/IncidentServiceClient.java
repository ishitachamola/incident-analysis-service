package com.incidentplatform.ai.incident;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads incidents and their timelines from the incident service, which owns that data.
 *
 * <p>The timeline is used rather than raw logs because the incident service has already merged
 * deployments, events and logs chronologically and collapsed repeated log lines. That makes it far
 * smaller than the underlying logs, which directly lowers the token cost of every analysis.
 */
@Component
public class IncidentServiceClient {

    private final RestClient restClient;

    public IncidentServiceClient(RestClient.Builder builder,
                                  @Value("${platform.incident-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public IncidentSummary getIncident(UUID incidentId) {
        return get("/api/incidents/{id}", incidentId, IncidentSummary.class);
    }

    public IncidentTimeline getTimeline(UUID incidentId) {
        return get("/api/incidents/{id}/timeline", incidentId, IncidentTimeline.class);
    }

    private <T> T get(String path, UUID incidentId, Class<T> type) {
        try {
            T body = restClient.get().uri(path, incidentId).retrieve().body(type);
            if (body == null) {
                throw new UpstreamServiceException("Incident service returned an empty response", null);
            }
            return body;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new IncidentNotFoundException(incidentId);
            }
            throw new UpstreamServiceException("Incident service rejected the request: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            throw new UpstreamServiceException("Incident service is unavailable", ex);
        }
    }
}
