package com.incidentplatform.incident.security;

import com.incidentplatform.security.PlatformRoles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Reading incidents is open to any signed-in user; changing operational data is not.
 *
 * <p>The API is stateless and token-authenticated, so there is no session to protect and CSRF
 * protection is switched off deliberately rather than by oversight.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/auth/token").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/incidents/**", "/api/services/**", "/api/deployments/**")
                            .hasAnyRole(PlatformRoles.READERS)
                        .requestMatchers("/api/incidents/**", "/api/services/**", "/api/deployments/**")
                            .hasAnyRole(PlatformRoles.OPERATORS)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }
}
