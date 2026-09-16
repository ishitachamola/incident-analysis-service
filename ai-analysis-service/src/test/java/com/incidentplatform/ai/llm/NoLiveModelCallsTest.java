package com.incidentplatform.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.incidentplatform.ai.AbstractKnowledgeIntegrationTest;
import com.incidentplatform.ai.config.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * Guards the guards: fails the build if the test suite could reach a real model, or if a change to
 * the shipped configuration loosens the limits that protect the provider quota.
 */
@Import(AbstractKnowledgeIntegrationTest.OfflineModelConfiguration.class)
class NoLiveModelCallsTest extends AbstractKnowledgeIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private LlmProperties llmProperties;

    @Test
    void testsUseTheScriptedModelAndAnUnreachableEndpoint() {
        assertThat(chatModel).isInstanceOf(ScriptedChatModel.class);
        assertThat(environment.getProperty("spring.ai.openai.base-url")).isEqualTo(UNREACHABLE_ENDPOINT);
        assertThat(environment.getProperty("spring.ai.openai.api-key")).doesNotStartWith("AQ.");
    }

    @Test
    void shippedConfigurationAllowsExactlyOneAttemptPerCall() {
        assertThat(environment.getProperty("spring.ai.retry.max-attempts")).isEqualTo("1");
        assertThat(environment.getProperty("spring.ai.retry.on-client-errors")).isEqualTo("false");
    }

    @Test
    void shippedConfigurationBoundsConcurrencyRepairsAndOutput() {
        assertThat(llmProperties.maxConcurrentCalls()).isEqualTo(1);
        assertThat(llmProperties.maxRepairAttempts()).isEqualTo(1);
        assertThat(llmProperties.maxOutputTokens()).isEqualTo(8192);
        assertThat(environment.getProperty("spring.http.client.read-timeout")).isEqualTo("90s");
    }

    @Test
    void shippedFlashLimitsStayBelowTheFreeTiersOwnLimits() {
        // Free tier for Gemini 3.8 Flash: 5 requests per minute, 20 per day.
        LlmProperties.ModelLimits flash = llmProperties.limitsFor("gemini-3.8-flash");
        assertThat(flash.requestsPerMinute()).isLessThan(5);
        assertThat(flash.requestsPerDay()).isLessThan(20);
    }

    @Test
    void unusedOpenAiClientsAreSwitchedOff() {
        assertThat(environment.getProperty("spring.ai.model.image")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.moderation")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
        assertThat(environment.getProperty("spring.ai.model.audio.transcription")).isEqualTo("none");
    }
}
