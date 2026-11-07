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
 * Conta nao entregue registrada (P2.8, JOR-35): uma linha por retorno, com o
 * motivo como CHAVE do catalogo fechado {@code UndeliveredReason} (nunca
 * texto livre), a data do retorno e a origem. E a tabela que a qualidade
 * cadastral da P3.32 le (regra "conta nao entregue") e que sustenta a marca
 * de endereco nao verificado do contato.
 *
 * <p>Mesma pendencia de migration declarada de {@link PrintFileBatchEntity}:
 * sem versao Flyway reservada a P2.8 no plano, a tabela
 * {@code bill_delivery_returns} entra na integracao com versao atribuida
 * (DDL proposto no relatorio da tarefa).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "bill_delivery_returns")
@Getter
@Setter
public class BillDeliveryReturnEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    /** Chave de {@code UndeliveredReason} (catalogo fechado, versao no relatorio). */
    @Column(name = "reason_key", nullable = false, length = 40)
    private String reasonKey;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    /** Chave de {@code ReturnOrigin}. */
    @Column(name = "origin_key", nullable = false, length = 30)
    private String originKey;

    /** Lote da grafica de onde a conta saiu, quando o retorno e do lote. */
    @Column(name = "print_batch_id")
    private UUID printBatchId;
}
