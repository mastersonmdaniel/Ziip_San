package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Fatura agrupadora/centralizadora (ED-22, tabela pendente
 * {@code grouped_bills}): {@code code} e o codigo unico do documento
 * (indice unico no banco). Os membros vivem em {@code grouped_bill_members}.
 * Nao usar {@code @Builder} (padrao do billing).
 */
@Entity
@Table(name = "grouped_bills")
@Getter
@Setter
public class GroupedBillEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "centralizer_customer_id", nullable = false)
    private UUID centralizerCustomerId;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "total_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "invoice_mode", nullable = false, length = 20)
    private String invoiceMode;

    @Column(name = "fiscal_document_count", nullable = false)
    private int fiscalDocumentCount;
}
