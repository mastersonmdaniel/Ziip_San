package br.com.ziipsan.platform.work;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Ponte da fila de contas retidas da P2.20 (JOR-09) para a caixa — o aceite da P2.20 já
 * previa: "a fila aparece como item de minhas tarefas pela P2.28", e o da P2.28 manda:
 * "a retificação da P2.20 passa a usar a caixa SEM MUDAR A REGRA".
 *
 * <p>Por isso esta classe NÃO decide nada da fila: liberar, manter, abrir revisão e
 * pedir releitura continuam sendo ações da {@code AnalysisQueueRule}/serviço da P2.20.
 * Aqui só se monta o rascunho de abertura do item {@link WorkItemType#HELD_BILL} que
 * APONTA para a fila ({@code recordAddress} = a rota da fila com a conta), para o
 * responsável pela fila enxergar a retenção na própria caixa, com o prazo da retenção.
 * O encerramento do item acompanha o desfecho da fila pelo evento de encerramento do
 * módulo de origem (ou pelo fechamento que a integração ligar à decisão da fila).
 *
 * <p>Endereçamento: a fila é endereçada pela PERMISSÃO que a governa no tenant
 * (parâmetro do chamador — a caixa não inventa o código da permissão da P2.20), ou a
 * um responsável nomeado quando ele é passado.
 */
public final class HeldBillWorkItems {

    /** Evento de origem declarado para o caminho interno (não é mensagem da outbox). */
    public static final String SOURCE_EVENT = "billing.held-bill.registered";
    public static final int SOURCE_EVENT_VERSION = 1;

    private HeldBillWorkItems() {
    }

    /**
     * Rascunho de abertura do item de conta retida.
     *
     * @param billId          a conta retida (objeto do item)
     * @param requesterId     quem/o processo que reteve (solicitante do item)
     * @param queuePermission permissão que governa a fila no tenant (endereçamento por
     *                        permissão) — obrigatória quando {@code assigneeId} é nulo
     * @param assigneeId      responsável nomeado pela fila, ou nulo
     * @param dueAt           prazo da retenção, ou nulo quando a fila não fixa prazo
     */
    public static WorkItemIngestRule.OpenDraft heldBillDraft(UUID tenantId, UUID billId, UUID requesterId,
                                                             String queuePermission, UUID assigneeId, Instant dueAt) {
        if (tenantId == null || billId == null || requesterId == null) {
            throw new IllegalArgumentException("tenant, conta retida e solicitante são obrigatórios");
        }
        if (assigneeId == null && (queuePermission == null || queuePermission.isBlank())) {
            throw new IllegalArgumentException("o item da fila precisa de responsável nomeado ou da permissão da fila");
        }
        return new WorkItemIngestRule.OpenDraft(null, tenantId, WorkItemType.HELD_BILL,
                "BILL", billId, "billing", requesterId, assigneeId, Set.of(),
                assigneeId == null ? queuePermission : null, dueAt,
                "/billing/analysis-queue?billId=" + billId, false, SOURCE_EVENT, SOURCE_EVENT_VERSION);
    }

    /**
     * Rascunho de abertura do item de APROVAÇÃO de retificação (P2.20): nasce com
     * {@code approval_task} pendente e a decisão passa pela porta comum
     * ({@code WorkInboxService.decideApproval}) — a regra de aprovação da retificação
     * (quem aprova, conforme o tipo de OS interna da P1.31) continua expressa no
     * endereçamento: os aprovadores elegíveis são os que o módulo de origem declarar.
     */
    public static WorkItemIngestRule.OpenDraft rectificationApprovalDraft(
            UUID tenantId, UUID billId, UUID serviceOrderId, UUID requesterId,
            Set<UUID> eligibleApproverIds, Instant dueAt) {
        if (tenantId == null || billId == null || requesterId == null) {
            throw new IllegalArgumentException("tenant, conta e solicitante são obrigatórios");
        }
        if (eligibleApproverIds == null || eligibleApproverIds.isEmpty()) {
            throw new IllegalArgumentException("a aprovação de retificação exige os aprovadores elegíveis");
        }
        String address = serviceOrderId == null
                ? "/billing/bills/" + billId
                : "/service-orders/" + serviceOrderId;
        return new WorkItemIngestRule.OpenDraft(null, tenantId, WorkItemType.BILL_RECTIFICATION_APPROVAL,
                "BILL", billId, "billing", requesterId, null, eligibleApproverIds, null, dueAt,
                address, true, "billing.rectification.approval-requested", 1);
    }
}
