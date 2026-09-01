package com.incidentplatform.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.incidentplatform.events.LogEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ScenarioLibraryTest {

    private final ScenarioLibrary library = new ScenarioLibrary();

    @ParameterizedTest
    @EnumSource(ScenarioType.class)
    void everyScenarioProducesWellFormedEvents(ScenarioType type) {
        ScenarioScript script = library.build(type);

        assertThat(script.logs()).isNotEmpty();
        assertThat(script.service()).isNotBlank();
        assertThat(script.logs()).allSatisfy(event -> {
            assertThat(event.eventId()).isNotNull();
            assertThat(event.timestamp()).isNotNull();
            assertThat(event.service()).isEqualTo(script.service());
            assertThat(event.level()).isIn("INFO", "WARN", "ERROR");
            assertThat(event.traceId()).isNotBlank();
            assertThat(event.message()).isNotBlank();
        });
    }

    @ParameterizedTest
    @EnumSource(ScenarioType.class)
    void everyScenarioEscalatesFromHealthyBaselineToErrors(ScenarioType type) {
        List<LogEvent> logs = library.build(type).logs();

        assertThat(logs).anyMatch(event -> "INFO".equals(event.level()));
        assertThat(logs).anyMatch(event -> "ERROR".equals(event.level()));

        Instant firstError = logs.stream()
                .filter(event -> "ERROR".equals(event.level()))
                .map(LogEvent::timestamp)
                .min(Instant::compareTo)
                .orElseThrow();
        Instant firstInfo = logs.stream()
                .filter(event -> "INFO".equals(event.level()))
                .map(LogEvent::timestamp)
                .min(Instant::compareTo)
                .orElseThrow();

        assertThat(firstInfo).isBefore(firstError);
    }

    @ParameterizedTest
    @EnumSource(ScenarioType.class)
    void eventsAreChronologicallyOrderedAndUniquelyIdentified(ScenarioType type) {
        List<LogEvent> logs = library.build(type).logs();

        assertThat(logs).isSortedAccordingTo((a, b) -> a.timestamp().compareTo(b.timestamp()));
        assertThat(logs.stream().map(LogEvent::eventId).distinct().toList()).hasSameSizeAs(logs);
    }

    @Test
    void deploymentDrivenScenariosAnnounceTheirDeployment() {
        assertThat(library.build(ScenarioType.DB_POOL_EXHAUSTION).deployment()).isNotNull();
        assertThat(library.build(ScenarioType.DEPLOYMENT_REGRESSION).deployment()).isNotNull();
        assertThat(library.build(ScenarioType.KAFKA_CONSUMER_LAG).deployment()).isNull();
    }

    @Test
    void dbPoolExhaustionCarriesItsCharacteristicEvidence() {
        List<LogEvent> logs = library.build(ScenarioType.DB_POOL_EXHAUSTION).logs();

        assertThat(logs).anyMatch(event -> "SQLTransientConnectionException".equals(event.exception()));
        assertThat(logs).anyMatch(event -> event.message().contains("Connection is not available"));
    }

    @Test
    void scenarioEventsFallWithinTheRecentTimeWindow() {
        Instant now = Instant.now();
        List<LogEvent> logs = library.build(ScenarioType.SLOW_QUERY).logs();

        assertThat(logs).allSatisfy(event -> {
            assertThat(event.timestamp()).isAfter(now.minusSeconds(25 * 60));
            assertThat(event.timestamp()).isBeforeOrEqualTo(now.plusSeconds(60));
        });
    }

    @Test
    void traceIdsAreStableLengthIdentifiers() {
        List<LogEvent> logs = library.build(ScenarioType.DOWNSTREAM_TIMEOUT).logs();

        assertThat(logs).allSatisfy(event -> assertThat(event.traceId()).hasSize(16));
    }
}
