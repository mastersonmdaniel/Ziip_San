package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Contrato especial de grande usuario (ED-22, tabela pendente
 * {@code large_user_contracts}; NR 11, art. 19). Os pontos de medicao
 * dedicados vivem em {@code large_user_metering_points}.
 * {@code metering_point_ids} aqui e a fotografia em JSON (classe Json do
 * core) para leitura rapida; a tabela filha e a fonte de FK.
 * Nao usar {@code @Builder} (padrao do billing).
 */
@Entity
@Table(name = "large_user_contracts")
@Getter
@Setter
public class LargeUserContractEntity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "modality", nullable = false, length = 30)
    private String modality;

    @Column(name = "contracted_volume_m3", nullable = false)
    private int contractedVolumeM3;

    @Column(name = "minimum_volume_m3", nullable = false)
    private int minimumVolumeM3;

    @Column(name = "negotiated_tariff", nullable = false, precision = 14, scale = 4)
    private BigDecimal negotiatedTariff;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "readjustment_index", nullable = false, length = 100)
    private String readjustmentIndex;

    @Column(name = "excess_penalty_percent", nullable = false, precision = 7, scale = 2)
    private BigDecimal excessPenaltyPercent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metering_point_ids", columnDefinition = "jsonb")
    private String meteringPointIds;

    @Column(name = "agency_approved", nullable = false)
    private boolean agencyApproved;
}
