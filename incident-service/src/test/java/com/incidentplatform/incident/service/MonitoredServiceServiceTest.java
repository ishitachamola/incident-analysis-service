package com.incidentplatform.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.incidentplatform.incident.dto.CreateServiceRequest;
import com.incidentplatform.incident.entity.MonitoredService;
import com.incidentplatform.incident.exception.DuplicateResourceException;
import com.incidentplatform.incident.exception.ResourceNotFoundException;
import com.incidentplatform.incident.repository.MonitoredServiceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitoredServiceServiceTest {

    @Mock
    private MonitoredServiceRepository repository;

    @InjectMocks
    private MonitoredServiceService service;

    @Test
    void throwsWhenServiceNameAlreadyExists() {
        when(repository.existsByName("payment-service")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateServiceRequest("payment-service", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("payment-service");
    }

    @Test
    void createsServiceWhenNameIsUnique() {
        when(repository.existsByName("order-service")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateServiceRequest("order-service", "handles orders"));

        assertThat(response.name()).isEqualTo("order-service");
        assertThat(response.description()).isEqualTo("handles orders");
        verify(repository).save(any());
    }

    @Test
    void throwsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrThrow(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void returnsServiceWhenFound() {
        UUID id = UUID.randomUUID();
        MonitoredService entity = new MonitoredService("inventory-service", null);
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        assertThat(service.getOrThrow(id)).isSameAs(entity);
    }
}
