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

/**
 * Afericao de hidrometro com laudo (P3.28), em {@code meter_tests} (V4_17):
 * uma linha por pedido, ligada a OS de campo {@code METER_TEST}. O
 * hidrometro e identificado por {@code meter_id} SEM FK: a tabela de
 * hidrometros e do modulo metering (V5_1) e o precedente da V5_7 e nao
 * criar FK entre modulos alem da raiz -- a integridade e da camada de
 * aplicacao, como nos demais codigos de catalogo do tenant.
 *
 * <p>O laudo e obrigatorio para sair de REQUESTED: {@code verdict},
 * {@code measured_error_pct} (o erro que o laudo declara) e
 * {@code report_ref} (referencia do documento do laudo) so existem com
 * {@code status = REPORTED}. A decisao de cobranca fotografada em
 * {@code chargeable} e a de {@link MeterTestRules#chargeDecision} no
 * momento do laudo.
 *
 * <p>Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "meter_tests")
@Getter
@Setter
public class MeterTestEntity extends TenantAwareEntity {

    @Column(name = "meter_id", nullable = false)
    private UUID meterId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    /** OS METER_TEST desta tarefa. */
    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "protocol_id")
    private UUID protocolId;

    /** REQUESTED, REPORTED ou CANCELLED. */
    @Column(name = "status", nullable = false, length = 12)
    private String status = "REQUESTED";

    /** Data do aviso ao usuario (a antecedencia e conferida contra scheduled_for). */
    @Column(name = "notice_on", nullable = false)
    private LocalDate noticeOn;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDate scheduledFor;

    @Column(name = "tested_on")
    private LocalDate testedOn;

    /** NORMAL, IRREGULAR ou INCONCLUSIVE -- o veredito do laudo. */
    @Column(name = "verdict", length = 14)
    private String verdict;

    /** Erro medido declarado no laudo, em percentual. */
    @Column(name = "measured_error_pct", precision = 7, scale = 4)
    private BigDecimal measuredErrorPct;

    /** Referencia do documento do laudo (documentos do tenant), obrigatoria no laudo. */
    @Column(name = "report_ref", length = 200)
    private String reportRef;

    /** A afericao e cobravel? Decisao fotografada no laudo (MeterTestRules). */
    @Column(name = "chargeable")
    private Boolean chargeable;

    @Column(name = "note", nullable = false, length = 500)
    private String note;
}
