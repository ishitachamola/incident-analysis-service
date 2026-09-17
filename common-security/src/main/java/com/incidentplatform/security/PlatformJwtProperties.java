package com.incidentplatform.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the platform's JWTs.
 *
 * <p>Tokens are signed with a shared secret (HMAC) rather than issued by an external identity
 * provider. That keeps the whole platform runnable on one laptop while still exercising the real
 * mechanism: every service validates a signed JWT and authorises from its role claim. Swapping in
 * Keycloak means pointing the services at its issuer URI; nothing else in the code changes.
 *
 * @param secret a shared signing key; must be at least 32 characters for HMAC-SHA256
 */
@ConfigurationProperties(prefix = "platform.jwt")
public record PlatformJwtProperties(
        String secret,
        String issuer,
        Duration tokenValidity,
        Duration serviceTokenValidity
) {

    public static final String ROLES_CLAIM = "roles";

    public PlatformJwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "platform.jwt.secret must be at least 32 characters; set PLATFORM_JWT_SECRET");
        }
        issuer = issuer != null && !issuer.isBlank() ? issuer : "incident-platform";
        tokenValidity = tokenValidity != null ? tokenValidity : Duration.ofHours(8);
        serviceTokenValidity = serviceTokenValidity != null ? serviceTokenValidity : Duration.ofMinutes(30);
    }
}
