package com.incidentplatform.ai.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisRepository {

    private static final String COLUMNS = """
            id, incident_id, model, evidence_fingerprint, result::text AS result, prompt_tokens,
            completion_tokens, latency_ms, model_calls, created_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(StoredAnalysis analysis) {
        jdbcTemplate.update("""
                INSERT INTO incident_analyses
                    (id, incident_id, status, model, evidence_fingerprint, result, prompt_tokens,
                     completion_tokens, latency_ms, model_calls, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """,
                analysis.id(), analysis.incidentId(), analysis.result().status().name(), analysis.model(),
                analysis.evidenceFingerprint(), toJson(analysis.result()), analysis.promptTokens(),
                analysis.completionTokens(), analysis.latencyMs(), analysis.modelCalls(),
                Timestamp.from(analysis.createdAt()));
    }

    public Optional<StoredAnalysis> findLatest(UUID incidentId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM incident_analyses WHERE incident_id = ? ORDER BY created_at DESC LIMIT 1",
                this::mapRow, incidentId).stream().findFirst();
    }

    /** The most recent analysis produced from exactly this evidence with this model, if any. */
    public Optional<StoredAnalysis> findLatestByFingerprint(UUID incidentId, String fingerprint, String model) {
        return jdbcTemplate.query("SELECT " + COLUMNS + """
                FROM incident_analyses
                WHERE incident_id = ? AND evidence_fingerprint = ? AND model = ?
                ORDER BY created_at DESC LIMIT 1
                """, this::mapRow, incidentId, fingerprint, model).stream().findFirst();
    }

    private StoredAnalysis mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StoredAnalysis(
                rs.getObject("id", UUID.class),
                rs.getObject("incident_id", UUID.class),
                rs.getString("model"),
                rs.getString("evidence_fingerprint"),
                fromJson(rs.getString("result")),
                (Integer) rs.getObject("prompt_tokens"),
                (Integer) rs.getObject("completion_tokens"),
                rs.getLong("latency_ms"),
                rs.getInt("model_calls"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String toJson(AnalysisResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise analysis result", ex);
        }
    }

    private AnalysisResult fromJson(String json) {
        try {
            return objectMapper.readValue(json, AnalysisResult.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored analysis result could not be read", ex);
        }
    }
}
