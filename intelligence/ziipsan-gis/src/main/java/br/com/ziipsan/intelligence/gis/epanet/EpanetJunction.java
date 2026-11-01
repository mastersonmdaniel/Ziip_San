package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Coordinate;

import java.math.BigDecimal;

/**
 * EPANET [JUNCTIONS] node.
 * Pure data holder mirroring the .inp [JUNCTIONS] section; performs no hydraulics.
 */
@Getter
@Setter
public class EpanetJunction {

    private String id;
    private String name;
    private BigDecimal elevation;
    private BigDecimal demand;
    private String demandPattern = "";
    private BigDecimal emitterCoeff = BigDecimal.ZERO;
    private BigDecimal initialQuality = BigDecimal.ZERO;
    private Coordinate coordinates;
}