package br.com.ziipsan.intelligence.quality.lab;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lab_instrument", indexes = {
        @Index(name = "idx_lab_inst_code", columnList = "code"),
        @Index(name = "idx_lab_inst_type", columnList = "instrument_type"),
        @Index(name = "idx_lab_inst_status", columnList = "status")
})
@Getter
@Setter
public class LabInstrument extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", length = 50)
    private InstrumentType instrumentType;

    @Column(name = "location", length = 200)
    private String location;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;
    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "calibration_due")
    private OffsetDateTime calibrationDue;

    @Column(name = "calibration_frequency_days")
    private Integer calibrationFrequencyDays = 365;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "certification", columnDefinition = "jsonb")
    private String certification;

    @Column(name = "software_version", length = 50)
    private String softwareVersion;

    @Column(name = "communication_protocol", length = 50)
    private String communicationProtocol;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "connection_config", columnDefinition = "jsonb")
    private String connectionConfig;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum InstrumentType {
        SPECTROPHOTOMETER, TITRATOR, PH_METER, TURBIDIMETER, CHLORINE_ANALYZER,
        TOC_ANALYZER, ICP_MS, GC_MS, HPLC, MICROSCOPE, AUTOCLAVE,
        INCUBATOR, BALANCE, PIPETTE, OTHER;
    }
}