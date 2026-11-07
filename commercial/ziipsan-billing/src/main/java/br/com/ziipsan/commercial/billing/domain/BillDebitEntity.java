package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
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
 * <p>DAD-20 (P2.5, trecho F3 da V4_4): {@code type_code} em texto livre vira a referencia ao
 * catalogo {@code debit_types} ({@code debitTypeId}) mais a fotografia do codigo
 * ({@code debitTypeCode}), para a conta emitida nao mudar se o catalogo for renomeado.
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

    // V4_14 (P2.3): mes da conta, exigido pela FK para a tabela particionada.
    // Preenchido pelo trigger trg_bill_debits_fill_month a partir da conta.
    @Column(name = "reference_month", nullable = false)
    private java.time.LocalDate referenceMonth;

    @Column(name = "receivable_id", nullable = false)
    private UUID receivableId;

    @Column(name = "debit_type_id", nullable = false)
    private UUID debitTypeId;

    @Column(name = "debit_type_code", nullable = false, length = 40)
    private String debitTypeCode;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(name = "installment_total", nullable = false)
    private int installmentTotal;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
