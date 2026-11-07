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
 * Trilha por campo de uma nova versao da conta (P2.20, DAD-16): uma linha
 * por campo alterado, com valor anterior e posterior (texto fotografado),
 * na tabela {@code bill_field_changes}. Usuario e momento sao o
 * {@code created_by}/{@code created_at} da base. So de insercao, como os
 * eventos de situacao.
 *
 * <p>Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "bill_field_changes")
@Getter
@Setter
public class BillFieldChangeEntity extends TenantAwareEntity {

    /** A versao NOVA da conta (a que a operacao gerou). */
    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "bill_reference_month", nullable = false)
    private LocalDate billReferenceMonth;

    /** A versao anterior, de onde o valor anterior saiu. */
    @Column(name = "previous_bill_id", nullable = false)
    private UUID previousBillId;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "field_name", nullable = false, length = 40)
    private String fieldName;

    @Column(name = "previous_value", length = 100)
    private String previousValue;

    @Column(name = "new_value", length = 100)
    private String newValue;
}
