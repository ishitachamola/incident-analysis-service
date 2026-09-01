package com.incidentplatform.simulator;

public record SimulationResult(
        ScenarioType scenario,
        String service,
        int logEventsPublished,
        boolean deploymentPublished
) {
}
