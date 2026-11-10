package br.com.ziipsan.grs.mtr.dmr;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E3.
// DMR (Declaração de Movimentação de Resíduos) trimestral do gerador: agregação determinística
// dos MTRs do tenant no trimestre. Estado próprio DRAFT -> CLOSED. A SUBMISSÃO ao sistema
// (SINIR/IMA) é acto externo e não acontece aqui (ver integration/): a DMR entrega conteúdo e
// registo; submission_status nasce NOT_SUBMITTED e nenhum caminho deste pacote o finge submetido.
@Entity
@Table(name = "waste_dmr", indexes = {
        @Index(name = "idx_waste_dmr_period", columnList = "year, quarter"),
        @Index(name = "idx_waste_dmr_status", columnList = "status")
})
@Getter
@Setter
public class WasteDmr extends TenantAwareEntity {

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "quarter", nullable = false)
    private int quarter; // 1..4

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DmrStatus status = DmrStatus.DRAFT;

    @Column(name = "mtr_count", nullable = false)
    private int mtrCount;

    @Column(name = "total_quantity_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal totalQuantityKg = BigDecimal.ZERO;

    @Column(name = "destined_quantity_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal destinedQuantityKg = BigDecimal.ZERO;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb")
    private String content; // agregação por classe/destinador, serializada com Json (nunca Map.toString)

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "submission_status", nullable = false, length = 20)
    private String submissionStatus = "NOT_SUBMITTED";

    public enum DmrStatus { DRAFT, CLOSED }
}
