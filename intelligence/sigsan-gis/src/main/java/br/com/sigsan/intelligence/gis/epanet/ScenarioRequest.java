package br.com.sigsan.intelligence.gis.epanet;

/**
 * A what-if scenario: run the DMA under a base configuration, apply a
 * modification, re-run and compare.
 *
 * <p>Currently the scenario modification step is NOT implemented - see
 * EpanetSimulationService.analyzeScenario. Only the base run executes, and
 * the returned result reports that via
 * {@link ScenarioAnalysisResult#isScenarioModelled()}.</p>
 */
public record ScenarioRequest(
        EpanetSimulationService.SimulationOptions baseOptions,
        EpanetSimulationService.SimulationOptions scenarioOptions,
        boolean storeResults
) {}