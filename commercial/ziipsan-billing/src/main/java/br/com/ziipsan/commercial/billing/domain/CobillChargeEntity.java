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
 * Cobranca cofaturada de uma competencia (ED-25, tabela pendente
 * {@code cobill_charges}): documento com IDENTIFICADOR UNICO
 * ({@code document_identifier}, unico por tenant), valor discriminado,
 * forma de cobranca e estado do repasse. A FK para {@code bills} so
 * existe no modo COBILLED (fatura desvinculada e carne nao tem bill).
 */
@Entity
@Table(name = "cobill_charges")
@Getter
@Setter
public class CobillChargeEntity extends TenantAwareEntity {

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "document_identifier", nullable = false, length = 60)
    private String documentIdentifier;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "billing_mode", nullable = false, length = 20)
    private String billingMode;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "exempt", nullable = false)
    private boolean exempt;

    @Column(name = "settled_amount", nullable = false)
    private BigDecimal settledAmount;

    @Column(name = "transferred_amount", nullable = false)
    private BigDecimal transferredAmount;
}
