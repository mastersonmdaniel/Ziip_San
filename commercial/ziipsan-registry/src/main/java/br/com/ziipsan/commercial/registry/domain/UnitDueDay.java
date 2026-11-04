package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dia de vencimento da unidade com vigência (P1.3, V3_12): a ÚNICA fonte do
 * dia de vencimento. A coluna {@code consumer_units.due_day} é o espelho do
 * valor vigente (lido pela API e pelo faturamento); a fonte é esta tabela.
 * A mudança de vencimento é a OS {@code ALTERNATIVE_DUE_DAY} (mapa da P1.31).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "unit_due_days")
@Getter
@Setter
public class UnitDueDay extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "due_day", nullable = false)
    private Short dueDay;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

}
