package br.com.ziipsan.commercial.billing.leakmeter;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Revisao por vazamento oculto (P3.28), em {@code leak_reviews} (V4_17):
 * uma linha por pedido, nascida junto da revisao de conta da P2.20
 * ({@code bill_reviews}, pela OS {@code BILL_REVIEW}) e da OS de campo
 * {@code LEAK_REVIEW} filha dela. Enquanto {@code status = OPEN} o desconto
 * ainda nao foi decidido; a decisao fotografa os volumes medidos, as medias
 * e os volumes revistos calculados por {@link LeakDiscountRule}, para a
 * retificacao da P2.20 e o Historico lerem sem recalcular.
 *
 * <p>No maximo um pedido aberto por conta (indice unico parcial da V4_17).
 * As referencias cobertas vao em JSON ({@code covered_references}, array de
 * {@code "AAAA-MM"} gravado pela classe {@code Json} do core por quem
 * persiste) -- sao a base da contagem do limite de 12 meses da
 * {@link LeakEligibilityRule}.
 *
 * <p>Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "leak_reviews")
@Getter
@Setter
public class LeakReviewEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "bill_reference_month", nullable = false)
    private LocalDate billReferenceMonth;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    /** Linha da revisao de conta da P2.20 (bill_reviews) da qual este pedido parte. */
    @Column(name = "bill_review_id", nullable = false)
    private UUID billReviewId;

    /** OS BILL_REVIEW da P2.20 que abriu a revisao de conta (mae da OS LEAK_REVIEW). */
    @Column(name = "bill_review_order_id")
    private UUID billReviewOrderId;

    /** OS LEAK_REVIEW desta tarefa (filha da BILL_REVIEW, parent_order_id no motor). */
    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "protocol_id")
    private UUID protocolId;

    /** OPEN, GRANTED ou DENIED. */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "OPEN";

    @Column(name = "claim_on", nullable = false)
    private LocalDate claimOn;

    @Column(name = "reading_on")
    private LocalDate readingOn;

    @Column(name = "due_on")
    private LocalDate dueOn;

    /** Ancora aplicada (READING_DATE ou DUE_DATE), fotografada na abertura. */
    @Column(name = "deadline_anchor", nullable = false, length = 20)
    private String deadlineAnchor;

    /** Ultimo dia tempestivo fotografado na abertura (ancora + prazo do parametro). */
    @Column(name = "deadline_on", nullable = false)
    private LocalDate deadlineOn;

    /** Referencias cobertas pelo pedido, JSON array de "AAAA-MM" (classe Json do core). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "covered_references", nullable = false, columnDefinition = "jsonb")
    private String coveredReferences;

    @Column(name = "measured_water", precision = 14, scale = 3)
    private BigDecimal measuredWater;

    @Column(name = "average_water", precision = 14, scale = 3)
    private BigDecimal averageWater;

    @Column(name = "measured_sewer", precision = 14, scale = 3)
    private BigDecimal measuredSewer;

    @Column(name = "average_sewer", precision = 14, scale = 3)
    private BigDecimal averageSewer;

    @Column(name = "revised_water", precision = 14, scale = 3)
    private BigDecimal revisedWater;

    @Column(name = "revised_sewer", precision = 14, scale = 3)
    private BigDecimal revisedSewer;

    /** Regra efetiva que produziu os volumes revistos (AVERAGE_6M ou PCT_OF_EXCESS). */
    @Column(name = "discount_rule", length = 30)
    private String discountRule;

    @Column(name = "max_discount_pct_water", precision = 7, scale = 4)
    private BigDecimal maxDiscountPctWater;

    @Column(name = "decided_on")
    private LocalDate decidedOn;

    /** Anotacao da ABERTURA do pedido (a da decisao vai em decisionNote -- Ba3 pos-revisao). */
    @Column(name = "note", nullable = false, length = 500)
    private String note;

    /** Anotacao da DECISAO (GRANT/DENY), separada da nota da abertura. */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;
}
