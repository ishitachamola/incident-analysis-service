package com.incidentplatform.incident.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Accounts the development token endpoint accepts.
 *
 * <p>This is a stand-in for an identity provider so the platform runs end to end on one machine.
 * Credentials come from configuration and are meant to be overridden by environment variables; a
 * real deployment would delete this and point the services at Keycloak instead.
 */
@ConfigurationProperties(prefix = "platform.auth")
public record AuthProperties(List<Account> users) {

    public AuthProperties {
        users = users != null ? List.copyOf(users) : List.of();
    }

    public record Account(String username, String password, List<String> roles) {
    }
}
