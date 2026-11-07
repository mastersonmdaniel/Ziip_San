package br.com.ziipsan.platform.work;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O serviço real sobre JDBC falso (padrão da P3.36): o fake guarda as linhas que o
 * próprio serviço lê/escreve e conta os UPDATEs/INSERTs por tabela — ele não decide
 * regra nenhuma. Cenário-base: item de aprovação de OS com TASK pendente; APPROVER
 * é elegível nomeado; REQUESTER é o solicitante.
 *
 * <p>Pós-revisão (M2): a classe ganhou os testes de serviço que faltavam —
 * {@code ingestOpen} (incl. reenvio e a CORRIDA simulada pelo índice único),
 * {@code ingestClose}, {@code delegate}, {@code revokeDelegation} e
 * {@code escalateOverdue} (incl. item sem alvo em {@code skippedWithoutTargetIds})
 * — e os testes de decisão delegada das correções A1/A2 no caminho real do serviço
 * (permissões do delegante resolvidas pela porta a cada chamada).
 */
class WorkInboxServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID APPROVER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID ASSIGNEE = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-03-10T15:00:00Z");

    private static final WorkParameters PARAMETERS = new WorkParameters() {
        @Override public boolean selfApproval(UUID tenantId) { return false; }
        @Override public int delegationMaxDays(UUID tenantId) { return 30; }
        @Override public long escalationHours(UUID tenantId) { return 24; }
    };

    private static final WorkEventCatalog INGEST_CATALOG = (name, version) -> {
        if (version == 1 && name.equals("billing.rectification.approval-requested")) {
            return Optional.of(new WorkCatalogEvent(name, version, WorkCatalogEvent.Effect.WORK_ITEM_OPENED));
        }
        if (version == 1 && name.equals("billing.bill.rectified")) {
            return Optional.of(new WorkCatalogEvent(name, version, WorkCatalogEvent.Effect.WORK_ITEM_CLOSED));
        }
        return Optional.empty();
    };

    private static WorkInboxService service(FakeDb db) {
        return service(db, (name, version) -> Optional.empty(), (tenantId, userId) -> Set.of(),
                (tenantId, item) -> Optional.empty());
    }

    private static WorkInboxService service(FakeDb db, WorkEventCatalog catalog, UserPermissions permissions,
                                            EscalationTargetResolver targets) {
        return new WorkInboxService(db.dataSource(), "tenant_test", TENANT,
                catalog, PARAMETERS, permissions, targets,
                ZoneId.of("America/Sao_Paulo"), Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private static UserPermissions permissionsFrom(Map<UUID, Set<String>> byUser) {
        return (tenantId, userId) -> byUser.getOrDefault(userId, Set.of());
    }

    // ------------------------------------------------------------------
    // decideApproval (cenário-base + correções A1/A2 no caminho real)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("decisão do elegível: aprova a tarefa, encerra o item e grava os dois UPDATEs")
    void eligibleApproverDecidesAndClosesItem() {
        FakeDb db = FakeDb.standardApproval();
        ApprovalTask decided = service(db).decideApproval(ITEM_ID, APPROVER, true, "conferido com a OS");
        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.decidedBy()).isEqualTo(APPROVER);
        assertThat(db.updates).hasValue(2); // approval_tasks + work_items
        assertThat(db.inserts.get()).isGreaterThanOrEqualTo(1); // trilha work_item_events
    }

    @Test
    @DisplayName("REGRA DO ACEITE no serviço: o SOLICITANTE é recusado e NENHUM update acontece (a transação não grava decisão)")
    void requesterDecisionIsRefusedAndWritesNothing() {
        FakeDb db = FakeDb.standardApproval();
        assertThatThrownBy(() -> service(db).decideApproval(ITEM_ID, REQUESTER, true, "eu mesmo aprovo"))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(db.updates).hasValue(0);
    }

    @Test
    @DisplayName("rejeição também encerra: status REJECTED na tarefa — e, pela convenção DESTA entrega (m4), o item fecha DONE com closeReason REJECTED")
    void rejectionAlsoDecides() {
        FakeDb db = FakeDb.standardApproval();
        ApprovalTask decided = service(db).decideApproval(ITEM_ID, APPROVER, false, "documentação insuficiente");
        assertThat(decided.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(db.updates).hasValue(2);
        assertThat(db.lastCloseStatus).isEqualTo("DONE");
        assertThat(db.lastCloseReason).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("CORREÇÃO A1 no serviço (cenário do revisor): solicitante delega a terceiro com escopo vigente e o terceiro tenta decidir o pedido dele — recusado, zero updates")
    void requesterDelegationDoesNotDecideAtService() {
        FakeDb db = new FakeDb();
        // Item em que o SOLICITANTE consta como elegível nomeado (o fato que abria o canal).
        db.seedItem(FakeDb.itemRow(ITEM_ID, "SERVICE_ORDER_APPROVAL", REQUESTER, null,
                Json.write(List.of(REQUESTER.toString())), null,
                Instant.parse("2026-03-12T15:00:00Z"), "OPEN", TASK_ID, null, null));
        db.seedTask(FakeDb.taskRow(TASK_ID, ITEM_ID, REQUESTER, "PENDING"));
        db.seedDelegation(FakeDb.delegationRow(UUID.randomUUID(), REQUESTER, STRANGER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                "[\"so:approve\"]", null, null));
        WorkInboxService service = service(db, (name, version) -> Optional.empty(),
                permissionsFrom(Map.of(REQUESTER, Set.of("so:approve"))), (tenantId, item) -> Optional.empty());
        assertThatThrownBy(() -> service.decideApproval(ITEM_ID, STRANGER, true, "decido pelo solicitante"))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(db.updates).hasValue(0);
    }

    @Test
    @DisplayName("CORREÇÃO A2 no serviço: com o delegante AINDA detentor da permissão, o delegado decide e vê o item; quando o delegante PERDE a permissão, o delegado não decide (zero updates) e não vê mais o item")
    void delegatedDecisionAndVisibilityFollowDelegatorCurrentPermissions() {
        Map<UUID, Set<String>> permissions = new HashMap<>(Map.of(APPROVER, Set.of("so:approve")));

        FakeDb dbDecide = delegatedScenario();
        WorkInboxService deciding = service(dbDecide, (name, version) -> Optional.empty(),
                permissionsFrom(permissions), (tenantId, item) -> Optional.empty());
        ApprovalTask decided = deciding.decideApproval(ITEM_ID, STRANGER, true, "pelo delegante");
        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.decidedBy()).isEqualTo(STRANGER);
        assertThat(dbDecide.updates).hasValue(2);

        FakeDb dbVisible = delegatedScenario();
        WorkInboxService listing = service(dbVisible, (name, version) -> Optional.empty(),
                permissionsFrom(permissions), (tenantId, item) -> Optional.empty());
        assertThat(listing.listInbox(STRANGER, DueFilter.ALL, 50))
                .extracting(WorkItem::id).containsExactly(ITEM_ID);

        // O delegante PERDE a permissão depois de delegar: o poder do delegado some junto.
        permissions.put(APPROVER, Set.of());
        FakeDb dbLost = delegatedScenario();
        WorkInboxService lost = service(dbLost, (name, version) -> Optional.empty(),
                permissionsFrom(permissions), (tenantId, item) -> Optional.empty());
        assertThatThrownBy(() -> lost.decideApproval(ITEM_ID, STRANGER, true, "ainda decido"))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(dbLost.updates).hasValue(0);
        assertThat(lost.listInbox(STRANGER, DueFilter.ALL, 50)).isEmpty();
    }

    @Test
    @DisplayName("CORREÇÃO A2 no serviço: permissão que o delegante GANHA depois da concessão não chega ao delegado (a foto limita) — decisão recusada, zero updates")
    void delegatedDecisionDoesNotGainLaterPermissionsAtService() {
        FakeDb db = new FakeDb();
        db.seedItem(FakeDb.itemRow(ITEM_ID, "SERVICE_ORDER_APPROVAL", REQUESTER, null,
                "[]", "so:approve-v2",
                Instant.parse("2026-03-12T15:00:00Z"), "OPEN", TASK_ID, null, null));
        db.seedTask(FakeDb.taskRow(TASK_ID, ITEM_ID, REQUESTER, "PENDING"));
        db.seedDelegation(FakeDb.delegationRow(UUID.randomUUID(), APPROVER, STRANGER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                "[\"so:approve\"]", null, null));
        WorkInboxService service = service(db, (name, version) -> Optional.empty(),
                permissionsFrom(Map.of(APPROVER, Set.of("so:approve", "so:approve-v2"))),
                (tenantId, item) -> Optional.empty());
        assertThatThrownBy(() -> service.decideApproval(ITEM_ID, STRANGER, true, "ganhei a nova"))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(db.updates).hasValue(0);
    }

    /** Item por permissão "so:approve" + tarefa pendente + delegação APPROVER→STRANGER concedendo "so:approve". */
    private static FakeDb delegatedScenario() {
        FakeDb db = new FakeDb();
        db.seedItem(FakeDb.itemRow(ITEM_ID, "SERVICE_ORDER_APPROVAL", REQUESTER, null,
                "[]", "so:approve",
                Instant.parse("2026-03-12T15:00:00Z"), "OPEN", TASK_ID, null, null));
        db.seedTask(FakeDb.taskRow(TASK_ID, ITEM_ID, REQUESTER, "PENDING"));
        db.seedDelegation(FakeDb.delegationRow(UUID.randomUUID(), APPROVER, STRANGER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                "[\"so:approve\"]", null, null));
        return db;
    }

    // ------------------------------------------------------------------
    // ingestOpen / ingestClose (correção M2)
    // ------------------------------------------------------------------

    private static WorkOutboxMessage openMessage(UUID messageId, boolean approval) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventVersion", 1);
        payload.put("workItemType", "BILL_RECTIFICATION_APPROVAL");
        payload.put("objectType", "BILL");
        payload.put("objectId", OBJECT_ID.toString());
        payload.put("originModule", "billing");
        payload.put("requesterId", REQUESTER.toString());
        payload.put("eligibleApproverIds", List.of(APPROVER.toString()));
        payload.put("dueAt", "2026-03-12T15:00:00Z");
        payload.put("recordAddress", "/billing/bills/" + OBJECT_ID);
        payload.put("approval", approval);
        return new WorkOutboxMessage(messageId, TENANT, "billing.rectification.approval-requested",
                Json.write(payload), NOW);
    }

    private static WorkOutboxMessage closeMessage(UUID itemId, String closeReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventVersion", 1);
        payload.put("workItemId", itemId.toString());
        payload.put("closeReason", closeReason);
        return new WorkOutboxMessage(UUID.randomUUID(), TENANT, "billing.bill.rectified",
                Json.write(payload), NOW);
    }

    @Test
    @DisplayName("ingestOpen: abre o item e a approval_task, grava a trilha e devolve o id gravado")
    void ingestOpenCreatesItemTaskAndEvent() {
        FakeDb db = new FakeDb();
        WorkInboxService service = service(db, INGEST_CATALOG, (t, u) -> Set.of(), (t, i) -> Optional.empty());
        UUID messageId = UUID.randomUUID();
        UUID itemId = service.ingestOpen(openMessage(messageId, true));
        assertThat(itemId).isNotNull();
        assertThat(db.itemIdBySourceMessage).containsEntry(messageId, itemId);
        assertThat(db.itemInserts).hasValue(1);
        assertThat(db.taskInserts).hasValue(1);
        assertThat(db.eventInserts).hasValue(1);
    }

    @Test
    @DisplayName("ingestOpen: REENVIO da mesma mensagem devolve o item existente sem gravar nada de novo (idempotência sequencial)")
    void ingestOpenResendReturnsExisting() {
        FakeDb db = new FakeDb();
        WorkInboxService service = service(db, INGEST_CATALOG, (t, u) -> Set.of(), (t, i) -> Optional.empty());
        WorkOutboxMessage message = openMessage(UUID.randomUUID(), true);
        UUID first = service.ingestOpen(message);
        UUID second = service.ingestOpen(message);
        assertThat(second).isEqualTo(first);
        assertThat(db.itemInserts).hasValue(1);
        assertThat(db.taskInserts).hasValue(1);
        assertThat(db.eventInserts).hasValue(1);
    }

    @Test
    @DisplayName("ingestOpen: CORRIDA simulada — a gravação bate no índice único uq_work_items_source_message e a ingestão relê e devolve o item do vencedor, sem erro")
    void ingestOpenRaceReturnsWinnerItem() {
        FakeDb db = new FakeDb();
        UUID winner = UUID.randomUUID();
        db.raceArmed = true;
        db.raceWinnerId = winner;
        WorkInboxService service = service(db, INGEST_CATALOG, (t, u) -> Set.of(), (t, i) -> Optional.empty());
        UUID result = service.ingestOpen(openMessage(UUID.randomUUID(), false));
        assertThat(result).isEqualTo(winner);
        assertThat(db.itemInserts).hasValue(0); // a NOSSA gravação perdeu; nada duplicou
    }

    @Test
    @DisplayName("ingestClose: encerra item aberto — COMPLETED vira DONE pela convenção desta entrega (m4) e devolve true")
    void ingestCloseClosesOpenItem() {
        FakeDb db = new FakeDb();
        db.seedItem(FakeDb.itemRow(ITEM_ID, "HELD_BILL", REQUESTER, ASSIGNEE, "[]", null,
                Instant.parse("2026-03-12T15:00:00Z"), "OPEN", null, null, null));
        WorkInboxService service = service(db, INGEST_CATALOG, (t, u) -> Set.of(), (t, i) -> Optional.empty());
        assertThat(service.ingestClose(closeMessage(ITEM_ID, "COMPLETED"))).isTrue();
        assertThat(db.lastCloseStatus).isEqualTo("DONE");
        assertThat(db.lastCloseReason).isEqualTo("COMPLETED");
        assertThat(db.updates).hasValue(1);
        assertThat(db.eventInserts).hasValue(1);
    }

    @Test
    @DisplayName("ingestClose: closeReason CANCELLED é o único que vira CANCELLED (convenção m4)")
    void ingestCloseCancelledMapsToCancelled() {
        FakeDb db = new FakeDb();
        db.seedItem(FakeDb.itemRow(ITEM_ID, "HELD_BILL", REQUESTER, ASSIGNEE, "[]", null,
                Instant.parse("2026-03-12T15:00:00Z"), "OPEN", null, null, null));
        WorkInboxService service = service(db, INGEST_CATALOG, (t, u) -> Set.of(), (t, i) -> Optional.empty());
        assertThat(service.ingestClose(closeMessage(ITEM_ID, "CANCELLED"))).isTrue();
        assertThat(db.lastCloseStatus).isEqualTo("CANCELLED");
        assertThat(db.lastCloseReason).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("ingestClose: item JÁ encerrado é no-op de reentrega — devolve false e não grava nada")
    void ingestCloseAlreadyClosedIsNoOp() {
        FakeDb db = new FakeDb();
        db.seedItem(FakeDb.itemRow(ITEM_ID, "HELD_BILL", REQUESTER, ASSIGNEE, "[]", null,
                Instant.parse("2026-03-12T15:00:00Z"), "DONE", null, "COMPLETED", NOW));
        WorkInboxService service = service(db, INGEST_CATALOG, (t, u) -> Set.of(), (t, i) -> Optional.empty());
        assertThat(service.ingestClose(closeMessage(ITEM_ID, "COMPLETED"))).isFalse();
        assertThat(db.updates).hasValue(0);
    }

    @Test
    @DisplayName("ingestClose: item com aprovação PENDENTE não encerra por evento (409) — encerra pela decisão")
    void ingestCloseWithPendingApprovalRefuses() {
        FakeDb db = FakeDb.standardApproval();
        WorkInboxService service = service(db, INGEST_CATALOG, (t, u) -> Set.of(), (t, i) -> Optional.empty());
        assertThatThrownBy(() -> service.ingestClose(closeMessage(ITEM_ID, "COMPLETED")))
                .isInstanceOf(ApiException.Conflict.class);
        assertThat(db.updates).hasValue(0);
    }

    // ------------------------------------------------------------------
    // delegate / revokeDelegation (correção M2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("delegate: registra a delegação dentro do teto e do subconjunto do delegante (a linha É o registro)")
    void delegateCreatesDelegation() {
        FakeDb db = new FakeDb();
        WorkInboxService service = service(db, (n, v) -> Optional.empty(),
                permissionsFrom(Map.of(APPROVER, Set.of("so:approve", "so:read"))), (t, i) -> Optional.empty());
        WorkDelegation created = service.delegate(APPROVER, STRANGER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                Set.of("so:approve"), "férias do aprovador");
        assertThat(created.delegatorId()).isEqualTo(APPROVER);
        assertThat(created.delegateId()).isEqualTo(STRANGER);
        assertThat(created.grantedPermissions()).containsExactly("so:approve");
        assertThat(created.revokedAt()).isNull();
        assertThat(db.delegationInserts).hasValue(1);
        assertThat(db.delegationRows).hasSize(1);
    }

    @Test
    @DisplayName("delegate: sobreposição com delegação vigente do mesmo par é recusada (409) e nada grava")
    void delegateOverlapRefuses() {
        FakeDb db = new FakeDb();
        db.seedDelegation(FakeDb.delegationRow(UUID.randomUUID(), APPROVER, STRANGER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                "[\"so:approve\"]", null, null));
        WorkInboxService service = service(db, (n, v) -> Optional.empty(),
                permissionsFrom(Map.of(APPROVER, Set.of("so:approve"))), (t, i) -> Optional.empty());
        assertThatThrownBy(() -> service.delegate(APPROVER, STRANGER,
                Instant.parse("2026-03-15T00:00:00Z"), Instant.parse("2026-03-25T00:00:00Z"),
                Set.of("so:approve"), "outra cobertura"))
                .isInstanceOf(ApiException.Conflict.class);
        assertThat(db.delegationInserts).hasValue(0);
    }

    @Test
    @DisplayName("delegate: conceder além da permissão do delegante é recusado pela DelegationRule antes de qualquer gravação")
    void delegateBeyondDelegatorRefuses() {
        FakeDb db = new FakeDb();
        WorkInboxService service = service(db, (n, v) -> Optional.empty(),
                permissionsFrom(Map.of(APPROVER, Set.of("so:approve"))), (t, i) -> Optional.empty());
        assertThatThrownBy(() -> service.delegate(APPROVER, STRANGER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                Set.of("billing:rectify"), "cobertura"))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(db.delegationInserts).hasValue(0);
    }

    @Test
    @DisplayName("revokeDelegation: o delegante revoga e a revogação fica gravada (revokedAt/revokedBy), sem apagar a linha")
    void revokeDelegationByDelegator() {
        FakeDb db = new FakeDb();
        UUID delegationId = UUID.randomUUID();
        db.seedDelegation(FakeDb.delegationRow(delegationId, APPROVER, STRANGER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                "[\"so:approve\"]", null, null));
        WorkDelegation revoked = service(db).revokeDelegation(delegationId, APPROVER);
        assertThat(revoked.revokedAt()).isEqualTo(NOW);
        assertThat(revoked.revokedBy()).isEqualTo(APPROVER);
        assertThat(db.updates).hasValue(1);
        assertThat(db.delegationRows).hasSize(1); // revogar grava; não apaga
    }

    @Test
    @DisplayName("revokeDelegation: quem não é parte (nem delegante, nem delegado) não revoga")
    void revokeDelegationByStrangerRefuses() {
        FakeDb db = new FakeDb();
        UUID delegationId = UUID.randomUUID();
        db.seedDelegation(FakeDb.delegationRow(delegationId, APPROVER, STRANGER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                "[\"so:approve\"]", null, null));
        assertThatThrownBy(() -> service(db).revokeDelegation(delegationId, UUID.randomUUID()))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(db.updates).hasValue(0);
    }

    @Test
    @DisplayName("revokeDelegation: delegação já revogada recusa nova revogação (409)")
    void revokeAlreadyRevokedRefuses() {
        FakeDb db = new FakeDb();
        UUID delegationId = UUID.randomUUID();
        db.seedDelegation(FakeDb.delegationRow(delegationId, APPROVER, STRANGER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                "[\"so:approve\"]", Instant.parse("2026-03-09T12:00:00Z"), APPROVER));
        assertThatThrownBy(() -> service(db).revokeDelegation(delegationId, APPROVER))
                .isInstanceOf(ApiException.Conflict.class);
        assertThat(db.updates).hasValue(0);
    }

    // ------------------------------------------------------------------
    // escalateOverdue (correção M2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("escalateOverdue: vencido com alvo escala; vencido SEM alvo volta em skippedWithoutTargetIds e não escala; item dentro do prazo não aparece em nenhuma lista")
    void escalateOverdueEscalatesAndSkipsWithoutTarget() {
        FakeDb db = new FakeDb();
        UUID withTarget = UUID.randomUUID();
        UUID withoutTarget = UUID.randomUUID();
        UUID notDue = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        // Vencimento 2026-03-09T15:00Z + 24h = 2026-03-10T15:00Z = NOW: devido.
        db.seedItem(FakeDb.itemRow(withTarget, "PROTOCOL_DUE", REQUESTER, ASSIGNEE, "[]", null,
                Instant.parse("2026-03-09T15:00:00Z"), "OPEN", null, null, null));
        db.seedItem(FakeDb.itemRow(withoutTarget, "PROTOCOL_DUE", REQUESTER, ASSIGNEE, "[]", null,
                Instant.parse("2026-03-09T15:00:00Z"), "OPEN", null, null, null));
        db.seedItem(FakeDb.itemRow(notDue, "PROTOCOL_DUE", REQUESTER, ASSIGNEE, "[]", null,
                Instant.parse("2026-03-12T15:00:00Z"), "OPEN", null, null, null));
        WorkInboxService service = service(db, (n, v) -> Optional.empty(), (t, u) -> Set.of(),
                (tenantId, item) -> item.id().equals(withTarget) ? Optional.of(target) : Optional.empty());
        WorkInboxService.EscalationRun run = service.escalateOverdue();
        assertThat(run.escalatedItemIds()).containsExactly(withTarget);
        assertThat(run.skippedWithoutTargetIds()).containsExactly(withoutTarget);
        assertThat(db.updates).hasValue(1); // só o item com alvo foi atualizado
        assertThat(db.eventInserts).hasValue(1);
    }

    /**
     * JDBC falso com estado: guarda linhas de work_items/approval_tasks/
     * work_item_delegations em memória, responde às consultas do serviço a partir
     * delas e aplica os UPDATEs/INSERTs nelas — sem decidir regra nenhuma.
     * A corrida do {@code ingestOpen} é simulada por {@code raceArmed}: a primeira
     * tentativa de INSERT em work_items lança a violação do índice único e, a
     * partir daí, a leitura por source_message_id "enxerga" o item do vencedor.
     */
    private static final class FakeDb {
        final AtomicInteger updates = new AtomicInteger();
        final AtomicInteger inserts = new AtomicInteger();
        final AtomicInteger itemInserts = new AtomicInteger();
        final AtomicInteger taskInserts = new AtomicInteger();
        final AtomicInteger delegationInserts = new AtomicInteger();
        final AtomicInteger eventInserts = new AtomicInteger();
        final Map<UUID, Object[]> itemsById = new LinkedHashMap<>();
        final Map<UUID, Object[]> tasksById = new LinkedHashMap<>();
        final List<Object[]> delegationRows = new ArrayList<>();
        final Map<UUID, UUID> itemIdBySourceMessage = new HashMap<>();
        volatile String lastCloseStatus;
        volatile String lastCloseReason;
        boolean raceArmed;
        boolean raceFailed;
        UUID raceWinnerId;

        static FakeDb standardApproval() {
            FakeDb db = new FakeDb();
            db.seedItem(itemRow(ITEM_ID, "SERVICE_ORDER_APPROVAL", REQUESTER, null,
                    Json.write(List.of(APPROVER.toString())), null,
                    Instant.parse("2026-03-12T15:00:00Z"), "OPEN", TASK_ID, null, null));
            db.seedTask(taskRow(TASK_ID, ITEM_ID, REQUESTER, "PENDING"));
            return db;
        }

        /** Linha de work_items na ordem de ITEM_COLUMNS/mapItem (23 colunas). */
        static Object[] itemRow(UUID id, String type, UUID requester, UUID assignee, String eligibleJson,
                                String requiredPermission, Instant dueAt, String status, UUID approvalTaskId,
                                String closeReason, Instant closedAt) {
            return new Object[]{id, TENANT, type, "SERVICE_ORDER", OBJECT_ID, "operations", requester, assignee,
                    eligibleJson, requiredPermission, dueAt == null ? null : Timestamp.from(dueAt), status,
                    "/service-orders/1", "so.approval.requested", 1, null, approvalTaskId,
                    closeReason, closedAt == null ? null : Timestamp.from(closedAt), null, 0, null,
                    Timestamp.from(NOW)};
        }

        /** Linha de approval_tasks na ordem de TASK_COLUMNS/loadTask (11 colunas). */
        static Object[] taskRow(UUID id, UUID itemId, UUID requester, String status) {
            boolean pending = "PENDING".equals(status);
            return new Object[]{id, TENANT, itemId, requester, "SERVICE_ORDER", OBJECT_ID, status,
                    pending ? null : APPROVER, pending ? null : Timestamp.from(NOW),
                    pending ? null : "motivo", Timestamp.from(NOW)};
        }

        /** Linha de work_item_delegations na ordem de DELEGATION_COLUMNS/mapDelegation (12 colunas). */
        static Object[] delegationRow(UUID id, UUID delegator, UUID delegate, Instant startsAt, Instant endsAt,
                                      String grantedJson, Instant revokedAt, UUID revokedBy) {
            return new Object[]{id, TENANT, delegator, delegate, Timestamp.from(startsAt), Timestamp.from(endsAt),
                    grantedJson, "cobertura", delegator,
                    revokedAt == null ? null : Timestamp.from(revokedAt), revokedBy, Timestamp.from(NOW)};
        }

        void seedItem(Object[] row) {
            itemsById.put((UUID) row[0], row);
        }

        void seedTask(Object[] row) {
            tasksById.put((UUID) row[0], row);
        }

        void seedDelegation(Object[] row) {
            delegationRows.add(row);
        }

        DataSource dataSource() {
            return proxy(DataSource.class, (p, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    return connection();
                }
                return padrao(method.getReturnType());
            });
        }

        private Connection connection() {
            return proxy(Connection.class, (p, method, args) -> {
                if (method.getName().equals("prepareStatement")) {
                    return statement((String) args[0]);
                }
                return padrao(method.getReturnType());
            });
        }

        private PreparedStatement statement(String sql) {
            Map<Integer, Object> params = new HashMap<>();
            return proxy(PreparedStatement.class, (p, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer index) {
                    params.put(index, args[1]);
                    return null;
                }
                if (name.equals("executeQuery")) {
                    return resultSet(rowsFor(sql, params));
                }
                if (name.equals("executeUpdate")) {
                    return executeUpdate(sql, params);
                }
                return padrao(method.getReturnType());
            });
        }

        private List<Object[]> rowsFor(String sql, Map<Integer, Object> params) {
            if (sql.contains("set_config")) {
                return List.of();
            }
            if (sql.contains("work_item_delegations")) {
                // O despacho olha SÓ o WHERE: a lista de colunas (DELEGATION_COLUMNS)
                // contém delegator_id E delegate_id ao mesmo tempo e não pode decidir.
                String where = sql.substring(sql.indexOf(" WHERE "));
                List<Object[]> rows = new ArrayList<>();
                for (Object[] row : delegationRows) {
                    boolean match;
                    if (where.contains("AND id = ?")) {
                        match = row[0].equals(params.get(2));
                    } else if (where.contains("delegator_id")) {
                        match = row[2].equals(params.get(2));
                    } else if (where.contains("delegate_id")) {
                        match = row[3].equals(params.get(2));
                    } else {
                        match = true;
                    }
                    if (match) {
                        rows.add(row);
                    }
                }
                return rows;
            }
            if (sql.contains("approval_tasks")) {
                Object[] row = tasksById.get(params.get(2));
                return row == null ? List.of() : List.<Object[]>of(row);
            }
            if (sql.contains("work_items")) {
                if (sql.contains("SELECT id FROM")) {
                    if (raceFailed && raceWinnerId != null) {
                        return List.<Object[]>of(new Object[]{raceWinnerId});
                    }
                    UUID found = itemIdBySourceMessage.get(params.get(2));
                    return found == null ? List.of() : List.<Object[]>of(new Object[]{found});
                }
                if (sql.contains("status IN")) {
                    List<Object[]> open = new ArrayList<>();
                    for (Object[] row : itemsById.values()) {
                        if (Set.of("OPEN", "IN_PROGRESS", "ESCALATED").contains(row[11])) {
                            open.add(row);
                        }
                    }
                    return open;
                }
                Object[] row = itemsById.get(params.get(2));
                return row == null ? List.of() : List.<Object[]>of(row);
            }
            throw new IllegalStateException("SQL inesperado no fake: " + sql);
        }

        private int executeUpdate(String sql, Map<Integer, Object> params) throws SQLException {
            if (sql.contains("INSERT INTO")) {
                inserts.incrementAndGet();
                if (sql.contains("work_item_delegations")) {
                    delegationInserts.incrementAndGet();
                    delegationRows.add(new Object[]{params.get(1), params.get(2), params.get(3), params.get(4),
                            params.get(5), params.get(6), params.get(7), params.get(8), params.get(9),
                            null, null, params.get(10)});
                    return 1;
                }
                if (sql.contains("approval_tasks")) {
                    taskInserts.incrementAndGet();
                    Object[] row = new Object[]{params.get(1), params.get(2), params.get(3), params.get(4),
                            params.get(5), params.get(6), params.get(7), null, null, null, params.get(8)};
                    tasksById.put((UUID) params.get(1), row);
                    return 1;
                }
                if (sql.contains("work_item_events")) {
                    eventInserts.incrementAndGet();
                    return 1;
                }
                if (sql.contains("work_items")) {
                    if (raceArmed && !raceFailed) {
                        raceFailed = true;
                        throw new SQLException(
                                "duplicate key value violates unique constraint \"uq_work_items_source_message\"",
                                "23505");
                    }
                    itemInserts.incrementAndGet();
                    Object[] row = new Object[]{params.get(1), params.get(2), params.get(3), params.get(4),
                            params.get(5), params.get(6), params.get(7), params.get(8), params.get(9),
                            params.get(10), params.get(11), params.get(12), params.get(13), params.get(14),
                            params.get(15), params.get(16), params.get(17), null, null, null, 0, null,
                            params.get(18)};
                    itemsById.put((UUID) params.get(1), row);
                    if (params.get(16) != null) {
                        itemIdBySourceMessage.put((UUID) params.get(16), (UUID) params.get(1));
                    }
                    return 1;
                }
            }
            if (sql.contains("UPDATE")) {
                updates.incrementAndGet();
                if (sql.contains("approval_tasks")) {
                    Object[] row = tasksById.get(params.get(7));
                    if (row != null) {
                        row[6] = params.get(1);
                        row[7] = params.get(2);
                        row[8] = params.get(3);
                        row[9] = params.get(4);
                    }
                    return 1;
                }
                if (sql.contains("work_item_delegations")) {
                    for (Object[] row : delegationRows) {
                        if (row[0].equals(params.get(4))) {
                            row[9] = params.get(1);
                            row[10] = params.get(2);
                        }
                    }
                    return 1;
                }
                if (sql.contains("work_items")) {
                    Object[] row;
                    if (sql.contains("escalated_at")) {
                        row = itemsById.get(params.get(4));
                        if (row != null) {
                            row[11] = "ESCALATED";
                            row[19] = params.get(1);
                            row[20] = ((Integer) row[20]) + 1;
                            row[21] = params.get(2);
                        }
                        return 1;
                    }
                    if (sql.contains("SET status = 'IN_PROGRESS'")) {
                        // Só o SET decide: o WHERE do closeItem também cita IN_PROGRESS.
                        row = itemsById.get(params.get(2));
                        if (row != null) {
                            row[11] = "IN_PROGRESS";
                        }
                        return 1;
                    }
                    lastCloseStatus = (String) params.get(1);
                    lastCloseReason = (String) params.get(3);
                    row = itemsById.get(params.get(5));
                    if (row != null) {
                        row[11] = lastCloseStatus;
                        row[17] = lastCloseReason;
                        row[18] = params.get(2);
                    }
                    return 1;
                }
            }
            throw new IllegalStateException("SQL inesperado no fake: " + sql);
        }

        private ResultSet resultSet(List<Object[]> rows) {
            int[] cursor = {-1};
            return proxy(ResultSet.class, (p, method, args) -> {
                String name = method.getName();
                switch (name) {
                    case "next" -> {
                        cursor[0]++;
                        return cursor[0] < rows.size();
                    }
                    case "getObject", "getString", "getTimestamp" -> {
                        return rows.get(cursor[0])[(Integer) args[0] - 1];
                    }
                    case "getInt", "getLong" -> {
                        return rows.get(cursor[0])[(Integer) args[0] - 1];
                    }
                    default -> {
                        return padrao(method.getReturnType());
                    }
                }
            });
        }

        private static <T> T proxy(Class<T> iface, InvocationHandler handler) {
            return iface.cast(Proxy.newProxyInstance(FakeDb.class.getClassLoader(), new Class<?>[]{iface}, handler));
        }

        private static Object padrao(Class<?> tipo) {
            if (tipo == boolean.class) {
                return false;
            }
            if (tipo == int.class) {
                return 0;
            }
            if (tipo == long.class) {
                return 0L;
            }
            if (tipo == double.class) {
                return 0d;
            }
            if (tipo == float.class) {
                return 0f;
            }
            if (tipo == short.class) {
                return (short) 0;
            }
            if (tipo == byte.class) {
                return (byte) 0;
            }
            if (tipo == char.class) {
                return (char) 0;
            }
            return null;
        }
    }
}
