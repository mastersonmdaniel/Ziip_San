package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * EPANET [TANKS] node.
 * Pure data holder mirroring the .inp [TANKS] section; performs no hydraulics.
 */
@Getter
@Setter
public class EpanetTank {

    private String id;
    private String name;
    private BigDecimal elevation;
    private BigDecimal initLevel;
    private BigDecimal minLevel;
    private BigDecimal maxLevel;
    private BigDecimal diameter;
    private BigDecimal minVolume;
    private String volCurve = "";
}