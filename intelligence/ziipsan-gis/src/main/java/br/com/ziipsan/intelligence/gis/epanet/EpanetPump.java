package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * EPANET [PUMPS] link.
 * Pure data holder mirroring the .inp [PUMPS] section; performs no hydraulics.
 */
@Getter
@Setter
public class EpanetPump {

    private String id;
    private String name;
    private String node1;
    private String node2;
    private BigDecimal power;
    private BigDecimal efficiency;
    private String pattern = "";
}