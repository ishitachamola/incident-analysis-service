package com.incidentplatform.security;

import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

/**
 * Mints short-lived tokens for service-to-service calls, so an internal call is authenticated like
 * any other rather than travelling on an unprotected path.
 *
 * <p>Tokens are cached until shortly before they expire: minting one per request would be wasted
 * work, and a long-lived token would be a standing risk if it leaked.
 */
public class ServiceTokenProvider {

    public static final String SERVICE_ROLE = "SERVICE";

    private final JwtEncoder jwtEncoder;
    private final PlatformJwtProperties properties;

    private volatile String cachedToken;
    private volatile Instant refreshAfter = Instant.EPOCH;

    public ServiceTokenProvider(JwtEncoder jwtEncoder, PlatformJwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public synchronized String token() {
        Instant now = Instant.now();
        if (cachedToken == null || now.isAfter(refreshAfter)) {
            Instant expiresAt = now.plus(properties.serviceTokenValidity());
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(properties.issuer())
                    .subject("service-account")
                    .issuedAt(now)
                    .expiresAt(expiresAt)
                    .claim(PlatformJwtProperties.ROLES_CLAIM, List.of(SERVICE_ROLE))
                    .build();
            cachedToken = jwtEncoder.encode(
                    JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            // Refresh a minute early so a token cannot expire in flight.
            refreshAfter = expiresAt.minusSeconds(60);
        }
        return cachedToken;
    }
}
