package br.com.ziipsan.platform.work;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O componente de aprovação (P2.28): segregação, elegibilidade e canal delegado.
 * IDs fixos por papel: REQUESTER pede, APPROVER aprova, STRANGER não tem relação.
 *
 * <p>Pós-revisão: a assinatura de {@code assertCanDecide} recebe também as
 * permissões ATUAIS de cada delegante (mapa) — é com elas que o canal delegado
 * calcula o escopo efetivo (concedidas ∩ delegante atual). Testes antigos foram
 * adaptados SÓ na assinatura (mapa vazio onde não há delegação em jogo), sem
 * mudar nenhuma asserção; a única asserção alterada por semântica é a de
 * {@code strangerNeedsDelegation} (correção M1, justificada no próprio teste).
 */
class ApprovalRuleTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID APPROVER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-03-10T15:00:00Z");
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    private static WorkItem approvalItem(Set<UUID> eligible, UUID assignee, String permission) {
        return new WorkItem(ITEM_ID, TENANT, WorkItemType.SERVICE_ORDER_APPROVAL, "SERVICE_ORDER",
                UUID.randomUUID(), "operations", REQUESTER, assignee, eligible, permission,
                Instant.parse("2026-03-12T15:00:00Z"), WorkItemStatus.OPEN, "/service-orders/1",
                "so.approval.requested", 1, null, TASK_ID, NOW, null, null, null, 0, null);
    }

    private static ApprovalTask pendingTask() {
        return new ApprovalTask(TASK_ID, TENANT, ITEM_ID, REQUESTER, "SERVICE_ORDER",
                UUID.randomUUID(), ApprovalStatus.PENDING, null, null, null, NOW);
    }

    private static WorkDelegation delegation(UUID delegator, UUID delegate, Set<String> granted) {
        return new WorkDelegation(UUID.randomUUID(), TENANT, delegator, delegate,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                granted, "cobertura", delegator, NOW, null, null);
    }

    @Test
    @DisplayName("REGRA DO ACEITE: quem solicita NÃO aprova — o solicitante é recusado com 422 self-approval mesmo sendo elegível nomeado")
    void requesterCannotApproveOwnRequest() {
        // Mesmo com o solicitante na lista de elegíveis (dado errado de origem), a segregação vence.
        WorkItem item = approvalItem(Set.of(REQUESTER, APPROVER), null, null);
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                REQUESTER, Set.of(), List.of(), Map.of(), NOW))
                .isInstanceOf(ApiException.Validation.class)
                .hasMessageContaining("Há campos inválidos");
    }

    @Test
    @DisplayName("wf.self_approval verdadeiro é catálogo adulterado: erro de estado, nunca licença para o solicitante aprovar")
    void selfApprovalTrueIsTamperedCatalog() {
        assertThatThrownBy(() -> ApprovalRule.assertSelfApprovalForbidden(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classe a, sempre falso");
        WorkItem item = approvalItem(Set.of(APPROVER), null, null);
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), true,
                REQUESTER, Set.of(), List.of(), Map.of(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("o registro decidido pelo solicitante nem se constrói: ApprovalTask com decidedBy == requester é inválido")
    void decidedTaskByRequesterDoesNotExist() {
        assertThatThrownBy(() -> new ApprovalTask(TASK_ID, TENANT, ITEM_ID, REQUESTER, "SERVICE_ORDER",
                UUID.randomUUID(), ApprovalStatus.APPROVED, REQUESTER, NOW, "ok", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quem solicitou não aprova");
    }

    @Test
    @DisplayName("aprovador elegível nomeado decide pelo canal DIRECT; responsável direto idem")
    void eligibleApproverDecidesDirectly() {
        assertThat(ApprovalRule.assertCanDecide(approvalItem(Set.of(APPROVER), null, null), pendingTask(), false,
                APPROVER, Set.of(), List.of(), Map.of(), NOW)).isEqualTo(ApprovalRule.Channel.DIRECT);
        assertThat(ApprovalRule.assertCanDecide(approvalItem(Set.of(), APPROVER, null), pendingTask(), false,
                APPROVER, Set.of(), List.of(), Map.of(), NOW)).isEqualTo(ApprovalRule.Channel.DIRECT);
    }

    @Test
    @DisplayName("item endereçado por permissão: portador da permissão decide; quem não a tem, não")
    void permissionAddressedItem() {
        WorkItem item = approvalItem(Set.of(), null, "so:approve");
        assertThat(ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of("so:approve"), List.of(), Map.of(), NOW)).isEqualTo(ApprovalRule.Channel.DIRECT);
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of("so:read"), List.of(), Map.of(), NOW))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("estranho sem delegação é recusado; com delegação vigente do elegível E escopo efetivo, decide DELEGATED; delegação de escopo VAZIO não decide mais (correção M1)")
    void strangerNeedsDelegation() {
        WorkItem item = approvalItem(Set.of(APPROVER), null, null);
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(), Map.of(), NOW))
                .isInstanceOf(ApiException.Validation.class);
        // JUSTIFICATIVA DO AJUSTE (correção M1, decisão do coordenador): a versão
        // anterior deste teste codificava que delegação com grantedPermissions
        // VAZIO habilitava o estranho a decidir item NOMEADO do delegante — era o
        // comportamento REPROVADO na revisão (escopo concedido ignorado para itens
        // endereçados por nome). A semântica nova: grantedPermissions é o teto de
        // TODA decisão delegada; escopo efetivo vazio não decide aprovação nenhuma.
        // Por isso a delegação abaixo concede "so:approve", que o delegante AINDA
        // tem (mapa de permissões atuais), e a de escopo vazio vira caso negativo.
        WorkDelegation delegation = delegation(APPROVER, STRANGER, Set.of("so:approve"));
        assertThat(ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(delegation), Map.of(APPROVER, Set.of("so:approve")), NOW))
                .isEqualTo(ApprovalRule.Channel.DELEGATED);
        WorkDelegation emptyScope = delegation(APPROVER, STRANGER, Set.of());
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(emptyScope), Map.of(APPROVER, Set.of("so:approve")), NOW))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("delegação NÃO lava segregação: o solicitante com delegação do elegível continua proibido de decidir o próprio pedido")
    void delegationDoesNotWashSelfApproval() {
        WorkItem item = approvalItem(Set.of(APPROVER), null, null);
        WorkDelegation delegation = new WorkDelegation(UUID.randomUUID(), TENANT, APPROVER, REQUESTER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                Set.of(), "cobertura", APPROVER, NOW, null, null);
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                REQUESTER, Set.of(), List.of(delegation), Map.of(), NOW))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("CORREÇÃO A1 (cenário do revisor): o SOLICITANTE delega a um terceiro e o terceiro tenta decidir o pedido dele — recusado, mesmo com escopo concedido e vigente no delegante")
    void requesterDelegationDoesNotWashSegregation() {
        // O solicitante consta como elegível nomeado do próprio item (o fato que,
        // antes da correção, tornava o delegante "elegível" e abria o canal).
        WorkItem item = approvalItem(Set.of(REQUESTER, APPROVER), null, null);
        // Delegação do SOLICITANTE para o terceiro, com escopo NÃO vazio e que o
        // delegante ainda tem — ou seja: nem M1 nem A2 salvam este caso; só a
        // recusa por delegatorId == requesterId (A1) fecha a lavagem. Antes da
        // correção, esta chamada devolvia DELEGATED e o record/CHECK do banco
        // aceitavam (decidedBy = terceiro ≠ solicitante).
        WorkDelegation fromRequester = delegation(REQUESTER, STRANGER, Set.of("so:approve"));
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(fromRequester), Map.of(REQUESTER, Set.of("so:approve")), NOW))
                .isInstanceOf(ApiException.Validation.class)
                .hasMessageContaining("Há campos inválidos");
        // E com escopo vazio (a forma literal do cenário do revisor), idem.
        WorkDelegation emptyFromRequester = delegation(REQUESTER, STRANGER, Set.of());
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(emptyFromRequester), Map.of(), NOW))
                .isInstanceOf(ApiException.Validation.class);
        // Controle: a MESMA delegação vinda do aprovador elegível (não solicitante)
        // habilita o terceiro — a recusa é pela identidade do delegante, não pelo canal.
        WorkDelegation fromApprover = delegation(APPROVER, STRANGER, Set.of("so:approve"));
        assertThat(ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(fromApprover), Map.of(APPROVER, Set.of("so:approve")), NOW))
                .isEqualTo(ApprovalRule.Channel.DELEGATED);
    }

    @Test
    @DisplayName("CORREÇÃO A2 na decisão: delegante PERDE a permissão após delegar → delegado NÃO decide mais (a interseção é com o delegante ATUAL, não com a foto)")
    void delegatedDecisionFollowsDelegatorCurrentPermissions() {
        WorkItem item = approvalItem(Set.of(), null, "so:approve");
        WorkDelegation delegation = delegation(APPROVER, STRANGER, Set.of("so:approve"));
        // Delegante ainda tem a permissão concedida: canal delegado aberto.
        assertThat(ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(delegation), Map.of(APPROVER, Set.of("so:approve")), NOW))
                .isEqualTo(ApprovalRule.Channel.DELEGATED);
        // Delegante perdeu a permissão: escopo efetivo vazio, decisão recusada —
        // antes da correção, a foto grantedPermissions sozinha mantinha o poder.
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(delegation), Map.of(APPROVER, Set.of("so:read")), NOW))
                .isInstanceOf(ApiException.Validation.class);
        // Delegante sem conjunto conhecido (ausente do mapa): nada é presumido.
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(delegation), Map.of(), NOW))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("CORREÇÃO A2 na decisão: delegante GANHA permissão nova após delegar → delegado NÃO ganha (a foto da concessão limita o escopo efetivo)")
    void delegatedDecisionDoesNotGainPermissionsDelegatorGotLater() {
        // O item exige a permissão NOVA, que o delegante só ganhou DEPOIS de delegar
        // concedendo apenas "so:approve". O conjunto ATUAL do delegante tem as duas;
        // o efetivo do delegado é a interseção com a foto = só "so:approve".
        WorkItem item = approvalItem(Set.of(), null, "so:approve-v2");
        WorkDelegation delegation = delegation(APPROVER, STRANGER, Set.of("so:approve"));
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(delegation),
                Map.of(APPROVER, Set.of("so:approve", "so:approve-v2")), NOW))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("CORREÇÃO M1: item COM requiredPermission declarada + endereçamento por nome — decisão delegada exige a permissão no escopo efetivo, não basta escopo qualquer")
    void delegatedDecisionRequiresDeclaredPermissionInEffectiveScope() {
        WorkItem item = approvalItem(Set.of(APPROVER), null, "so:approve");
        // Escopo efetivo não vazio, mas OUTRA permissão: não decide.
        WorkDelegation otherScope = delegation(APPROVER, STRANGER, Set.of("so:read"));
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(otherScope), Map.of(APPROVER, Set.of("so:read")), NOW))
                .isInstanceOf(ApiException.Validation.class);
        // Escopo efetivo com a permissão declarada: decide DELEGATED.
        WorkDelegation rightScope = delegation(APPROVER, STRANGER, Set.of("so:approve"));
        assertThat(ApprovalRule.assertCanDecide(item, pendingTask(), false,
                STRANGER, Set.of(), List.of(rightScope), Map.of(APPROVER, Set.of("so:approve")), NOW))
                .isEqualTo(ApprovalRule.Channel.DELEGATED);
    }

    @Test
    @DisplayName("tarefa já decidida não aceita nova decisão (409)")
    void alreadyDecidedTaskRefuses() {
        ApprovalTask decided = new ApprovalTask(TASK_ID, TENANT, ITEM_ID, REQUESTER, "SERVICE_ORDER",
                UUID.randomUUID(), ApprovalStatus.APPROVED, APPROVER, NOW, "conferido", NOW);
        assertThatThrownBy(() -> ApprovalRule.assertCanDecide(approvalItem(Set.of(APPROVER), null, null),
                decided, false, APPROVER, Set.of(), List.of(), Map.of(), NOW))
                .isInstanceOf(ApiException.Conflict.class);
    }
}
