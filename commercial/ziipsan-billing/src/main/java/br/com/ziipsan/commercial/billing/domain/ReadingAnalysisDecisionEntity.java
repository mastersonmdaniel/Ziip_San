package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Decisao da fila de analise (JOR-09, P2.20) sobre uma leitura/conta
 * retida, em {@code reading_analysis_decisions}: liberar, manter, abrir
 * revisao ou pedir releitura, com a OS quando a acao a gera. A fila em si
 * e consulta sobre os fatos do metering/billing; esta tabela guarda a
 * decisao tomada (inclusive o "manter", que nao muda fato nenhum).
 *
 * <p>Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "reading_analysis_decisions")
@Getter
@Setter
public class ReadingAnalysisDecisionEntity extends TenantAwareEntity {

    @Column(name = "reading_id")
    private UUID readingId;

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    /** RELEASE, KEEP_HELD, OPEN_REVIEW ou REQUEST_REREADING. */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "note", length = 500)
    private String note;
}
