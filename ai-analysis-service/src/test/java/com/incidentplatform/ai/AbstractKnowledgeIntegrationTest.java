package com.incidentplatform.ai;

import com.incidentplatform.ai.knowledge.HashingEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs against a real Postgres with pgvector, so the vector column, the HNSW index and the metadata
 * filtering are all exercised as they behave in production rather than simulated.
 *
 * <p>The embedding model is replaced with a deterministic offline one: these tests are about the
 * pipeline's wiring, and making them depend on a paid external API would make them slow, costly and
 * unable to run in CI without a secret.
 */
@SpringBootTest
public abstract class AbstractKnowledgeIntegrationTest {

    // The stock postgres image has no pgvector extension, so the image that ships it is required.
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("incident_platform_test")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Mirrors the search_path the service uses in production: its own schema first, with public
        // retained so the pgvector extension's types and operators still resolve.
        registry.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=ai,public";
        });
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Present only so the OpenAI auto-configuration can start; no call is ever made to it.
        registry.add("spring.ai.openai.api-key", () -> "test-key-not-used");
    }

    @TestConfiguration
    public static class OfflineEmbeddingConfiguration {

        @Bean
        @Primary
        EmbeddingModel offlineEmbeddingModel() {
            return new HashingEmbeddingModel();
        }
    }
}
