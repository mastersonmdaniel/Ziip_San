package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.commercial.collection.payment.PaymentClassifier;
import br.com.ziipsan.commercial.collection.surcharge.CorrectionRule;
import br.com.ziipsan.commercial.collection.surcharge.FineRule;
import br.com.ziipsan.commercial.collection.surcharge.IndexSeries;
import br.com.ziipsan.commercial.collection.surcharge.InterestRule;
import br.com.ziipsan.commercial.collection.surcharge.LegalLimits;
import br.com.ziipsan.commercial.collection.surcharge.SurchargePolicy;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;

/**
 * Política de acréscimos e de classificação de um tenant, com vigência e a norma que a fixa
 * ({@code normSource}: contrato ou resolução do regulador). Nenhum campo de regra tem valor
 * padrão: ausência de juros ou de correção é declarada ({@code NONE}).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "collection_policies")
@Getter
@Setter
public class CollectionPolicy extends TenantAwareEntity {

    public enum InterestMode { NONE, SIMPLE_PER_DAY, DAILY_RATE }

    public enum CorrectionMode { NONE, BY_INDEX }

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "norm_source", nullable = false, length = 300)
    private String normSource;

    @Column(name = "grace_business_days", nullable = false)
    private int graceBusinessDays;

    @Column(name = "due_date_rolls_to_business_day", nullable = false)
    private boolean dueDateRollsToBusinessDay;

    @Column(name = "fine_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal finePercent;

    @Column(name = "fine_cap_percent", precision = 5, scale = 2)
    private BigDecimal fineCapPercent;

    @Column(name = "fine_cap_justification", length = 300)
    private String fineCapJustification;

    // P0.20 (V6_6): aceite reforçado do teto acima de 2%. NULL para tetos até 2%.
    @Column(name = "fine_cap_norm_citation", length = 300)
    private String fineCapNormCitation;

    @Column(name = "fine_cap_proposed_by")
    private java.util.UUID fineCapProposedBy;

    @Column(name = "fine_cap_approved_by")
    private java.util.UUID fineCapApprovedBy;

    @Column(name = "fine_cap_acceptance_ref", length = 64)
    private String fineCapAcceptanceRef;

    @Column(name = "fine_cap_risk_alert_ack")
    private Boolean fineCapRiskAlertAck;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_mode", nullable = false, length = 20)
    private InterestMode interestMode;

    @Column(name = "interest_monthly_percent", precision = 7, scale = 4)
    private BigDecimal interestMonthlyPercent;

    @Column(name = "interest_month_basis_days")
    private Integer interestMonthBasisDays;

    @Column(name = "interest_cap_percent", precision = 7, scale = 4)
    private BigDecimal interestCapPercent;

    // P0.20 (V6_6): taxa diaria de juros (DAILY_RATE). O teto da agencia (ex.: SC
    // 0,033%/dia) e validado na camada de parametro, nao aparado aqui.
    @Column(name = "interest_daily_percent", precision = 7, scale = 4)
    private BigDecimal interestDailyPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_mode", nullable = false, length = 20)
    private CorrectionMode correctionMode;

    @Column(name = "correction_index_code", length = 40)
    private String correctionIndexCode;

    @Column(name = "correction_min_months")
    private Integer correctionMinMonths;

    @Column(name = "correction_allow_negative")
    private Boolean correctionAllowNegative;

    @Column(name = "classify_tolerance", nullable = false, precision = 14, scale = 2)
    private BigDecimal classifyTolerance;

    @Column(name = "adjust_tolerance", nullable = false, precision = 14, scale = 2)
    private BigDecimal adjustTolerance;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_winner", nullable = false, length = 20)
    private PaymentClassifier.DuplicateWinner duplicateWinner;

    /**
     * Converte para a política do cálculo de acréscimos. As validações do domínio (trava de multa
     * de 2%, por exemplo) rodam aqui de novo, no código.
     *
     * @param indexSeries resolve o código do índice na série do tenant (só chamado se a correção for por índice)
     */
    public SurchargePolicy toSurchargePolicy(Function<String, IndexSeries> indexSeries) {
        FineRule fine = fineCapPercent != null
                && fineCapPercent.compareTo(LegalLimits.MAX_FINE_PERCENT) > 0
                ? FineRule.withAboveLegalCap(finePercent, fineCapPercent,
                        new FineRule.FineCapApproval(fineCapNormCitation, fineCapProposedBy,
                                fineCapApprovedBy, fineCapAcceptanceRef,
                                Boolean.TRUE.equals(fineCapRiskAlertAck)))
                : new FineRule(finePercent, fineCapPercent, fineCapJustification);
        InterestRule interest = switch (interestMode) {
            case SIMPLE_PER_DAY -> InterestRule.simplePerDay(
                    interestMonthlyPercent, interestMonthBasisDays, interestCapPercent);
            case DAILY_RATE -> InterestRule.dailyRate(interestDailyPercent, interestCapPercent);
            case NONE -> InterestRule.none();
            case null -> InterestRule.none();
        };
        CorrectionRule correction = correctionMode == CorrectionMode.BY_INDEX
                ? CorrectionRule.byIndex(indexSeries.apply(correctionIndexCode), correctionMinMonths,
                        correctionAllowNegative)
                : CorrectionRule.none();
        return new SurchargePolicy(graceBusinessDays, dueDateRollsToBusinessDay, fine, interest, correction);
    }

    public PaymentClassifier.Policy toClassifierPolicy() {
        return new PaymentClassifier.Policy(classifyTolerance, adjustTolerance, duplicateWinner);
    }
}
