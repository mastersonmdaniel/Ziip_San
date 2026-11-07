package br.com.ziipsan.operations.metering.hydrometry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Modelo aprovado do catálogo (ED-14, Portaria 155/2022): portaria de aprovação, DN, Q3, R,
 * classe de exatidão 1/2, posição e regime. Q1/Q2/Q4 NÃO são colunas digitáveis: derivam de
 * Q3 e R por {@code FlowRates} (Q2/Q1=1,6; Q4/Q3=1,25) e o CHECK do banco confere Q3/R.
 */
@Entity @Table(name = "meter_models") @Getter @Setter
public class MeterModel extends TenantAwareEntity {
    @Column(name = "brand_id", nullable = false) private UUID brandId;
    @Column(name = "code", nullable = false, length = 40) private String code;
    @Column(name = "name", nullable = false, length = 120) private String name;
    @Column(name = "approval_ordinance", nullable = false, length = 60) private String approvalOrdinance;
    @Column(name = "nominal_diameter_mm", nullable = false, precision = 6, scale = 1) private BigDecimal nominalDiameterMm;
    @Column(name = "q3_m3h", nullable = false, precision = 10, scale = 3) private BigDecimal q3;
    @Column(name = "ratio_r", nullable = false) private int ratioR;
    @Column(name = "accuracy_class", nullable = false) private int accuracyClass;
    @Enumerated(EnumType.STRING) @Column(name = "position", nullable = false, length = 20)
    private HydrometryEnums.InstallationPosition position;
    @Enumerated(EnumType.STRING) @Column(name = "regime", nullable = false, length = 30)
    private HydrometryEnums.Regime regime;
    @Column(name = "active", nullable = false) private boolean active;
}
