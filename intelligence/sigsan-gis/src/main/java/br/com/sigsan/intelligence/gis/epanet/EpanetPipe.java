package br.com.sigsan.intelligence.gis.epanet;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * EPANET [PIPES] link.
 * Pure data holder mirroring the .inp [PIPES] section; performs no hydraulics.
 *
 * <p>Note on {@code status}: EPANET encodes link status as OPEN/CLOSED (1/0).
 * The service assigns {@code pipe.setStatus(1)} and reads it back as a boolean
 * in {@code getStatus() ? 1 : 0}, so the int setter normalizes to a boolean.</p>
 */
@Getter
public class EpanetPipe {

    private String id;
    private String name;
    private String node1;
    private String node2;
    private double length;
    private double diameter;
    private double roughness;
    private BigDecimal minorLoss = BigDecimal.ZERO;
    private BigDecimal bulkCoeff = BigDecimal.ZERO;
    private BigDecimal wallCoeff = BigDecimal.ZERO;
    private boolean status;

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNode1(String node1) {
        this.node1 = node1;
    }

    public void setNode2(String node2) {
        this.node2 = node2;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public void setDiameter(double diameter) {
        this.diameter = diameter;
    }

    public void setRoughness(double roughness) {
        this.roughness = roughness;
    }

    public void setMinorLoss(BigDecimal minorLoss) {
        this.minorLoss = minorLoss;
    }

    public void setBulkCoeff(BigDecimal bulkCoeff) {
        this.bulkCoeff = bulkCoeff;
    }

    public void setWallCoeff(BigDecimal wallCoeff) {
        this.wallCoeff = wallCoeff;
    }

    /** Accepts the EPANET OPEN/CLOSED code; anything non-zero means open. */
    public void setStatus(int status) {
        this.status = status != 0;
    }

    /**
     * Hand-written so the accessor is {@code getStatus()} and not Lombok's
     * {@code isStatus()} for the boolean field; the .inp writer relies on the
     * former.
     */
    public boolean getStatus() {
        return this.status;
    }
}