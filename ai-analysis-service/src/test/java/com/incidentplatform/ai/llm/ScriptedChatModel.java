package com.incidentplatform.ai.llm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A chat model that returns scripted responses in order and records every prompt it receives.
 * An unscripted call fails loudly, so a test can never silently depend on a real model.
 */
public class ScriptedChatModel implements ChatModel {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();

    public synchronized ScriptedChatModel respond(String text) {
        script.add(new Scripted(text, "STOP"));
        return this;
    }

    public synchronized ScriptedChatModel respondTruncated(String text) {
        script.add(new Scripted(text, "LENGTH"));
        return this;
    }

    public synchronized ScriptedChatModel fail(RuntimeException failure) {
        script.add(failure);
        return this;
    }

    public synchronized void reset() {
        script.clear();
        prompts.clear();
    }

    public int callCount() {
        return prompts.size();
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    @Override
    public synchronized ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        Object next = script.poll();
        if (next == null) {
            throw new IllegalStateException("Unscripted model call: the test did not expect the model to be called");
        }
        if (next instanceof RuntimeException failure) {
            throw failure;
        }
        Scripted scripted = (Scripted) next;
        Generation generation = new Generation(new AssistantMessage(scripted.text()),
                ChatGenerationMetadata.builder().finishReason(scripted.finishReason()).build());
        return new ChatResponse(List.of(generation),
                ChatResponseMetadata.builder().usage(new DefaultUsage(1200, 300)).build());
    }

    private record Scripted(String text, String finishReason) {
    }
}
