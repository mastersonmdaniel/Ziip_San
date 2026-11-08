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
 * Retirada a pedido do usuario (ED-25, tabela pendente
 * {@code cobill_withdrawals}): protocolo (prova do pedido), data do
 * pedido e competencia de efetivacao. Espelha
 * {@link br.com.ziipsan.commercial.billing.cobill.CobillWithdrawalRule.WithdrawalRequest}.
 *
 * <p>{@code effectiveFrom} e a COMPETENCIA de efetivacao gravada como
 * dia 1 do mes (mesmo padrao de {@code reference_month}); o CHECK do
 * SQL compara meses ({@code date_trunc('month', ...)}) - correcao
 * pos-reprovacao D2: esta era a unica das 3 tabelas da ED-25 sem
 * entidade, e o CHECK original comparava datas cheias, recusando a
 * efetivacao no proprio mes do pedido que a regra de dominio aceita.
 *
 * <p>Nao usar {@code @Builder}: padrao das entidades do billing (ED-22).
 */
@Entity
@Table(name = "cobill_withdrawals")
@Getter
@Setter
public class CobillWithdrawalEntity extends TenantAwareEntity {

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "protocol", nullable = false, length = 60)
    private String protocol;

    @Column(name = "requested_on", nullable = false)
    private LocalDate requestedOn;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
}
