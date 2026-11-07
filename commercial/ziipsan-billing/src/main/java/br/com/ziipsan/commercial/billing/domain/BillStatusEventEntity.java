package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Evento de situacao da conta em {@code bill_status_events} (P2.20, DAD-16):
 * situacao anterior e nova, motivo (de revisao ou de cancelamento, por id),
 * OS e protocolo ligados, anotacao obrigatoria da operacao e o momento.
 * Tabela <b>so de insercao</b>: a V4_16 revoga UPDATE/DELETE do papel da
 * aplicacao e um gatilho recusa qualquer tentativa -- nem o servico expoe
 * metodo de alteracao.
 *
 * <p>Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "bill_status_events")
@Getter
@Setter
public class BillStatusEventEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    /** Mes da conta, exigido pela FK composta para a bills particionada. */
    @Column(name = "bill_reference_month", nullable = false)
    private LocalDate billReferenceMonth;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 30)
    private String newStatus;

    @Column(name = "review_reason_id")
    private UUID reviewReasonId;

    @Column(name = "cancel_reason_id")
    private UUID cancelReasonId;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "protocol_id")
    private UUID protocolId;

    /** Anotacao obrigatoria da operacao (aceite: motivo de catalogo E anotacao). */
    @Column(name = "note", nullable = false, length = 500)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
}
