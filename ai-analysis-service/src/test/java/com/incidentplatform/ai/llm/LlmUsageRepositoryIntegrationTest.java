package com.incidentplatform.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.incidentplatform.ai.AbstractKnowledgeIntegrationTest;
import com.incidentplatform.ai.llm.LlmUsageRepository.CallOutcome;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(AbstractKnowledgeIntegrationTest.OfflineModelConfiguration.class)
class LlmUsageRepositoryIntegrationTest extends AbstractKnowledgeIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 16);

    @Autowired
    private LlmUsageRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM llm_daily_usage");
        jdbcTemplate.update("DELETE FROM llm_call_log");
    }

    @Test
    void reservesExactlyUpToTheDailyLimitThenRefuses() {
        assertThat(repository.tryReserveDailySlot(DAY, "model-a", 3)).isTrue();
        assertThat(repository.tryReserveDailySlot(DAY, "model-a", 3)).isTrue();
        assertThat(repository.tryReserveDailySlot(DAY, "model-a", 3)).isTrue();
        assertThat(repository.tryReserveDailySlot(DAY, "model-a", 3)).isFalse();

        assertThat(repository.dailyCount(DAY, "model-a")).isEqualTo(3);
    }

    @Test
    void eachModelAndEachDayHasItsOwnBudget() {
        repository.tryReserveDailySlot(DAY, "model-a", 1);

        assertThat(repository.tryReserveDailySlot(DAY, "model-a", 1)).isFalse();
        assertThat(repository.tryReserveDailySlot(DAY, "model-b", 1)).isTrue();
        assertThat(repository.tryReserveDailySlot(DAY.plusDays(1), "model-a", 1)).isTrue();
    }

    @Test
    void concurrentReservationsNeverExceedTheLimit() throws Exception {
        int threads = 20;
        int limit = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return repository.tryReserveDailySlot(DAY, "contended-model", limit);
                }));
            }
            start.countDown();

            long granted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    granted++;
                }
            }

            assertThat(granted).isEqualTo(limit);
            assertThat(repository.dailyCount(DAY, "contended-model")).isEqualTo(limit);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void callLogRecordsRefusalsAndBoundsDetailLength() {
        repository.logCall("model-a", "incident-analysis", CallOutcome.REJECTED_PER_DAY, false, "x".repeat(2000),
                null, null, null);

        Integer detailLength = jdbcTemplate.queryForObject("SELECT length(detail) FROM llm_call_log", Integer.class);
        Boolean sent = jdbcTemplate.queryForObject("SELECT sent_to_provider FROM llm_call_log", Boolean.class);
        assertThat(detailLength).isEqualTo(500);
        assertThat(sent).isFalse();
    }
}
