package com.incidentplatform.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentplatform.incident.AbstractIntegrationTest;
import com.incidentplatform.incident.dto.CreateServiceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class ServiceControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createAndFetchService() throws Exception {
        MockMvc mockMvc = mockMvc();
        CreateServiceRequest request = new CreateServiceRequest("payment-service", "Handles payments");

        MvcResult created = mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("payment-service"))
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/services/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("payment-service"))
                .andExpect(jsonPath("$.description").value("Handles payments"));
    }

    @Test
    void rejectsDuplicateServiceName() throws Exception {
        MockMvc mockMvc = mockMvc();
        CreateServiceRequest request = new CreateServiceRequest("order-service", null);

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        MvcResult conflict = mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(conflict.getResponse().getContentAsString()).contains("already exists");
    }

    @Test
    void rejectsBlankServiceName() throws Exception {
        mockMvc().perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateServiceRequest("", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").exists());
    }

    @Test
    void returns404ForUnknownService() throws Exception {
        mockMvc().perform(get("/api/services/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
