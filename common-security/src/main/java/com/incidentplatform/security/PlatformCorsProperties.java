package com.incidentplatform.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Origins the browser frontend may call from. Defaults to the local Angular dev server. */
@ConfigurationProperties(prefix = "platform.cors")
public record PlatformCorsProperties(List<String> allowedOrigins) {

    public PlatformCorsProperties {
        allowedOrigins = allowedOrigins != null && !allowedOrigins.isEmpty()
                ? List.copyOf(allowedOrigins)
                : List.of("http://localhost:4200", "http://localhost:8085");
    }
}
