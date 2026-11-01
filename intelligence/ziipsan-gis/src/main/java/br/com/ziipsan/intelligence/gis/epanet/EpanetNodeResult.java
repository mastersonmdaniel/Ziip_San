package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Result for a single node (junction/tank/reservoir) at one report period.
 *
 * <p>IMPORTANT: the hydraulic fields ({@code pressure}, {@code demand},
 * {@code head}) are ONLY populated by a real EPANET solver run. They are
 * left null by the current non-solver implementation. Never treat a null here
 * as a computed zero.</p>
 */
@Getter
@Setter
public class EpanetNodeResult {

    private String nodeId;
    private String nodeType;

    /** Computed hydraulic grade line / pressure (m). Requires an EPANET solver. */
    private BigDecimal pressure;

    /** Computed demand (L/s). Requires an EPANET solver. */
    private BigDecimal demand;

    /** Computed head (m). Requires an EPANET solver. */
    private BigDecimal head;
}