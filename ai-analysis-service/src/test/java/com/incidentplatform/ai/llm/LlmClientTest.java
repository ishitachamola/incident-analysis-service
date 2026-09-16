package com.incidentplatform.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.incidentplatform.ai.config.LlmProperties;
import com.incidentplatform.ai.config.LlmProperties.ModelLimits;
import com.incidentplatform.ai.llm.LlmQuotaExceededException.LimitType;
import com.incidentplatform.ai.llm.LlmUsageRepository.CallOutcome;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;

class LlmClientTest {

    private static final String MODEL = "gemini-3.5-flash-lite";

    private ScriptedChatModel chatModel;
    private LlmCallGuard guard;
    private LlmUsageRepository repository;
    private AtomicInteger releases;
    private LlmClient client;

    @BeforeEach
    void setUp() {
        chatModel = new ScriptedChatModel();
        guard = mock(LlmCallGuard.class);
        repository = mock(LlmUsageRepository.class);
        releases = new AtomicInteger();
        when(guard.acquire(eq(MODEL), anyString()))
                .thenAnswer(invocation -> new LlmPermit(MODEL, invocation.getArgument(1), repository,
                        releases::incrementAndGet));
        LlmProperties properties = new LlmProperties(true, MODEL, 8192, 0.2, 1, 1, ZoneId.of("America/Los_Angeles"),
                new ModelLimits(2, 10), Map.of(MODEL, new ModelLimits(10, 400)));
        client = new LlmClient(chatModel, guard, properties);
    }

    @Test
    void sendsExplicitCapsOnEveryRequestAndRecordsTheOutcome() {
        chatModel.respond("{\"status\":\"ok\"}");

        LlmResult result = client.generate("incident-analysis", List.of(new UserMessage("hello")));

        assertThat(result.text()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(result.promptTokens()).isEqualTo(1200);
        assertThat(result.completionTokens()).isEqualTo(300);

        ChatOptions options = chatModel.prompts().getFirst().getOptions();
        assertThat(options.getModel()).isEqualTo(MODEL);
        assertThat(options.getMaxTokens()).isEqualTo(8192);
        assertThat(options.getTemperature()).isEqualTo(0.2);

        verify(repository).logCall(eq(MODEL), eq("incident-analysis"), eq(CallOutcome.SUCCEEDED), eq(true),
                isNull(), eq(1200), eq(300), org.mockito.ArgumentMatchers.anyLong());
        assertThat(releases).hasValue(1);
    }

    @Test
    void providerFailureIsRecordedWrappedAndNeverRetried() {
        chatModel.fail(new RuntimeException("503 Service Unavailable"));

        assertThatThrownBy(() -> client.generate("incident-analysis", List.of(new UserMessage("hello"))))
                .isInstanceOf(LlmCallFailedException.class)
                .hasMessage("The model provider request failed.");

        assertThat(chatModel.callCount()).isEqualTo(1);
        verify(repository).logCall(eq(MODEL), eq("incident-analysis"), eq(CallOutcome.FAILED), eq(true),
                contains("503"), isNull(), isNull(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(releases).hasValue(1);
    }

    @Test
    void emptyResponseCountsAsAFailedCall() {
        chatModel.respond("   ");

        assertThatThrownBy(() -> client.generate("incident-analysis", List.of(new UserMessage("hello"))))
                .isInstanceOf(LlmCallFailedException.class)
                .hasMessageContaining("empty response");
        verify(repository).logCall(eq(MODEL), eq("incident-analysis"), eq(CallOutcome.FAILED), eq(true),
                contains("empty response"), isNull(), isNull(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void callRefusedByTheGuardNeverReachesTheModel() {
        when(guard.acquire(eq(MODEL), anyString()))
                .thenThrow(new LlmQuotaExceededException(LimitType.PER_DAY, MODEL, 3600, "Daily limit reached"));

        assertThatThrownBy(() -> client.generate("incident-analysis", List.of(new UserMessage("hello"))))
                .isInstanceOf(LlmQuotaExceededException.class);
        assertThat(chatModel.callCount()).isZero();
    }

    @Test
    void responseCutOffAtTheTokenCapIsReportedAsTruncated() {
        chatModel.respondTruncated("{\"status\": \"ROOT_CA");

        LlmResult result = client.generate("incident-analysis", List.of(new UserMessage("hello")));

        assertThat(result.truncated()).isTrue();
    }
}
