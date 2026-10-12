package br.com.sigsan.commercial.billing.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Parcela de debito a cobrar cobrada na conta.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bill_debits")
@Getter
@Setter
public class BillDebitEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "receivable_id", nullable = false)
    private UUID receivableId;

    @Column(name = "type_code", nullable = false, length = 40)
    private String typeCode;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(name = "installment_total", nullable = false)
    private int installmentTotal;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
