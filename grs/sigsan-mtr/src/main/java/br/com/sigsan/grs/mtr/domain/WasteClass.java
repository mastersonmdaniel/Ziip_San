package br.com.sigsan.grs.mtr.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "waste_class", indexes = {
        @Index(name = "idx_waste_class_code", columnList = "code"),
        @Index(name = "idx_waste_class_hazard", columnList = "hazard_class")
})
@Getter
@Setter
public class WasteClass extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code; // NBR 10.004 code: A.1.1, B.2.3, etc.

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "hazard_class", nullable = false, length = 3)
    private HazardClass hazardClass; // I (perigoso), II (não inerte), III (inerte)

    @Column(name = "physical_state", length = 1)
    private String physicalState; // S, L, P, G

    @Column(name = "sinir_code", length = 20)
    private String sinirCode;

    @Column(name = "mtr_code", length = 20)
    private String mtrCode;

    @Column(name = "default_destination_type", length = 50)
    private String defaultDestinationType;

    @Column(name = "requires_special_handling")
    private Boolean requiresSpecialHandling = false;

    @Column(name = "un_number", length = 10)
    private String unNumber; // ONU number for transport

    @Column(name = "packaging_group", length = 10)
    private String packagingGroup; // I, II, III

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum HazardClass {
        I, // Perigoso
        II, // Não inerte
        III; // Inerte
    }
}