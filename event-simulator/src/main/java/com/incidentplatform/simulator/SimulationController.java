package com.incidentplatform.simulator;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping
    public List<ScenarioType> availableScenarios() {
        return List.of(ScenarioType.values());
    }

    @PostMapping("/{scenario}")
    public SimulationResult run(@PathVariable ScenarioType scenario) {
        return simulationService.run(scenario);
    }
}
