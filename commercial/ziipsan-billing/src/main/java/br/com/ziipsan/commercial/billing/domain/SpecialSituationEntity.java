package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Situacao especial de faturamento (ED-22, tabela pendente
 * {@code special_situations}): registro do 23b S3.17 - unidade, dominio,
 * tipo, motivo, base legal, meses de inicio/fim/retirada como DATAS no
 * dia 1, volumes fixos, OS de abertura/encerramento e lote (OS-mae).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity} (padrao das entidades do billing).
 */
@Entity
@Table(name = "special_situations")
@Getter
@Setter
public class SpecialSituationEntity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "domain", nullable = false, length = 20)
    private String domain;

    @Column(name = "type_code", nullable = false, length = 30)
    private String typeCode;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "legal_basis")
    private String legalBasis;

    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(name = "end_month")
    private LocalDate endMonth;

    @Column(name = "withdrawn_month")
    private LocalDate withdrawnMonth;

    @Column(name = "fixed_water_volume")
    private Integer fixedWaterVolume;

    @Column(name = "fixed_sewer_volume")
    private Integer fixedSewerVolume;

    @Column(name = "opening_os_id", nullable = false)
    private UUID openingOsId;

    @Column(name = "closing_os_id")
    private UUID closingOsId;

    @Column(name = "batch_id")
    private UUID batchId;
}
