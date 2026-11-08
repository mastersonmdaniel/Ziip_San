package br.com.ziipsan.platform.work;

import java.util.Set;
import java.util.UUID;

/**
 * Ponte da fila de Documentos fiscais do tenant (P2.33) para a caixa "Minhas
 * tarefas" (P2.28, JOR-05): rejeição, contingência e cancelamento pendente geram
 * item para o RESPONSÁVEL PELO CONTRATO, e o item se encerra quando o documento é
 * autorizado (ou o cancelamento é confirmado) — o encerramento roda pela porta comum
 * da caixa na integração, a partir do desfecho que o módulo fiscal declarar.
 *
 * <p>Tipo do item: o catálogo da P2.28 tem UM tipo para a fila fiscal da P2.33 —
 * {@link WorkItemType#FISCAL_REJECTION} ("Rejeição fiscal a tratar (fila da
 * P2.16/P2.33)"). As três pendências usam esse tipo, distinguidas pelo EVENTO de
 * origem e pelo endereço do registro (a fila já filtrada pela pendência); tipos
 * próprios para contingência/cancelamento são evolução do catálogo da P2.28 e NÃO
 * são inventados aqui (o CHECK da V1_11 lista exatamente os tipos do enum).
 *
 * <p>Endereçamento: SEMPRE o responsável pelo contrato nomeado — item fiscal sem
 * dono não nasce. Quem resolve qual usuário é o responsável pelo contrato do tenant
 * é a integração (cadastro do contrato); esta fábrica recusa responsável nulo.
 */
public final class FiscalDocumentWorkItems {

    public static final String REJECTION_EVENT = "fiscal.document.rejected";
    public static final String CONTINGENCY_EVENT = "fiscal.document.contingency-entered";
    public static final String CANCELLATION_PENDING_EVENT = "fiscal.document.cancellation-pending";
    public static final int EVENT_VERSION = 1;

    /** Endereço do registro: a fila da P2.33 já filtrada pela pendência do documento. */
    public static final String QUEUE_ADDRESS = "/fiscal/documents";

    private FiscalDocumentWorkItems() {
    }

    public static WorkItemIngestRule.OpenDraft rejectionDraft(
            UUID tenantId, UUID fiscalDocumentId, UUID contractResponsibleId, UUID requesterId) {
        return draft(tenantId, fiscalDocumentId, contractResponsibleId, requesterId,
                REJECTION_EVENT, "REJECTED");
    }

    public static WorkItemIngestRule.OpenDraft contingencyDraft(
            UUID tenantId, UUID fiscalDocumentId, UUID contractResponsibleId, UUID requesterId) {
        return draft(tenantId, fiscalDocumentId, contractResponsibleId, requesterId,
                CONTINGENCY_EVENT, "CONTINGENCY");
    }

    public static WorkItemIngestRule.OpenDraft cancellationPendingDraft(
            UUID tenantId, UUID fiscalDocumentId, UUID contractResponsibleId, UUID requesterId) {
        return draft(tenantId, fiscalDocumentId, contractResponsibleId, requesterId,
                CANCELLATION_PENDING_EVENT, "CANCELLATION_PENDING");
    }

    private static WorkItemIngestRule.OpenDraft draft(
            UUID tenantId, UUID fiscalDocumentId, UUID contractResponsibleId, UUID requesterId,
            String sourceEvent, String queueState) {
        if (tenantId == null || fiscalDocumentId == null || requesterId == null) {
            throw new IllegalArgumentException("tenant, documento fiscal e solicitante são obrigatórios");
        }
        if (contractResponsibleId == null) {
            throw new IllegalArgumentException("o item da pendência fiscal exige o responsável "
                    + "pelo contrato nomeado (aceite da P2.33): item sem dono não nasce");
        }
        return new WorkItemIngestRule.OpenDraft(null, tenantId, WorkItemType.FISCAL_REJECTION,
                "FISCAL_DOCUMENT", fiscalDocumentId, "fiscal", requesterId, contractResponsibleId,
                Set.of(), null, null,
                QUEUE_ADDRESS + "?state=" + queueState + "&documentId=" + fiscalDocumentId,
                false, sourceEvent, EVENT_VERSION);
    }
}
