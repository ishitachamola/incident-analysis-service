package com.incidentplatform.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.incidentplatform.ai.config.LlmProperties;
import com.incidentplatform.ai.config.LlmProperties.ModelLimits;
import com.incidentplatform.ai.llm.LlmQuotaExceededException.LimitType;
import com.incidentplatform.ai.llm.LlmUsageRepository.CallOutcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LlmCallGuardTest {

    private static final String MODEL = "gemini-3.5-flash-lite";
    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    private LlmUsageRepository repository;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        repository = mock(LlmUsageRepository.class);
        when(repository.tryReserveDailySlot(any(), anyString(), anyInt())).thenReturn(true);
        clock = new MutableClock(Instant.parse("2026-09-16T18:00:00Z"));
    }

    private static LlmProperties properties(boolean enabled, int perMinute, int perDay, int maxConcurrent) {
        return new LlmProperties(enabled, MODEL, 8192, 0.2, maxConcurrent, 1, PACIFIC,
                new ModelLimits(2, 10), Map.of(MODEL, new ModelLimits(perMinute, perDay)));
    }

    private LlmCallGuard guard(LlmProperties properties) {
        return new LlmCallGuard(properties, repository, clock, "AQ.real-looking-key");
    }

    @Test
    void refusesEverythingWhenSwitchedOff() {
        LlmCallGuard guard = guard(properties(false, 10, 400, 1));

        assertThatThrownBy(() -> guard.acquire(MODEL, "test"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("switched off");
        verify(repository, never()).tryReserveDailySlot(any(), anyString(), anyInt());
        verify(repository).logCall(MODEL, "test", CallOutcome.REJECTED_DISABLED, false,
                "ai.llm.enabled is false", null, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-configured", "changeme", "sk-changeme"})
    void refusesWhenNoRealApiKeyIsConfigured(String apiKey) {
        LlmCallGuard guard = new LlmCallGuard(properties(true, 10, 400, 1), repository, clock, apiKey);

        assertThatThrownBy(() -> guard.acquire(MODEL, "test"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("GEMINI_API_KEY");
        verify(repository, never()).tryReserveDailySlot(any(), anyString(), anyInt());
    }

    @Test
    void permittedCallSpendsOneDailySlotAgainstTheModelsOwnLimit() {
        LlmCallGuard guard = guard(properties(true, 10, 400, 1));

        try (LlmPermit permit = guard.acquire(MODEL, "test")) {
            assertThat(permit.model()).isEqualTo(MODEL);
        }
        verify(repository).tryReserveDailySlot(LocalDate.of(2026, 9, 16), MODEL, 400);
    }

    @Test
    void dailyBudgetIsCountedInPacificTimeNotUtc() {
        // 06:30 UTC on the 17th is still 23:30 on the 16th in Pacific time.
        clock.set(Instant.parse("2026-09-17T06:30:00Z"));
        LlmCallGuard guard = guard(properties(true, 10, 400, 1));

        guard.acquire(MODEL, "test").close();

        verify(repository).tryReserveDailySlot(LocalDate.of(2026, 9, 16), MODEL, 400);
    }

    @Test
    void refusesWhenDailyBudgetIsSpentAndSaysWhenItResets() {
        when(repository.tryReserveDailySlot(any(), anyString(), anyInt())).thenReturn(false);
        LlmCallGuard guard = guard(properties(true, 10, 400, 1));

        assertThatThrownBy(() -> guard.acquire(MODEL, "test"))
                .isInstanceOfSatisfying(LlmQuotaExceededException.class, ex -> {
                    assertThat(ex.limitType()).isEqualTo(LimitType.PER_DAY);
                    // 18:00 UTC is 11:00 Pacific, leaving 13 hours until midnight Pacific.
                    assertThat(ex.retryAfterSeconds()).isEqualTo(Duration.ofHours(13).toSeconds());
                });
        verify(repository).logCall(eq(MODEL), eq("test"), eq(CallOutcome.REJECTED_PER_DAY), eq(false),
                anyString(), isNull(), isNull(), isNull());
    }

    @Test
    void refusesOnceThePerMinuteLimitIsReachedWithoutSpendingADailySlot() {
        LlmCallGuard guard = guard(properties(true, 2, 400, 1));

        guard.acquire(MODEL, "test").close();
        guard.acquire(MODEL, "test").close();

        assertThatThrownBy(() -> guard.acquire(MODEL, "test"))
                .isInstanceOfSatisfying(LlmQuotaExceededException.class, ex -> {
                    assertThat(ex.limitType()).isEqualTo(LimitType.PER_MINUTE);
                    assertThat(ex.retryAfterSeconds()).isBetween(1L, 61L);
                });
        verify(repository, times(2)).tryReserveDailySlot(any(), anyString(), anyInt());
    }

    @Test
    void perMinuteWindowSlidesSoCallsResumeAfterAMinute() {
        LlmCallGuard guard = guard(properties(true, 1, 400, 1));
        guard.acquire(MODEL, "test").close();
        assertThatThrownBy(() -> guard.acquire(MODEL, "test")).isInstanceOf(LlmQuotaExceededException.class);

        clock.advance(Duration.ofSeconds(61));

        guard.acquire(MODEL, "test").close();
        verify(repository, times(2)).tryReserveDailySlot(any(), anyString(), anyInt());
    }

    @Test
    void aCallRefusedForTheDailyBudgetDoesNotOccupyThePerMinuteWindow() {
        when(repository.tryReserveDailySlot(any(), anyString(), anyInt())).thenReturn(false, true);
        LlmCallGuard guard = guard(properties(true, 1, 400, 1));

        assertThatThrownBy(() -> guard.acquire(MODEL, "test")).isInstanceOf(LlmQuotaExceededException.class);

        // With a limit of one per minute, this only succeeds if the refused call left no trace.
        guard.acquire(MODEL, "test").close();
    }

    @Test
    void concurrencySlotIsHeldUntilThePermitIsClosed() {
        LlmCallGuard guard = guard(properties(true, 10, 400, 1));
        LlmPermit first = guard.acquire(MODEL, "test");

        assertThatThrownBy(() -> guard.acquire(MODEL, "test"))
                .isInstanceOfSatisfying(LlmQuotaExceededException.class,
                        ex -> assertThat(ex.limitType()).isEqualTo(LimitType.CONCURRENCY));
        verify(repository, times(1)).tryReserveDailySlot(any(), anyString(), anyInt());

        first.close();
        guard.acquire(MODEL, "test").close();
    }

    @Test
    void closingAPermitTwiceReleasesItsSlotOnlyOnce() {
        LlmCallGuard guard = guard(properties(true, 10, 400, 1));
        LlmPermit permit = guard.acquire(MODEL, "test");
        permit.close();
        permit.close();

        guard.acquire(MODEL, "test");
        // A double release would have freed a second slot and let this through.
        assertThatThrownBy(() -> guard.acquire(MODEL, "test"))
                .isInstanceOf(LlmQuotaExceededException.class);
    }

    @Test
    void slotIsReleasedWhenTheDailyCheckRefusesTheCall() {
        when(repository.tryReserveDailySlot(any(), anyString(), anyInt())).thenReturn(false, true);
        LlmCallGuard guard = guard(properties(true, 10, 400, 1));

        assertThatThrownBy(() -> guard.acquire(MODEL, "test")).isInstanceOf(LlmQuotaExceededException.class);

        // With one concurrency slot, a leaked slot would make this a CONCURRENCY refusal.
        guard.acquire(MODEL, "test").close();
    }

    @Test
    void modelWithoutConfiguredLimitsGetsTheConservativeDefaults() {
        LlmCallGuard guard = guard(properties(true, 10, 400, 1));

        guard.acquire("some-new-model", "test").close();

        verify(repository).tryReserveDailySlot(any(), eq("some-new-model"), eq(10));
    }

    @Test
    void snapshotReportsUsageAgainstLimitsWithoutReservingAnything() {
        when(repository.dailyCount(LocalDate.of(2026, 9, 16), MODEL)).thenReturn(7);
        LlmCallGuard guard = guard(properties(true, 10, 400, 1));
        guard.acquire(MODEL, "test").close();

        LlmUsageSnapshot snapshot = guard.snapshot();

        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.apiKeyConfigured()).isTrue();
        assertThat(snapshot.callsInFlight()).isZero();
        assertThat(snapshot.dailyResetAt()).isEqualTo(Instant.parse("2026-09-17T07:00:00Z"));
        assertThat(snapshot.models()).singleElement().satisfies(usage -> {
            assertThat(usage.requestsToday()).isEqualTo(7);
            assertThat(usage.requestsPerDayLimit()).isEqualTo(400);
            assertThat(usage.requestsLastMinute()).isEqualTo(1);
        });
        verify(repository, times(1)).tryReserveDailySlot(any(), anyString(), anyInt());
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
