package br.com.ziipsan.commercial.fiscal.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Vínculo entre a pendência fiscal (V4_11, P2.33) e o item de "minhas tarefas" que
 * ela gerou na caixa da P2.28 ({@code work_items} da V1_11, em outro módulo — a
 * ligação é por id, sem FK entre módulos, como o vínculo com pagamentos da V4_7).
 * O encerramento do item acompanha o desfecho da pendência (documento autorizado,
 * cancelamento confirmado).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "fiscal_pendency_work_items")
@Getter
@Setter
public class FiscalPendencyWorkItemEntity extends TenantAwareEntity {

    @Column(name = "fiscal_document_id", nullable = false)
    private UUID fiscalDocumentId;

    @Column(name = "pendency_kind", nullable = false, length = 30)
    private String pendencyKind;

    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    @Column(name = "contract_responsible_id", nullable = false)
    private UUID contractResponsibleId;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "close_reason", length = 100)
    private String closeReason;
}
