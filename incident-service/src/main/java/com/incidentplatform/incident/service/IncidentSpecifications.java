package com.incidentplatform.incident.service;

import com.incidentplatform.incident.entity.Incident;
import com.incidentplatform.incident.entity.IncidentSeverity;
import com.incidentplatform.incident.entity.IncidentStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    static Specification<Incident> withFilters(UUID serviceId, IncidentStatus status, IncidentSeverity severity) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (serviceId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("service").get("id"), serviceId));
            }
            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates = cb.and(predicates, cb.equal(root.get("severity"), severity));
            }
            return predicates;
        };
    }
}
