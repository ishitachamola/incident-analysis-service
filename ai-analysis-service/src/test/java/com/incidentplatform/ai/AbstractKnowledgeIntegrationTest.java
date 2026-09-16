package com.incidentplatform.ai;

import com.incidentplatform.ai.knowledge.HashingEmbeddingModel;
import com.incidentplatform.ai.llm.ScriptedChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs against a real Postgres with pgvector, so the vector column, the HNSW index, metadata
 * filtering and the quota counters are all exercised as they behave in production.
 *
 * <p>No test may ever reach a hosted model. Three independent safeguards ensure that:
 * <ol>
 *   <li>the chat model is replaced by a scripted stub that returns canned responses;</li>
 *   <li>the model endpoint is pointed at an unreachable local port, so even an unstubbed call fails
 *       immediately instead of reaching the provider;</li>
 *   <li>the API key is a dummy value that the provider would reject.</li>
 * </ol>
 */
@SpringBootTest
@Import(AbstractKnowledgeIntegrationTest.OfflineModelConfiguration.class)
public abstract class AbstractKnowledgeIntegrationTest {

    public static final String UNREACHABLE_ENDPOINT = "http://127.0.0.1:9";
    public static final String TEST_MODEL = "gemini-3.5-flash-lite";

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

        registry.add("spring.ai.openai.api-key", () -> "test-key-not-used");
        registry.add("spring.ai.openai.base-url", () -> UNREACHABLE_ENDPOINT);
        registry.add("platform.incident-service.base-url", () -> UNREACHABLE_ENDPOINT);

        // Tests exercise many analyses within a minute; the per-minute behaviour has its own unit
        // tests, so the shared context gets headroom rather than intermittent refusals.
        registry.add("ai.llm.model", () -> TEST_MODEL);
        registry.add("ai.llm.models[" + TEST_MODEL + "].requests-per-minute", () -> "10000");
        registry.add("ai.llm.models[" + TEST_MODEL + "].requests-per-day", () -> "10000");
    }

    @TestConfiguration
    public static class OfflineModelConfiguration {

        @Bean
        @Primary
        EmbeddingModel offlineEmbeddingModel() {
            return new HashingEmbeddingModel();
        }

        @Bean
        @Primary
        ScriptedChatModel scriptedChatModel() {
            return new ScriptedChatModel();
        }
    }
}
