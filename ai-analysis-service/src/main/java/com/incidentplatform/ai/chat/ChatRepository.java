package com.incidentplatform.ai.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(ChatMessage message) {
        jdbcTemplate.update("""
                INSERT INTO chat_messages (id, incident_id, role, content, sources, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)
                """,
                message.id(), message.incidentId(), message.role().name(), message.content(),
                toJson(message.sources()), Timestamp.from(message.createdAt()));
    }

    public List<ChatMessage> history(UUID incidentId) {
        return jdbcTemplate.query("""
                SELECT id, incident_id, role, content, sources::text AS sources, created_at
                FROM chat_messages WHERE incident_id = ? ORDER BY created_at
                """, this::mapRow, incidentId);
    }

    /** The most recent turns, oldest first, used to bound how much history each call carries. */
    public List<ChatMessage> recentHistory(UUID incidentId, int limit) {
        List<ChatMessage> recent = jdbcTemplate.query("""
                SELECT id, incident_id, role, content, sources::text AS sources, created_at
                FROM chat_messages WHERE incident_id = ? ORDER BY created_at DESC LIMIT ?
                """, this::mapRow, incidentId, limit);
        return recent.reversed();
    }

    private ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChatMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("incident_id", UUID.class),
                ChatMessage.Role.valueOf(rs.getString("role")),
                rs.getString("content"),
                fromJson(rs.getString("sources")),
                rs.getTimestamp("created_at").toInstant());
    }

    private String toJson(List<String> sources) {
        try {
            return objectMapper.writeValueAsString(sources != null ? sources : List.of());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise chat sources", ex);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored chat sources could not be read", ex);
        }
    }
}
