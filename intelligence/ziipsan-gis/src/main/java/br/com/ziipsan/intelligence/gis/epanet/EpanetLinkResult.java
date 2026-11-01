package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Result for a single link (pipe/valve/pump) at one report period.
 *
 * <p>IMPORTANT: the hydraulic fields ({@code flow}, {@code velocity},
 * {@code headloss}, {@code unitHeadloss}) are ONLY populated by a real EPANET
 * solver run. They are left null by the current non-solver implementation.
 * Never treat a null here as a computed zero.</p>
 */
@Getter
@Setter
public class EpanetLinkResult {

    private String linkId;
    private String linkType;

    /** Computed flow (L/s). Requires an EPANET solver. */
    private BigDecimal flow;

    /** Computed velocity (m/s). Requires an EPANET solver. */
    private BigDecimal velocity;

    /** Computed headloss across the link (m). Requires an EPANET solver. */
    private BigDecimal headloss;

    /** Computed unit headloss (m/km). Requires an EPANET solver. */
    private BigDecimal unitHeadloss;
}