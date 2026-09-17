package com.incidentplatform.ai.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessage(
        UUID id,
        UUID incidentId,
        Role role,
        String content,
        List<String> sources,
        Instant createdAt
) {

    public enum Role {
        USER,
        ASSISTANT
    }
}
