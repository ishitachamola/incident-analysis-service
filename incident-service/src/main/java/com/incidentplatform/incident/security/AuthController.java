package com.incidentplatform.incident.security;

import com.incidentplatform.security.PlatformJwtProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Issues the platform's JWTs for development. Replaced by Keycloak in a real deployment. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthProperties authProperties;
    private final PlatformJwtProperties jwtProperties;
    private final JwtEncoder jwtEncoder;

    public AuthController(AuthProperties authProperties, PlatformJwtProperties jwtProperties, JwtEncoder jwtEncoder) {
        this.authProperties = authProperties;
        this.jwtProperties = jwtProperties;
        this.jwtEncoder = jwtEncoder;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        AuthProperties.Account account = authProperties.users().stream()
                .filter(candidate -> candidate.username().equals(request.username()))
                .filter(candidate -> candidate.password().equals(request.password()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Incorrect username or password"));

        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.tokenValidity());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(account.username())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(PlatformJwtProperties.ROLES_CLAIM, account.roles())
                .build();

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, account.username(), account.roles(), expiresAt);
    }

    public record TokenRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String username, List<String> roles, Instant expiresAt) {
    }
}
