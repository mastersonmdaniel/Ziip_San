package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Aggregated hydraulic performance indicators derived from a SimulationResult.
 *
 * <p>Every field here is a statistic over solver-produced values. If the
 * producing result was not solver-computed, the corresponding field stays
 * null - absence of data is reported as null, never as zero.</p>
 */
@Getter
@Setter
public class HydraulicIndicators {

    private BigDecimal avgPressure;
    private BigDecimal minPressure;
    private BigDecimal maxPressure;
    private int pressureDeficitCount;
    private int pressureExcessCount;

    private BigDecimal avgVelocity;
    private BigDecimal maxVelocity;
    private int highVelocityCount;

    private BigDecimal totalHeadloss;
}