package com.incidentplatform.simulator;

import com.incidentplatform.events.DeploymentEvent;
import com.incidentplatform.events.LogEvent;
import java.util.List;

/**
 * The full set of events one scenario run emits: the log lines, plus the deployment announcement
 * where the scenario involves one.
 *
 * @param deployment may be {@code null} for scenarios with no deployment involved
 */
public record ScenarioScript(
        ScenarioType type,
        String service,
        DeploymentEvent deployment,
        List<LogEvent> logs
) {
}
