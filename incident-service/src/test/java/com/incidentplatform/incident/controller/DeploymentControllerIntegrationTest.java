package com.incidentplatform.incident.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentplatform.incident.AbstractIntegrationTest;
import com.incidentplatform.incident.dto.CreateDeploymentRequest;
import com.incidentplatform.incident.dto.CreateServiceRequest;
import com.incidentplatform.incident.entity.DeploymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class DeploymentControllerIntegrationTest extends AbstractIntegrationTest {

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
                                new CreateServiceRequest("order-service-" + UUID.randomUUID(), null))))
                .andReturn().getResponse().getContentAsString();
        serviceId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    @Test
    void createsDeploymentWithDefaultsAndListsByService() throws Exception {
        CreateDeploymentRequest request = new CreateDeploymentRequest(serviceId, "v2.4.1", null, null);

        mockMvc.perform(post("/api/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("v2.4.1"))
                .andExpect(jsonPath("$.status").value(DeploymentStatus.SUCCESS.name()))
                .andExpect(jsonPath("$.deployedAt").exists());

        mockMvc.perform(get("/api/deployments").param("service", serviceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").exists());
    }

    @Test
    void rejectsDeploymentForUnknownService() throws Exception {
        CreateDeploymentRequest request = new CreateDeploymentRequest(UUID.randomUUID(), "v1.0.0", null, null);

        mockMvc.perform(post("/api/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
