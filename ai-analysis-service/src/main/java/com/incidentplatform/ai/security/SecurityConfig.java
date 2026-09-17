package com.incidentplatform.ai.security;

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
 * Anyone signed in may read an analysis that already exists, but only an SRE can spend a model call
 * by running one or asking a follow-up, and only an admin can change the knowledge base. Authorising
 * the expensive actions more tightly than the cheap ones is itself part of controlling model spend.
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
                        .requestMatchers(HttpMethod.POST, "/api/knowledge/ingest").hasRole(PlatformRoles.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/incidents/*/analyze", "/api/incidents/*/chat")
                            .hasAnyRole(PlatformRoles.OPERATORS)
                        .requestMatchers(HttpMethod.GET, "/api/incidents/*/analysis", "/api/incidents/*/chat")
                            .hasAnyRole(PlatformRoles.READERS)
                        .requestMatchers("/api/knowledge/**", "/api/ai/**").hasAnyRole(PlatformRoles.OPERATORS)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }
}
