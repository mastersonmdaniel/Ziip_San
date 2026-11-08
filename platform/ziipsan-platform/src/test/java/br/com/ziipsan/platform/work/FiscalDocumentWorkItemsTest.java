package br.com.ziipsan.platform.work;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ponte da P2.33 para a caixa da P2.28: as três pendências (rejeição, contingência,
 * cancelamento pendente) geram item endereçado ao responsável pelo contrato, com o
 * tipo fiscal do catálogo e o endereço da fila filtrada. Item sem responsável NÃO
 * nasce (aceite da P2.33 + invariante do WorkItem da P2.28).
 */
class FiscalDocumentWorkItemsTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DOC = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();

    @Test
    @DisplayName("rejeição gera item FISCAL_REJECTION para o responsável pelo contrato, apontando para a fila")
    void rejectionDraft() {
        var draft = FiscalDocumentWorkItems.rejectionDraft(TENANT, DOC, RESPONSIBLE, REQUESTER);
        assertThat(draft.type()).isEqualTo(WorkItemType.FISCAL_REJECTION);
        assertThat(draft.assigneeId()).isEqualTo(RESPONSIBLE);
        assertThat(draft.objectType()).isEqualTo("FISCAL_DOCUMENT");
        assertThat(draft.objectId()).isEqualTo(DOC);
        assertThat(draft.originModule()).isEqualTo("fiscal");
        assertThat(draft.sourceEventName()).isEqualTo(FiscalDocumentWorkItems.REJECTION_EVENT);
        assertThat(draft.recordAddress()).contains("state=REJECTED").contains(DOC.toString());
        // O rascunho satisfaz o invariante de endereçamento do WorkItem da P2.28.
        assertThat(draft.assigneeId() != null || draft.requiredPermission() != null).isTrue();
    }

    @Test
    @DisplayName("contingência e cancelamento pendente geram itens com eventos e endereços próprios")
    void contingencyAndCancellationDrafts() {
        var contingency = FiscalDocumentWorkItems.contingencyDraft(TENANT, DOC, RESPONSIBLE, REQUESTER);
        var cancellation = FiscalDocumentWorkItems.cancellationPendingDraft(TENANT, DOC, RESPONSIBLE, REQUESTER);
        assertThat(contingency.sourceEventName()).isEqualTo(FiscalDocumentWorkItems.CONTINGENCY_EVENT);
        assertThat(contingency.recordAddress()).contains("state=CONTINGENCY");
        assertThat(cancellation.sourceEventName()).isEqualTo(FiscalDocumentWorkItems.CANCELLATION_PENDING_EVENT);
        assertThat(cancellation.recordAddress()).contains("state=CANCELLATION_PENDING");
        assertThat(contingency.type()).isEqualTo(WorkItemType.FISCAL_REJECTION);
        assertThat(cancellation.type()).isEqualTo(WorkItemType.FISCAL_REJECTION);
    }

    @Test
    @DisplayName("sem responsável pelo contrato, nenhum dos três itens nasce")
    void noResponsibleNoItem() {
        assertThatThrownBy(() -> FiscalDocumentWorkItems.rejectionDraft(TENANT, DOC, null, REQUESTER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("responsável pelo contrato");
        assertThatThrownBy(() -> FiscalDocumentWorkItems.contingencyDraft(TENANT, DOC, null, REQUESTER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FiscalDocumentWorkItems.cancellationPendingDraft(TENANT, DOC, null, REQUESTER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
