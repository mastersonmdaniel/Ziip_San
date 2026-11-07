package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Registro de idempotencia das operacoes de manutencao
 * ({@code bill_maintenance_operations}, P2.20): uma linha por
 * (tenant, Idempotency-Key). Repetir a chave com o mesmo pedido devolve
 * {@code resultBillId} sem duplicar; com pedido diferente, conflito.
 * {@code againstUser} e a direcao fotografada da retificacao, base da
 * contagem do teto {@code bill.rectification_limits_against_user}.
 *
 * <p>Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "bill_maintenance_operations")
@Getter
@Setter
public class BillMaintenanceOperationEntity extends TenantAwareEntity {

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "operation", nullable = false, length = 40)
    private String operation;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "result_bill_id")
    private UUID resultBillId;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    /** EXECUTED ou AWAITING_APPROVAL. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "against_user", nullable = false)
    private boolean againstUser;
}
