package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * EPANET [VALVES] link.
 * Pure data holder mirroring the .inp [VALVES] section; performs no hydraulics.
 */
@Getter
@Setter
public class EpanetValve {

    private String id;
    private String name;
    private String node1;
    private String node2;
    private double diameter;
    private ValveType type = ValveType.PRV;
    private BigDecimal setting = BigDecimal.ZERO;
    private BigDecimal minorLoss = BigDecimal.ZERO;

    public enum ValveType {
        PRV, PSV, PBV, FCV, TCV
    }
}