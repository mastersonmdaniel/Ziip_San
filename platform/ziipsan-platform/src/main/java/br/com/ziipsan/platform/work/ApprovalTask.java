package br.com.ziipsan.platform.work;

import java.time.Instant;
import java.util.UUID;

/**
 * Tarefa de aprovação comum ({@code approval_tasks} da V1_11): a decisão pendente ligada
 * 1:1 ao item de trabalho de aprovação. TODA aprovação nova do produto nasce aqui (OS,
 * retificação, parâmetro, documento, GIS, conciliação...) em vez de criar a sua própria
 * fila — é o que o aceite chama de "componente".
 *
 * <p>A segregação solicitante != aprovador é invariante DO REGISTRO, não só da regra:
 * uma tarefa decidida com {@code decidedBy == requesterId} nem sequer se constrói
 * ({@link IllegalArgumentException}). Não há construtor, flag ou caminho que produza
 * esse estado — ver também {@link ApprovalRule} e {@code wf.self_approval}.
 */
public record ApprovalTask(UUID id, UUID tenantId, UUID workItemId, UUID requesterId,
                           String objectType, UUID objectId, ApprovalStatus status,
                           UUID decidedBy, Instant decidedAt, String decisionReason,
                           Instant createdAt) {

    public ApprovalTask {
        if (id == null || tenantId == null || workItemId == null || requesterId == null
                || status == null || createdAt == null) {
            throw new IllegalArgumentException("approval_task exige id, tenant, item, solicitante, estado e criação");
        }
        if (objectType == null || objectType.isBlank() || objectId == null) {
            throw new IllegalArgumentException("approval_task exige o objeto (tipo e id)");
        }
        if (status.isPending() && (decidedBy != null || decidedAt != null)) {
            throw new IllegalArgumentException("approval_task pendente não pode ter decisão gravada");
        }
        if (!status.isPending()) {
            if (decidedBy == null || decidedAt == null) {
                throw new IllegalArgumentException("approval_task decidida exige decidedBy e decidedAt");
            }
            if (decidedBy.equals(requesterId)) {
                throw new IllegalArgumentException("segregação (wf.self_approval, classe a, sempre falso): "
                        + "quem solicitou não aprova a própria tarefa");
            }
            if (decisionReason == null || decisionReason.isBlank()) {
                throw new IllegalArgumentException("decisão de approval_task exige o motivo (decisionReason)");
            }
        }
    }
}
