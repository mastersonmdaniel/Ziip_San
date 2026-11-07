package br.com.ziipsan.platform.work;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Caixa unificada "Minhas tarefas" (P2.28, JOR-05/PLT-13) e o componente comum de
 * aprovação. Mesmo padrão transacional do {@code TenantParameterService} e da central
 * da P3.36: cada método roda em transação própria com {@code app.current_tenant} local
 * (as tabelas têm RLS forçada) e o schema do tenant é explícito no nome da tabela.
 *
 * <p>O que este serviço garante, e onde:
 * <ul>
 *   <li><b>Contrato por eventos (P1.38/JOR-20):</b> {@link #ingestOpen}/{@link #ingestClose}
 *       só aceitam evento do catálogo, pela {@link WorkItemIngestRule}; abertura é
 *       idempotente pela mensagem da outbox ({@code source_message_id});</li>
 *   <li><b>Caminho interno síncrono:</b> {@link #openDirect} é a mesma abertura sem a
 *       outbox, para módulo NO MESMO processo registrar na hora — é por ele que a fila
 *       de contas retidas da P2.20 (JOR-09) aparece na caixa SEM mudar a regra dela
 *       (a decisão da fila continua na P2.20; aqui nasce só o item que aponta para ela);</li>
 *   <li><b>Aprovação comum:</b> {@link #decideApproval} é a ÚNICA porta de decisão das
 *       {@code approval_tasks}: aplica a {@link ApprovalRule} (segregação
 *       {@code wf.self_approval} classe a, elegibilidade, canal delegado), grava a
 *       decisão e encerra o item na mesma transação;</li>
 *   <li><b>Delegação auditada:</b> {@link #delegate} aplica a {@link DelegationRule}
 *       (janela, teto {@code wf.delegation_max_days}, concedidas ⊆ delegante) e grava o
 *       registro em {@code work_item_delegations} — a linha É a auditoria (quem, para
 *       quem, janela, conjunto concedido, motivo, revogação);</li>
 *   <li><b>Escalonamento:</b> {@link #escalateOverdue} aplica a {@link EscalationRule}
 *       ({@code wf.escalation_hours}); sem alvo resolvido o item não escala e é
 *       contado, nunca escalado "para ninguém";</li>
 *   <li><b>Trilha:</b> abertura, início, decisão, encerramento e escalonamento gravam
 *       {@code work_item_events} (só inserção) na mesma transação do fato.</li>
 * </ul>
 *
 * <p>FRONTEIRA (aceite): o escopo por área é da P3.30 e NÃO é aplicado aqui.
 */
public class WorkInboxService {

    /** Resultado de uma rodada de escalonamento: escalados e vencidos sem alvo (pendência visível). */
    public record EscalationRun(List<UUID> escalatedItemIds, List<UUID> skippedWithoutTargetIds) {
    }

    private static final String ITEM_COLUMNS = "id, tenant_id, item_type, object_type, object_id, origin_module, "
            + "requester_id, assignee_id, eligible_approver_ids, required_permission, due_at, status, "
            + "record_address, source_event_name, source_event_version, source_message_id, approval_task_id, "
            + "close_reason, closed_at, escalated_at, escalation_count, escalated_to_id, created_at";

    private static final String TASK_COLUMNS = "id, tenant_id, work_item_id, requester_id, object_type, object_id, "
            + "status, decided_by, decided_at, decision_reason, created_at";

    private static final String DELEGATION_COLUMNS = "id, tenant_id, delegator_id, delegate_id, starts_at, ends_at, "
            + "granted_permissions, reason, created_by, revoked_at, revoked_by, created_at";

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;
    private final WorkEventCatalog catalog;
    private final WorkParameters parameters;
    private final UserPermissions userPermissions;
    private final EscalationTargetResolver escalationTargets;
    private final ZoneId tenantZone;
    private final Clock clock;

    public WorkInboxService(DataSource dataSource, String schema, UUID tenantId, WorkEventCatalog catalog,
                            WorkParameters parameters, UserPermissions userPermissions,
                            EscalationTargetResolver escalationTargets, ZoneId tenantZone, Clock clock) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null || catalog == null
                || parameters == null || userPermissions == null || escalationTargets == null
                || tenantZone == null || clock == null) {
            throw new IllegalArgumentException("dataSource, schema, tenantId, catálogo, parâmetros, permissões, "
                    + "resolvedor de escalonamento, fuso e relógio são obrigatórios");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
        this.catalog = catalog;
        this.parameters = parameters;
        this.userPermissions = userPermissions;
        this.escalationTargets = escalationTargets;
        this.tenantZone = tenantZone;
        this.clock = clock;
    }

    private String t(String table) {
        return "\"" + schema.replace("\"", "\"\"") + "\"." + table;
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Operação da caixa de tarefas falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operação da caixa de tarefas falhou: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Abertura e encerramento (eventos da P1.38 e caminho interno)
    // ------------------------------------------------------------------

    /**
     * Ingere a mensagem de ABERTURA da outbox. Reentrega da mesma mensagem devolve o
     * item existente. IDEMPOTÊNCIA SOB CONCORRÊNCIA (correção pós-revisão M2): se
     * duas entregas da mesma mensagem correrem em paralelo, as duas podem passar
     * pela leitura prévia sem achar o item; quem perde a corrida bate no índice
     * único {@code uq_work_items_source_message} na gravação. Nesse caso — e só
     * nesse — a ingestão NÃO devolve erro: a transação perdedora já foi desfeita
     * (no Postgres, transação que violou unicidade está abortada e não aceita nova
     * consulta, por isso a releitura roda numa transação NOVA) e o item existente,
     * gravado pelo vencedor, é relido e devolvido, como numa reentrega comum.
     * Qualquer outra falha propaga como veio.
     */
    public UUID ingestOpen(WorkOutboxMessage message) {
        WorkItemIngestRule.OpenDraft draft = WorkItemIngestRule.draftOpen(message, catalog);
        requireMessageTenant(message);
        try {
            return openInternal(draft);
        } catch (RuntimeException failure) {
            if (draft.sourceMessageId() != null && isSourceMessageUniqueViolation(failure)) {
                UUID existing = inTx(c -> findBySourceMessage(c, draft.sourceMessageId()));
                if (existing != null) {
                    return existing;
                }
            }
            throw failure;
        }
    }

    /** A falha é a perda da corrida pelo índice único da mensagem de origem (SQLState 23505 nomeando a constraint)? */
    private static boolean isSourceMessageUniqueViolation(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains("uq_work_items_source_message")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Abertura síncrona para módulo no mesmo processo (ex.: a fila da P2.20 ao reter uma
     * conta). Sem {@code sourceMessageId} não há dedup por mensagem: a idempotência é do
     * chamador pela sua própria chave de negócio — registrar duas vezes o mesmo fato com
     * rascunhos diferentes cria dois itens, e isso é erro do chamador, não desta caixa.
     */
    public UUID openDirect(WorkItemIngestRule.OpenDraft draft) {
        if (draft == null || !tenantId.equals(draft.tenantId())) {
            throw new IllegalArgumentException("rascunho de abertura é obrigatório e precisa ser deste tenant");
        }
        return openInternal(draft);
    }

    private UUID openInternal(WorkItemIngestRule.OpenDraft draft) {
        return inTx(c -> {
            if (draft.sourceMessageId() != null) {
                UUID existing = findBySourceMessage(c, draft.sourceMessageId());
                if (existing != null) {
                    return existing;
                }
            }
            Instant now = clock.instant();
            UUID itemId = UUID.randomUUID();
            UUID taskId = null;
            if (draft.approval()) {
                taskId = UUID.randomUUID();
                insertApprovalTask(c, new ApprovalTask(taskId, tenantId, itemId, draft.requesterId(),
                        draft.objectType(), draft.objectId(), ApprovalStatus.PENDING, null, null, null, now));
            }
            WorkItem item = new WorkItem(itemId, tenantId, draft.type(), draft.objectType(), draft.objectId(),
                    draft.originModule(), draft.requesterId(), draft.assigneeId(), draft.eligibleApproverIds(),
                    draft.requiredPermission(), draft.dueAt(), WorkItemStatus.OPEN, draft.recordAddress(),
                    draft.sourceEventName(), draft.sourceEventVersion(), draft.sourceMessageId(), taskId,
                    now, null, null, null, 0, null);
            insertItem(c, item);
            insertEvent(c, itemId, "CREATED", draft.requesterId(),
                    Json.write(Map.of("sourceEvent", draft.sourceEventName(),
                            "sourceEventVersion", draft.sourceEventVersion(),
                            "approval", draft.approval())));
            return itemId;
        });
    }

    /**
     * Ingere a mensagem de ENCERRAMENTO da outbox. Item já encerrado: reentrega é no-op
     * que devolve {@code false} (nada mudou). Item com {@code approval_task} PENDENTE não
     * encerra por evento (409): a decisão da aprovação é a porta de encerramento dele —
     * encerrar por fora deixaria uma tarefa decidível sobre um item morto.
     *
     * <p><b>CONVENÇÃO DESTA ENTREGA (explícita, pós-revisão m4 — pendente de decisão
     * do plano, NÃO regra do aceite):</b> o estado final do encerramento por evento é
     * derivado do {@code closeReason} por uma única regra — {@code CANCELLED} (exato)
     * vira {@link WorkItemStatus#CANCELLED}; QUALQUER outro motivo
     * ({@code COMPLETED}, {@code SUPERSEDED}, {@code APPROVED}...) vira
     * {@link WorkItemStatus#DONE}, com o motivo gravado literal em
     * {@code close_reason}. Não há, nesta entrega, estado terminal distinto por
     * motivo além de cancelado/não-cancelado; se o plano decidir estados por motivo,
     * a mudança é neste mapeamento e nos testes dele.
     *
     * @return true se ESTA mensagem encerrou o item
     */
    public boolean ingestClose(WorkOutboxMessage message) {
        WorkItemIngestRule.CloseDraft draft = WorkItemIngestRule.draftClose(message, catalog);
        requireMessageTenant(message);
        return inTx(c -> {
            WorkItem item = loadItem(c, draft.workItemId())
                    .orElseThrow(() -> new ApiException.NotFound("Item de trabalho não encontrado para encerramento."));
            if (item.status().isTerminal()) {
                return false;
            }
            if (item.approvalTaskId() != null) {
                ApprovalTask task = loadTask(c, item.approvalTaskId())
                        .orElseThrow(() -> new IllegalStateException("item com approval_task_id sem a tarefa correspondente"));
                if (task.status().isPending()) {
                    throw new ApiException.Conflict("approval-pending",
                            "O item tem aprovação pendente: ele encerra pela decisão da aprovação, não por evento.");
                }
            }
            WorkItemStatus closed = "CANCELLED".equals(draft.closeReason()) ? WorkItemStatus.CANCELLED : WorkItemStatus.DONE;
            closeItem(c, item, closed, draft.closeReason(), clock.instant());
            insertEvent(c, item.id(), "CLOSED", null,
                    Json.write(Map.of("sourceEvent", draft.sourceEventName(), "closeReason", draft.closeReason())));
            return true;
        });
    }

    private void requireMessageTenant(WorkOutboxMessage message) {
        if (message == null || !tenantId.equals(message.tenantId())) {
            throw new IllegalArgumentException("mensagem é obrigatória e precisa ser deste tenant");
        }
    }

    // ------------------------------------------------------------------
    // Caixa do usuário
    // ------------------------------------------------------------------

    /**
     * A caixa de {@code userId}: itens ABERTOS visíveis para ele (ver
     * {@link WorkItemVisibility}), no filtro por prazo pedido, ordenados por vencimento
     * (quem vence antes primeiro; sem prazo por último) e criação. A varredura lê no
     * máximo 500 abertos do tenant e filtra em memória — o volume de pendências abertas
     * por tenant é dessa ordem; se um dia não for, a paginação entra aqui sem mudar o
     * contrato (pendência declarada no relatório).
     */
    public List<WorkItem> listInbox(UUID userId, DueFilter filter, int limit) {
        if (userId == null || filter == null) {
            throw new IllegalArgumentException("usuário e filtro por prazo são obrigatórios");
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit precisa estar entre 1 e 200");
        }
        Set<String> permissions = userPermissions.permissionsOf(tenantId, userId);
        return inTx(c -> {
            List<WorkDelegation> received = delegationsReceivedBy(c, userId);
            Map<UUID, Set<String>> delegatorPermissions = currentPermissionsByDelegator(received);
            Instant now = clock.instant();
            List<WorkItem> visible = new ArrayList<>();
            for (WorkItem item : openItems(c)) {
                if (WorkItemVisibility.isVisibleTo(item, userId, permissions, received, delegatorPermissions, now)
                        && filter.matches(item, now, tenantZone)) {
                    visible.add(item);
                }
            }
            return visible.size() > limit ? List.copyOf(visible.subList(0, limit)) : List.copyOf(visible);
        });
    }

    /** Contagem por filtro para o contador do item de menu (mesma visibilidade da lista). */
    public long countInbox(UUID userId, DueFilter filter) {
        if (userId == null || filter == null) {
            throw new IllegalArgumentException("usuário e filtro por prazo são obrigatórios");
        }
        return countAll(userId, filter);
    }

    private long countAll(UUID userId, DueFilter filter) {
        Set<String> permissions = userPermissions.permissionsOf(tenantId, userId);
        return inTx(c -> {
            List<WorkDelegation> received = delegationsReceivedBy(c, userId);
            Map<UUID, Set<String>> delegatorPermissions = currentPermissionsByDelegator(received);
            Instant now = clock.instant();
            long count = 0;
            for (WorkItem item : openItems(c)) {
                if (WorkItemVisibility.isVisibleTo(item, userId, permissions, received, delegatorPermissions, now)
                        && filter.matches(item, now, tenantZone)) {
                    count++;
                }
            }
            return count;
        });
    }

    /**
     * Permissões ATUAIS de cada delegante distinto das delegações recebidas, pela
     * porta da P0.13 (correção pós-revisão A2): é este mapa — e nunca a foto
     * {@code grantedPermissions} sozinha — que alimenta a interseção
     * {@link DelegationRule#effectiveGrantedPermissions} na decisão e na
     * visibilidade por delegação. Delegante repetido é consultado uma vez só.
     */
    private Map<UUID, Set<String>> currentPermissionsByDelegator(List<WorkDelegation> delegations) {
        Map<UUID, Set<String>> byDelegator = new HashMap<>();
        for (WorkDelegation delegation : delegations) {
            byDelegator.computeIfAbsent(delegation.delegatorId(),
                    delegatorId -> userPermissions.permissionsOf(tenantId, delegatorId));
        }
        return byDelegator;
    }

    /** Marca o item como em andamento (OPEN/ESCALATED → IN_PROGRESS), por quem o enxerga na caixa. */
    public WorkItem startItem(UUID itemId, UUID actorId) {
        if (itemId == null || actorId == null) {
            throw new IllegalArgumentException("item e ator são obrigatórios");
        }
        Set<String> permissions = userPermissions.permissionsOf(tenantId, actorId);
        return inTx(c -> {
            WorkItem item = loadItem(c, itemId)
                    .orElseThrow(() -> new ApiException.NotFound("Item de trabalho não encontrado."));
            Instant now = clock.instant();
            List<WorkDelegation> received = delegationsReceivedBy(c, actorId);
            if (!WorkItemVisibility.isVisibleTo(item, actorId, permissions, received,
                    currentPermissionsByDelegator(received), now)) {
                throw new ApiException.NotFound("Item de trabalho não encontrado.");
            }
            if (item.status() == WorkItemStatus.IN_PROGRESS) {
                return item;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("work_items") + " SET status = 'IN_PROGRESS', updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ? AND status IN ('OPEN', 'ESCALATED')")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, itemId);
                if (ps.executeUpdate() != 1) {
                    throw new ApiException.Conflict("work-item-closed", "O item já foi encerrado.");
                }
            }
            insertEvent(c, itemId, "STARTED", actorId, "{}");
            return loadItem(c, itemId).orElseThrow();
        });
    }

    // ------------------------------------------------------------------
    // Aprovação comum
    // ------------------------------------------------------------------

    /**
     * Decide a approval_task do item (aprovar ou rejeitar) — a ÚNICA porta de decisão.
     * Aplica a {@link ApprovalRule} com o valor resolvido de {@code wf.self_approval}
     * (a trava explode se ele vier verdadeiro), grava decisão + canal
     * (DIRECT/DELEGATED) na tarefa e na trilha, e encerra o item na mesma transação.
     * O EFEITO do objeto aprovado (a OS executa, a conta retifica, o parâmetro vigora)
     * é do módulo de origem, que reage ao encerramento — a caixa decide a aprovação,
     * não executa o negócio alheio.
     *
     * <p>Correções pós-revisão aplicadas NESTE caminho (a regra é chamada antes de
     * qualquer UPDATE — recusa aqui significa zero gravação, conferido em teste):
     * <b>A1</b>, delegação cujo delegante é o solicitante não habilita o terceiro a
     * decidir (a segregação não se lava por delegação); <b>A2/M1</b>, o canal
     * delegado usa o escopo EFETIVO (concedidas ∩ permissões ATUAIS de cada
     * delegante, resolvidas aqui pela porta {@code UserPermissions} a cada decisão)
     * e exige escopo efetivo não vazio — com a {@code requiredPermission} do item
     * dentro dele, quando declarada.
     *
     * <p><b>CONVENÇÃO DESTA ENTREGA (explícita, pós-revisão m4 — pendente de decisão
     * do plano, NÃO regra do aceite):</b> aprovar E rejeitar encerram o item como
     * {@link WorkItemStatus#DONE}; o desfecho vai no {@code closeReason}
     * ({@code APPROVED} ou {@code REJECTED}) e no estado da tarefa
     * ({@code APPROVED}/{@code REJECTED}). Rejeição NÃO é cancelamento: não existe
     * estado terminal {@code REJECTED} para o item nesta entrega, e mapear rejeição
     * para {@code CANCELLED} misturaria "o aprovador disse não" com "o trabalho foi
     * cancelado". Se o plano decidir um estado próprio para item rejeitado, a
     * mudança é aqui, no {@link #ingestClose} e nos testes.
     */
    public ApprovalTask decideApproval(UUID itemId, UUID actorId, boolean approved, String reason) {
        if (itemId == null || actorId == null) {
            throw new IllegalArgumentException("item e ator são obrigatórios");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "reason", "required", "A decisão exige o motivo."));
        }
        boolean selfApproval = parameters.selfApproval(tenantId);
        Set<String> permissions = userPermissions.permissionsOf(tenantId, actorId);
        return inTx(c -> {
            WorkItem item = loadItem(c, itemId)
                    .orElseThrow(() -> new ApiException.NotFound("Item de trabalho não encontrado."));
            if (item.approvalTaskId() == null) {
                throw new ApiException.Conflict("not-an-approval",
                        "Este item não é uma aprovação: não há approval_task ligada a ele.");
            }
            ApprovalTask task = loadTask(c, item.approvalTaskId())
                    .orElseThrow(() -> new IllegalStateException("item com approval_task_id sem a tarefa correspondente"));
            Instant now = clock.instant();
            List<WorkDelegation> received = delegationsReceivedBy(c, actorId);
            ApprovalRule.Channel channel = ApprovalRule.assertCanDecide(
                    item, task, selfApproval, actorId, permissions, received,
                    currentPermissionsByDelegator(received), now);
            ApprovalStatus decided = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("approval_tasks") + " SET status = ?, decided_by = ?, decided_at = ?, "
                            + "decision_reason = ?, decision_channel = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ? AND status = 'PENDING'")) {
                ps.setString(1, decided.name());
                ps.setObject(2, actorId);
                ps.setTimestamp(3, Timestamp.from(now));
                ps.setString(4, reason);
                ps.setString(5, channel.name());
                ps.setObject(6, tenantId);
                ps.setObject(7, task.id());
                if (ps.executeUpdate() != 1) {
                    throw new ApiException.Conflict("approval-already-decided",
                            "Esta aprovação já foi decidida e não aceita nova decisão.");
                }
            }
            closeItem(c, item, WorkItemStatus.DONE, decided.name(), now);
            insertEvent(c, item.id(), approved ? "APPROVED" : "REJECTED", actorId,
                    Json.write(Map.of("channel", channel.name(), "reason", reason)));
            return new ApprovalTask(task.id(), tenantId, item.id(), task.requesterId(),
                    task.objectType(), task.objectId(), decided, actorId, now, reason, task.createdAt());
        });
    }

    // ------------------------------------------------------------------
    // Delegação temporária
    // ------------------------------------------------------------------

    /**
     * Registra a delegação: aplica a {@link DelegationRule} com o teto vigente de
     * {@code wf.delegation_max_days} e as permissões ATUAIS do delegante (porta da
     * P0.13). Sobreposição com outra delegação vigente do mesmo par é recusada (409)
     * — duas janelas para o mesmo delegado tornam o registro ambíguo; revogue a
     * anterior primeiro.
     */
    public WorkDelegation delegate(UUID delegatorId, UUID delegateId, Instant startsAt, Instant endsAt,
                                   Set<String> grantedPermissions, String reason) {
        if (delegatorId == null || delegateId == null) {
            throw new IllegalArgumentException("delegante e delegado são obrigatórios");
        }
        int maxDays = parameters.delegationMaxDays(tenantId);
        Set<String> delegatorPermissions = userPermissions.permissionsOf(tenantId, delegatorId);
        DelegationRule.assertCanCreate(delegatorId, delegateId, startsAt, endsAt,
                grantedPermissions, delegatorPermissions, maxDays, clock.instant());
        return inTx(c -> {
            for (WorkDelegation existing : delegationsSentBy(c, delegatorId)) {
                if (existing.delegateId().equals(delegateId) && existing.revokedAt() == null
                        && startsAt.isBefore(existing.endsAt()) && existing.startsAt().isBefore(endsAt)) {
                    throw new ApiException.Conflict("delegation-overlap",
                            "Já existe delegação vigente ou agendada para este delegado no período; revogue-a antes.");
                }
            }
            WorkDelegation delegation = new WorkDelegation(UUID.randomUUID(), tenantId, delegatorId, delegateId,
                    startsAt, endsAt, grantedPermissions == null ? Set.of() : grantedPermissions,
                    reason, delegatorId, clock.instant(), null, null);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("work_item_delegations") + " (" + DELEGATION_COLUMNS + ") VALUES ("
                            + "?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, NULL, NULL, ?)")) {
                ps.setObject(1, delegation.id());
                ps.setObject(2, tenantId);
                ps.setObject(3, delegatorId);
                ps.setObject(4, delegateId);
                ps.setTimestamp(5, Timestamp.from(startsAt));
                ps.setTimestamp(6, Timestamp.from(endsAt));
                ps.setString(7, Json.write(new ArrayList<>(delegation.grantedPermissions())));
                ps.setString(8, reason);
                ps.setObject(9, delegatorId);
                ps.setTimestamp(10, Timestamp.from(delegation.createdAt()));
                ps.executeUpdate();
            }
            return delegation;
        });
    }

    /** Revoga (delegante ou o próprio delegado, renunciando). Revogação é gravação, nunca exclusão. */
    public WorkDelegation revokeDelegation(UUID delegationId, UUID actorId) {
        if (delegationId == null || actorId == null) {
            throw new IllegalArgumentException("delegação e ator são obrigatórios");
        }
        return inTx(c -> {
            WorkDelegation delegation = loadDelegation(c, delegationId)
                    .orElseThrow(() -> new ApiException.NotFound("Delegação não encontrada."));
            if (!actorId.equals(delegation.delegatorId()) && !actorId.equals(delegation.delegateId())) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        "revokedBy", "not-a-party", "Só o delegante ou o delegado revogam esta delegação."));
            }
            if (delegation.revokedAt() != null) {
                throw new ApiException.Conflict("delegation-already-revoked", "Esta delegação já foi revogada.");
            }
            Instant now = clock.instant();
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("work_item_delegations") + " SET revoked_at = ?, revoked_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ? AND revoked_at IS NULL")) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setObject(2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, delegationId);
                if (ps.executeUpdate() != 1) {
                    throw new ApiException.Conflict("delegation-already-revoked", "Esta delegação já foi revogada.");
                }
            }
            return new WorkDelegation(delegation.id(), tenantId, delegation.delegatorId(), delegation.delegateId(),
                    delegation.startsAt(), delegation.endsAt(), delegation.grantedPermissions(), delegation.reason(),
                    delegation.createdBy(), delegation.createdAt(), now, actorId);
        });
    }

    public List<WorkDelegation> delegationsReceivedBy(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("usuário é obrigatório");
        }
        return inTx(c -> delegationsReceivedBy(c, userId));
    }

    public List<WorkDelegation> delegationsSentBy(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("usuário é obrigatório");
        }
        return inTx(c -> delegationsSentBy(c, userId));
    }

    // ------------------------------------------------------------------
    // Escalonamento por prazo
    // ------------------------------------------------------------------

    /**
     * Rodada de escalonamento (chamada pelo worker/agendador da integração): todo item
     * aberto com prazo cuja janela {@code wf.escalation_hours} venceu escala para o alvo
     * do {@link EscalationTargetResolver}, com registro na trilha. Sem alvo, o item fica
     * vencido e é devolvido em {@code skippedWithoutTargetIds} — a rodada seguinte tenta
     * de novo; nada escala para o vazio.
     */
    public EscalationRun escalateOverdue() {
        long hours = parameters.escalationHours(tenantId);
        return inTx(c -> {
            Instant now = clock.instant();
            List<UUID> escalated = new ArrayList<>();
            List<UUID> skipped = new ArrayList<>();
            for (WorkItem item : openItems(c)) {
                if (!EscalationRule.isEscalationDue(item, hours, now)) {
                    continue;
                }
                Optional<UUID> target = escalationTargets.escalationTargetFor(tenantId, item);
                if (target.isEmpty()) {
                    skipped.add(item.id());
                    continue;
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + t("work_items") + " SET status = 'ESCALATED', escalated_at = ?, "
                                + "escalation_count = escalation_count + 1, escalated_to_id = ?, updated_at = NOW()"
                                + " WHERE tenant_id = ? AND id = ? AND escalated_at IS NULL")) {
                    ps.setTimestamp(1, Timestamp.from(now));
                    ps.setObject(2, target.get());
                    ps.setObject(3, tenantId);
                    ps.setObject(4, item.id());
                    if (ps.executeUpdate() == 1) {
                        escalated.add(item.id());
                        insertEvent(c, item.id(), "ESCALATED", null,
                                Json.write(Map.of("escalatedTo", target.get().toString(),
                                        "escalationHours", hours)));
                    }
                }
            }
            return new EscalationRun(List.copyOf(escalated), List.copyOf(skipped));
        });
    }

    // ------------------------------------------------------------------
    // Persistência
    // ------------------------------------------------------------------

    private List<WorkItem> openItems(Connection c) throws SQLException {
        List<WorkItem> items = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + ITEM_COLUMNS + " FROM " + t("work_items")
                        + " WHERE tenant_id = ? AND status IN ('OPEN', 'IN_PROGRESS', 'ESCALATED')"
                        + " ORDER BY (due_at IS NULL), due_at ASC, created_at ASC LIMIT 500")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapItem(rs));
                }
            }
        }
        return items;
    }

    private Optional<WorkItem> loadItem(Connection c, UUID itemId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + ITEM_COLUMNS + " FROM " + t("work_items") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapItem(rs)) : Optional.empty();
            }
        }
    }

    private UUID findBySourceMessage(Connection c, UUID sourceMessageId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM " + t("work_items") + " WHERE tenant_id = ? AND source_message_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, sourceMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        }
    }

    private Optional<ApprovalTask> loadTask(Connection c, UUID taskId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + TASK_COLUMNS + " FROM " + t("approval_tasks") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ApprovalTask(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5),
                        rs.getObject(6, UUID.class), ApprovalStatus.valueOf(rs.getString(7)),
                        rs.getObject(8, UUID.class), instant(rs.getTimestamp(9)), rs.getString(10),
                        instant(rs.getTimestamp(11))));
            }
        }
    }

    private Optional<WorkDelegation> loadDelegation(Connection c, UUID delegationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + DELEGATION_COLUMNS + " FROM " + t("work_item_delegations")
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, delegationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapDelegation(rs)) : Optional.empty();
            }
        }
    }

    private List<WorkDelegation> delegationsReceivedBy(Connection c, UUID userId) throws SQLException {
        return delegationsBy(c, "delegate_id", userId);
    }

    private List<WorkDelegation> delegationsSentBy(Connection c, UUID userId) throws SQLException {
        return delegationsBy(c, "delegator_id", userId);
    }

    private List<WorkDelegation> delegationsBy(Connection c, String column, UUID userId) throws SQLException {
        List<WorkDelegation> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + DELEGATION_COLUMNS + " FROM " + t("work_item_delegations")
                        + " WHERE tenant_id = ? AND " + column + " = ? ORDER BY starts_at ASC")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapDelegation(rs));
                }
            }
        }
        return result;
    }

    private void insertItem(Connection c, WorkItem item) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + t("work_items") + " (" + ITEM_COLUMNS + ") VALUES ("
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, 0, NULL, ?)")) {
            ps.setObject(1, item.id());
            ps.setObject(2, tenantId);
            ps.setString(3, item.type().name());
            ps.setString(4, item.objectType());
            ps.setObject(5, item.objectId());
            ps.setString(6, item.originModule());
            ps.setObject(7, item.requesterId());
            ps.setObject(8, item.assigneeId());
            ps.setString(9, Json.write(uuidStrings(item.eligibleApproverIds())));
            ps.setString(10, item.requiredPermission());
            ps.setTimestamp(11, item.dueAt() == null ? null : Timestamp.from(item.dueAt()));
            ps.setString(12, item.status().name());
            ps.setString(13, item.recordAddress());
            ps.setString(14, item.sourceEventName());
            ps.setInt(15, item.sourceEventVersion());
            ps.setObject(16, item.sourceMessageId());
            ps.setObject(17, item.approvalTaskId());
            ps.setTimestamp(18, Timestamp.from(item.createdAt()));
            ps.executeUpdate();
        }
    }

    private void insertApprovalTask(Connection c, ApprovalTask task) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + t("approval_tasks") + " (" + TASK_COLUMNS + ", decision_channel) VALUES ("
                        + "?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?, NULL)")) {
            ps.setObject(1, task.id());
            ps.setObject(2, tenantId);
            ps.setObject(3, task.workItemId());
            ps.setObject(4, task.requesterId());
            ps.setString(5, task.objectType());
            ps.setObject(6, task.objectId());
            ps.setString(7, task.status().name());
            ps.setTimestamp(8, Timestamp.from(task.createdAt()));
            ps.executeUpdate();
        }
    }

    private void closeItem(Connection c, WorkItem item, WorkItemStatus closed, String closeReason, Instant now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE " + t("work_items") + " SET status = ?, closed_at = ?, close_reason = ?, updated_at = NOW()"
                        + " WHERE tenant_id = ? AND id = ? AND status IN ('OPEN', 'IN_PROGRESS', 'ESCALATED')")) {
            ps.setString(1, closed.name());
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setString(3, closeReason);
            ps.setObject(4, tenantId);
            ps.setObject(5, item.id());
            if (ps.executeUpdate() != 1) {
                throw new ApiException.Conflict("work-item-closed", "O item já foi encerrado.");
            }
        }
    }

    private void insertEvent(Connection c, UUID itemId, String eventType, UUID actorId, String detailJson)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + t("work_item_events") + " (id, tenant_id, work_item_id, event_type, actor_id, detail)"
                        + " VALUES (?, ?, ?, ?, ?, ?::jsonb)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, itemId);
            ps.setString(4, eventType);
            ps.setObject(5, actorId);
            ps.setString(6, detailJson);
            ps.executeUpdate();
        }
    }

    private WorkItem mapItem(ResultSet rs) throws SQLException {
        return new WorkItem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                WorkItemType.valueOf(rs.getString(3)), rs.getString(4), rs.getObject(5, UUID.class),
                rs.getString(6), rs.getObject(7, UUID.class), rs.getObject(8, UUID.class),
                uuidSet(rs.getString(9)), rs.getString(10), instant(rs.getTimestamp(11)),
                WorkItemStatus.valueOf(rs.getString(12)), rs.getString(13), rs.getString(14),
                rs.getInt(15), rs.getObject(16, UUID.class), rs.getObject(17, UUID.class),
                instant(rs.getTimestamp(23)), instant(rs.getTimestamp(19)), rs.getString(18),
                instant(rs.getTimestamp(20)), rs.getInt(21), rs.getObject(22, UUID.class));
    }

    private WorkDelegation mapDelegation(ResultSet rs) throws SQLException {
        return new WorkDelegation(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6)), stringSet(rs.getString(7)),
                rs.getString(8), rs.getObject(9, UUID.class), instant(rs.getTimestamp(12)),
                instant(rs.getTimestamp(10)), rs.getObject(11, UUID.class));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static List<String> uuidStrings(Set<UUID> ids) {
        List<String> result = new ArrayList<>();
        for (UUID id : ids) {
            result.add(id.toString());
        }
        return result;
    }

    private static Set<UUID> uuidSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        JsonNode node = Json.readTree(json);
        Set<UUID> result = new LinkedHashSet<>();
        for (JsonNode element : node) {
            result.add(UUID.fromString(element.asText()));
        }
        return Set.copyOf(result);
    }

    private static Set<String> stringSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        JsonNode node = Json.readTree(json);
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode element : node) {
            result.add(element.asText());
        }
        return Set.copyOf(result);
    }
}
