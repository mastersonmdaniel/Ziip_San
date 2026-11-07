package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Efeito tributario do cashback agregado por competencia (V4_12, P2.34): o registro para o pleito de reequilibrio contratual (LC 214/2025, arts. 373 a 377). A simulacao tarifaria e da P2.29 — aqui so o registro.
 *
 * <p>Nao usar @Builder: o Lombok descarta os campos herdados de BaseEntity.
 */
@Entity
@Table(name = "cashback_tax_effects")
@Getter
@Setter
public class CashbackTaxEffectEntity extends TenantAwareEntity {
    @Column(name = "competence_month", nullable = false)
    private LocalDate competenceMonth;

    @Column(name = "total_cbs_returned", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCbsReturned;

    @Column(name = "total_ibs_returned", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalIbsReturned;

    @Column(name = "total_returned", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalReturned;

    @Column(name = "returns_count", nullable = false)
    private int returnsCount;
}
