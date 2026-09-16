package com.incidentplatform.ai.llm;

import com.incidentplatform.ai.config.LlmProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * The only path from this service to the hosted model. Every call is permitted by {@link LlmCallGuard}
 * first, sends exactly one provider request (the application's retry policy is set to a single
 * attempt), and has its outcome recorded.
 *
 * <p>Model, output token cap and temperature are set on every request rather than left to defaults,
 * so the model a request is quota-checked against is always the model it is sent to.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ChatModel chatModel;
    private final LlmCallGuard guard;
    private final LlmProperties properties;

    public LlmClient(ChatModel chatModel, LlmCallGuard guard, LlmProperties properties) {
        this.chatModel = chatModel;
        this.guard = guard;
        this.properties = properties;
    }

    public LlmResult generate(String purpose, List<Message> messages) {
        String model = properties.model();

        try (LlmPermit permit = guard.acquire(model, purpose)) {
            ChatOptions options = ChatOptions.builder()
                    .model(model)
                    .maxTokens(properties.maxOutputTokens())
                    .temperature(properties.temperature())
                    .build();

            long startedAt = System.nanoTime();
            ChatResponse response;
            try {
                response = chatModel.call(new Prompt(messages, options));
            } catch (RuntimeException ex) {
                long latency = elapsedMillis(startedAt);
                permit.recordFailure(ex.getClass().getSimpleName() + ": " + ex.getMessage(), latency);
                log.warn("Model call for {} failed after {}ms: {}", purpose, latency, ex.getClass().getSimpleName());
                throw new LlmCallFailedException("The model provider request failed.", ex);
            }
            long latency = elapsedMillis(startedAt);

            Generation generation = response != null ? response.getResult() : null;
            String text = generation != null && generation.getOutput() != null
                    ? generation.getOutput().getText() : null;
            String finishReason = generation != null && generation.getMetadata() != null
                    ? generation.getMetadata().getFinishReason() : null;
            Usage usage = response != null && response.getMetadata() != null
                    ? response.getMetadata().getUsage() : null;
            Integer promptTokens = usage != null ? usage.getPromptTokens() : null;
            Integer completionTokens = usage != null ? usage.getCompletionTokens() : null;

            if (text == null || text.isBlank()) {
                permit.recordFailure("empty response (finishReason=" + finishReason + ")", latency);
                throw new LlmCallFailedException("The model returned an empty response.", null);
            }

            permit.recordSuccess(promptTokens, completionTokens, latency);
            log.info("Model call for {} on {} took {}ms ({} prompt / {} completion tokens)",
                    purpose, model, latency, promptTokens, completionTokens);
            return new LlmResult(text, model, finishReason, promptTokens, completionTokens, latency);
        }
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
