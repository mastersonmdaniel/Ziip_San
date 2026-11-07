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
 * Debito a cobrar: valor devido que entra na conta em parcelas.
 *
 * <p>DAD-20 (P2.5, trecho F3 da V4_4): o tipo e item do catalogo {@code debit_types}
 * ({@code debitTypeId}); o indicador {@code generates_bill} saiu desta tabela e vive no
 * catalogo. A proveniencia e tipada: {@code sourceType} (um dos seis valores de
 * {@code ReceivableSourceType}, com CHECK no banco), {@code sourceId}, {@code legacyReference}
 * e {@code serviceOrderId} — obrigatorio para debito de servico (CHECK
 * {@code chk_drec_service_order} e regra de dominio em {@code DebitReceivable}). Meses
 * contabil e de arrecadacao sao datas no primeiro dia do mes, como o {@code reference_month}
 * da conta. {@code serviceOrderId} nao tem FK: a tabela de ordens de servico e de outro modulo
 * (mesmo padrao da V4_2 para {@code social_tariff_enrollments.service_order_id}).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "debit_receivables")
@Getter
@Setter
public class DebitReceivableEntity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "debit_type_id", nullable = false)
    private UUID debitTypeId;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "installments", nullable = false)
    private int installments;

    @Column(name = "installments_done", nullable = false)
    private int installmentsDone;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "NORMAL";

    @Column(name = "under_review", nullable = false)
    private boolean underReview;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "legacy_reference", length = 100)
    private String legacyReference;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "accounting_month", nullable = false)
    private LocalDate accountingMonth;

    @Column(name = "collection_month")
    private LocalDate collectionMonth;

    @Column(name = "financing_term")
    private Integer financingTerm;
}
