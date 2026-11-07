package br.com.ziipsan.platform.work;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * CONTRATO do item de trabalho que cada módulo registra na caixa (P2.28): tipo, objeto
 * (tipo + id do registro de origem), módulo de origem, solicitante, responsável OU
 * aprovadores elegíveis, prazo, estado e endereço do registro (a rota que abre o item
 * na tela do módulo de origem). É o espelho em memória de {@code work_items} (V1_11).
 *
 * <p>Endereçamento (ao menos UM é obrigatório — item sem dono não entra na caixa de
 * ninguém e some, que é exatamente a falha que a caixa existe para impedir):
 * {@code assigneeId} (responsável direto), {@code eligibleApproverIds} (aprovadores
 * elegíveis nomeados) ou {@code requiredPermission} (permissão {@code recurso:ação}
 * do catálogo da P0.13 que habilita qualquer portador).
 *
 * <p>Invariantes conferidas no construtor: estado terminal exige {@code closedAt} e
 * {@code closeReason}; estado aberto proíbe os dois; {@code escalationCount} nunca é
 * negativo. Ausência de fato obrigatório é erro explícito, nunca valor presumido.
 */
public record WorkItem(UUID id, UUID tenantId, WorkItemType type, String objectType, UUID objectId,
                       String originModule, UUID requesterId, UUID assigneeId,
                       Set<UUID> eligibleApproverIds, String requiredPermission,
                       Instant dueAt, WorkItemStatus status, String recordAddress,
                       String sourceEventName, int sourceEventVersion, UUID sourceMessageId,
                       UUID approvalTaskId, Instant createdAt, Instant closedAt, String closeReason,
                       Instant escalatedAt, int escalationCount, UUID escalatedToId) {

    public WorkItem {
        if (id == null || tenantId == null || type == null || status == null || createdAt == null) {
            throw new IllegalArgumentException("item de trabalho exige id, tenant, tipo, estado e criação");
        }
        if (objectType == null || objectType.isBlank() || objectId == null) {
            throw new IllegalArgumentException("item de trabalho exige o objeto (tipo e id do registro de origem)");
        }
        if (originModule == null || originModule.isBlank()) {
            throw new IllegalArgumentException("item de trabalho exige o módulo de origem");
        }
        if (requesterId == null) {
            throw new IllegalArgumentException("item de trabalho exige o solicitante");
        }
        eligibleApproverIds = eligibleApproverIds == null ? Set.of() : Set.copyOf(eligibleApproverIds);
        if (assigneeId == null && eligibleApproverIds.isEmpty()
                && (requiredPermission == null || requiredPermission.isBlank())) {
            throw new IllegalArgumentException("item de trabalho sem dono: informe o responsável, "
                    + "os aprovadores elegíveis ou a permissão exigida");
        }
        if (recordAddress == null || recordAddress.isBlank()) {
            throw new IllegalArgumentException("item de trabalho exige o endereço do registro de origem");
        }
        if (sourceEventName == null || sourceEventName.isBlank() || sourceEventVersion < 1) {
            throw new IllegalArgumentException("item de trabalho exige o evento de origem do catálogo (nome e versão >= 1)");
        }
        if (escalationCount < 0) {
            throw new IllegalArgumentException("escalationCount não pode ser negativo");
        }
        if (status.isTerminal() && (closedAt == null || closeReason == null || closeReason.isBlank())) {
            throw new IllegalArgumentException("item encerrado exige closedAt e closeReason");
        }
        if (status.isOpen() && closedAt != null) {
            throw new IllegalArgumentException("item aberto não pode ter closedAt");
        }
    }

    /** Vencido: aberto com prazo estritamente no passado. Item sem prazo nunca está "vencido". */
    public boolean isOverdue(Instant now) {
        return status.isOpen() && dueAt != null && dueAt.isBefore(now);
    }

    /** O usuário é o responsável direto ou um dos aprovadores elegíveis nomeados. */
    public boolean isDirectlyAddressedTo(UUID userId) {
        return userId != null && (userId.equals(assigneeId) || eligibleApproverIds.contains(userId));
    }

    /** Endereçado pela permissão: só quando NÃO há responsável/elegíveis nomeados. */
    public boolean isPermissionAddressed() {
        return assigneeId == null && eligibleApproverIds.isEmpty()
                && requiredPermission != null && !requiredPermission.isBlank();
    }
}
