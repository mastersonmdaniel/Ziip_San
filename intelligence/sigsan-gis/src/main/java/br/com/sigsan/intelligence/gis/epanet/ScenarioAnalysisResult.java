package br.com.sigsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Comparison of a base simulation against a modified (scenario) simulation.
 *
 * <p>Comparison fields stay null until a scenario is actually modelled AND a
 * real EPANET solver produces both runs. See
 * {@link #isScenarioModelled()} and
 * {@link #isSolverExecuted()}.</p>
 */
@Getter
@Setter
public class ScenarioAnalysisResult {

    /** The base-case run that was actually executed. */
    private SimulationResult baseResult;

    /** Scenario-modifier name (e.g. PIPE_BREAK). Null when not modelled. */
    private String scenarioType;

    /** The element the scenario targets. Null when not modelled. */
    private String targetElementId;

    /** True once a scenario modification has actually been applied and re-run. */
    private boolean scenarioModelled = false;

    /** True only when both runs were produced by a real EPANET solver. */
    private boolean solverExecuted = false;

    /** Deltas below are null until scenarioModelled && solverExecuted. */
    private BigDecimal avgPressureDelta;
    private BigDecimal minPressureDelta;
    private BigDecimal maxPressureDelta;
    private BigDecimal totalHeadlossDelta;
}