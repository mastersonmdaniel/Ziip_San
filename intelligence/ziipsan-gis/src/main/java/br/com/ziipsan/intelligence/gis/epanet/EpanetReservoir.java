package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * EPANET [RESERVOIRS] node - an infinite fixed-head source.
 * Pure data holder mirroring the .inp [RESERVOIRS] section; performs no hydraulics.
 */
@Getter
@Setter
public class EpanetReservoir {

    private String id;
    private String name;
    private BigDecimal head;
    private String pattern = "";
}