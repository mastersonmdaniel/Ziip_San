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
 * Membro da fatura agrupadora (ED-22, tabela pendente
 * {@code grouped_bill_members}; correcao pos-revisao, D5): a conta
 * individual ({@code billId}) de uma unidade que entrou na agrupadora
 * {@code groupedBillId}, com o total que ela contribuiu. No banco, a
 * tabela tem FK composta para {@code grouped_bills} e para {@code bills}
 * e unicidade {@code (tenant_id, bill_id)} - a mesma conta nao entra em
 * duas agrupadoras. Nao usar {@code @Builder} (padrao do billing).
 */
@Entity
@Table(name = "grouped_bill_members")
@Getter
@Setter
public class GroupedBillMemberEntity extends TenantAwareEntity {

    @Column(name = "grouped_bill_id", nullable = false)
    private UUID groupedBillId;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "total_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalValue;
}
