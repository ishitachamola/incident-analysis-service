package com.incidentplatform.incident.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentplatform.incident.AbstractIntegrationTest;
import com.incidentplatform.incident.dto.CreateIncidentEventRequest;
import com.incidentplatform.incident.dto.CreateIncidentRequest;
import com.incidentplatform.incident.dto.CreateServiceRequest;
import com.incidentplatform.incident.entity.IncidentSeverity;
import com.incidentplatform.incident.entity.IncidentStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class IncidentControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private UUID serviceId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        String response = mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateServiceRequest("payment-service-" + UUID.randomUUID(), null))))
                .andReturn().getResponse().getContentAsString();
        serviceId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    @Test
    void createsIncidentWithDefaultsAndFetchesIt() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                serviceId, "High error rate", IncidentSeverity.CRITICAL, null, null);

        String response = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DETECTED"))
                .andExpect(jsonPath("$.detectedAt").exists())
                .andReturn().getResponse().getContentAsString();

        String incidentId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/api/incidents/" + incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("High error rate"))
                .andExpect(jsonPath("$.serviceId").value(serviceId.toString()));
    }

    @Test
    void filtersIncidentsByServiceAndSeverity() throws Exception {
        mockMvc.perform(post("/api/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateIncidentRequest(serviceId, "Critical one", IncidentSeverity.CRITICAL, null, null))));
        mockMvc.perform(post("/api/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateIncidentRequest(serviceId, "Low one", IncidentSeverity.LOW, null, null))));

        mockMvc.perform(get("/api/incidents")
                        .param("service", serviceId.toString())
                        .param("severity", "CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Critical one"));
    }

    @Test
    void addsAndListsIncidentEvents() throws Exception {
        String response = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateIncidentRequest(serviceId, "DB errors", IncidentSeverity.HIGH, null, null))))
                .andReturn().getResponse().getContentAsString();
        String incidentId = objectMapper.readTree(response).get("id").asText();

        CreateIncidentEventRequest event = new CreateIncidentEventRequest(
                "LOG_ANOMALY", "Connection timeout spike detected", null, "LOG#1832");

        mockMvc.perform(post("/api/incidents/" + incidentId + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LOG_ANOMALY"));

        mockMvc.perform(get("/api/incidents/" + incidentId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceRef").value("LOG#1832"));
    }

    @Test
    void rejectsIncidentForUnknownService() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                UUID.randomUUID(), "Ghost incident", IncidentSeverity.LOW, null, null);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns400ForInvalidEnumValueInBody() throws Exception {
        String body = """
                {"serviceId":"%s","title":"bad severity","severity":"NOT_A_SEVERITY"}
                """.formatted(serviceId);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400ForInvalidEnumValueInQueryParam() throws Exception {
        mockMvc.perform(get("/api/incidents").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404WhenAddingEventToUnknownIncident() throws Exception {
        CreateIncidentEventRequest event = new CreateIncidentEventRequest("MANUAL_NOTE", "note", null, null);

        mockMvc.perform(post("/api/incidents/" + UUID.randomUUID() + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isNotFound());
    }
}
