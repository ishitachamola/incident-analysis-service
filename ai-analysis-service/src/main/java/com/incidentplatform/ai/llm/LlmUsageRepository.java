package com.incidentplatform.ai.llm;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LlmUsageRepository {

    private static final int MAX_DETAIL_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;

    public LlmUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Takes one request slot from a model's daily budget, if one remains.
     *
     * <p>The increment is conditional on the count still being below the cap and happens in a single
     * UPDATE, which Postgres executes atomically. Two concurrent callers competing for the last slot
     * therefore cannot both succeed, which a read-then-write check could not guarantee.
     *
     * @return true if a slot was taken; false if the daily budget is already spent
     */
    public boolean tryReserveDailySlot(LocalDate quotaDay, String model, int dailyLimit) {
        jdbcTemplate.update("""
                INSERT INTO llm_daily_usage (usage_date, model, request_count)
                VALUES (?, ?, 0)
                ON CONFLICT (usage_date, model) DO NOTHING
                """, quotaDay, model);

        int updated = jdbcTemplate.update("""
                UPDATE llm_daily_usage
                SET request_count = request_count + 1
                WHERE usage_date = ? AND model = ? AND request_count < ?
                """, quotaDay, model, dailyLimit);
        return updated == 1;
    }

    public int dailyCount(LocalDate quotaDay, String model) {
        Integer count = jdbcTemplate.query(
                "SELECT request_count FROM llm_daily_usage WHERE usage_date = ? AND model = ?",
                rs -> rs.next() ? rs.getInt(1) : 0,
                quotaDay, model);
        return count != null ? count : 0;
    }

    public List<DailyUsage> usageForDay(LocalDate quotaDay) {
        return jdbcTemplate.query(
                "SELECT model, request_count FROM llm_daily_usage WHERE usage_date = ? ORDER BY model",
                (rs, row) -> new DailyUsage(rs.getString("model"), rs.getInt("request_count")),
                quotaDay);
    }

    public void logCall(String model, String purpose, CallOutcome outcome, boolean sentToProvider, String detail,
                        Integer promptTokens, Integer completionTokens, Long latencyMs) {
        jdbcTemplate.update("""
                INSERT INTO llm_call_log
                    (id, model, purpose, outcome, sent_to_provider, detail, prompt_tokens, completion_tokens, latency_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), model, purpose, outcome.name(), sentToProvider, truncate(detail),
                promptTokens, completionTokens, latencyMs);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_DETAIL_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_DETAIL_LENGTH);
    }

    public record DailyUsage(String model, int requestCount) {
    }

    public enum CallOutcome {
        SUCCEEDED,
        FAILED,
        REJECTED_DISABLED,
        REJECTED_NOT_CONFIGURED,
        REJECTED_CONCURRENCY,
        REJECTED_PER_MINUTE,
        REJECTED_PER_DAY
    }
}
