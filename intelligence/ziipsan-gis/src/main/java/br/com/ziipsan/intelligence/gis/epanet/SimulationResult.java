package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of a simulation run.
 *
 * <p>The node/link maps are keyed by element id. Populating them with real
 * pressures and flows requires an EPANET solver; until one is wired in they
 * stay empty and {@link #isSolverExecuted()} is false.</p>
 */
@Getter
@Setter
public class SimulationResult {

    private boolean success = true;
    private OffsetDateTime simulationTime;
    private long durationSeconds;

    private Map<String, EpanetNodeResult> nodeResults = new LinkedHashMap<>();
    private Map<String, EpanetLinkResult> linkResults = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();

    /**
     * False when the run was produced without a real EPANET solver, meaning the
     * node/link maps carry no hydraulic values. Consumers must not present an
     * empty result as a successful hydraulic simulation.
     */
    private boolean solverExecuted = false;

    /** Deterministic network structure summary; valid without a solver. */
    private int nodeCount;
    private int linkCount;
    private double totalPipeLengthMeters;
}