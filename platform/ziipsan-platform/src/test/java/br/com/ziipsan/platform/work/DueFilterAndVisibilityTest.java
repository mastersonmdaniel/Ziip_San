package br.com.ziipsan.platform.work;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filtro por prazo na data LOCAL do tenant (America/Sao_Paulo = UTC-3) e visibilidade
 * da caixa por usuário. Agora de referência: 2026-03-10T13:00:00Z = 10:00 locais de
 * 2026-03-10. Um item que vence às 23:00 locais de hoje é 2026-03-11T02:00:00Z — em UTC
 * já é "amanhã", para o usuário é HOJE.
 *
 * <p>Pós-revisão: {@code isVisibleTo} recebe também as permissões ATUAIS de cada
 * delegante (mapa) — correção A2; as chamadas antigas ganharam o mapa sem mudar
 * asserção (o item dos testes antigos é endereçado por NOME, caminho que não
 * depende de permissão), e há teste novo para o caminho por permissão.
 */
class DueFilterAndVisibilityTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final Instant NOW = Instant.parse("2026-03-10T13:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static WorkItem item(Instant dueAt, WorkItemStatus status) {
        return new WorkItem(UUID.randomUUID(), UUID.randomUUID(), WorkItemType.HELD_BILL,
                "BILL", UUID.randomUUID(), "billing", UUID.randomUUID(), OWNER, Set.of(), null,
                dueAt, status, "/billing/analysis-queue", "billing.held-bill.registered", 1, null, null,
                Instant.parse("2026-03-09T13:00:00Z"), status.isTerminal() ? NOW : null,
                status.isTerminal() ? "DONE" : null, null, 0, null);
    }

    private static WorkItem permissionItem(String permission) {
        return new WorkItem(UUID.randomUUID(), UUID.randomUUID(), WorkItemType.HELD_BILL,
                "BILL", UUID.randomUUID(), "billing", UUID.randomUUID(), null, Set.of(), permission,
                Instant.parse("2026-03-12T13:00:00Z"), WorkItemStatus.OPEN, "/billing/analysis-queue",
                "billing.held-bill.registered", 1, null, null,
                Instant.parse("2026-03-09T13:00:00Z"), null, null, null, 0, null);
    }

    @Test
    @DisplayName("filtro por prazo: vencido ontem é OVERDUE; 23:00 locais de hoje (02:00Z de amanhã) é DUE_TODAY; sem prazo só existe em ALL")
    void dueFiltersUseTenantLocalDate() {
        WorkItem overdue = item(Instant.parse("2026-03-09T13:00:00Z"), WorkItemStatus.OPEN);
        WorkItem dueTonightLocal = item(Instant.parse("2026-03-11T02:00:00Z"), WorkItemStatus.OPEN);
        WorkItem dueInFiveDays = item(Instant.parse("2026-03-15T13:00:00Z"), WorkItemStatus.OPEN);
        WorkItem dueInTenDays = item(Instant.parse("2026-03-20T13:00:00Z"), WorkItemStatus.OPEN);
        WorkItem noDue = item(null, WorkItemStatus.OPEN);
        WorkItem closedOverdue = item(Instant.parse("2026-03-09T13:00:00Z"), WorkItemStatus.DONE);

        assertThat(DueFilter.OVERDUE.matches(overdue, NOW, SP)).isTrue();
        assertThat(DueFilter.OVERDUE.matches(dueTonightLocal, NOW, SP)).isFalse();
        assertThat(DueFilter.OVERDUE.matches(closedOverdue, NOW, SP)).isFalse();

        assertThat(DueFilter.DUE_TODAY.matches(dueTonightLocal, NOW, SP)).isTrue();
        assertThat(DueFilter.DUE_TODAY.matches(dueInFiveDays, NOW, SP)).isFalse();
        assertThat(DueFilter.DUE_TODAY.matches(overdue, NOW, SP)).isFalse();

        assertThat(DueFilter.DUE_IN_7_DAYS.matches(dueTonightLocal, NOW, SP)).isTrue();
        assertThat(DueFilter.DUE_IN_7_DAYS.matches(dueInFiveDays, NOW, SP)).isTrue();
        assertThat(DueFilter.DUE_IN_7_DAYS.matches(dueInTenDays, NOW, SP)).isFalse();

        assertThat(DueFilter.ALL.matches(noDue, NOW, SP)).isTrue();
        assertThat(DueFilter.OVERDUE.matches(noDue, NOW, SP)).isFalse();
        assertThat(DueFilter.DUE_TODAY.matches(noDue, NOW, SP)).isFalse();
        assertThat(DueFilter.DUE_IN_7_DAYS.matches(noDue, NOW, SP)).isFalse();
    }

    @Test
    @DisplayName("CORREÇÃO m1 — fronteira do DUE_IN_7_DAYS em DATAS locais: hoje+6 dentro, hoje+7 dentro (até 23:59:59 locais), hoje+8 fora (desde 00:00 locais)")
    void dueIn7DaysLocalDateBoundary() {
        // Hoje local = 2026-03-10. +6 = 2026-03-16, +7 = 2026-03-17, +8 = 2026-03-18.
        // Instantes às 13:00Z = 10:00 locais do próprio dia.
        assertThat(DueFilter.DUE_IN_7_DAYS.matches(
                item(Instant.parse("2026-03-16T13:00:00Z"), WorkItemStatus.OPEN), NOW, SP)).isTrue();
        assertThat(DueFilter.DUE_IN_7_DAYS.matches(
                item(Instant.parse("2026-03-17T13:00:00Z"), WorkItemStatus.OPEN), NOW, SP)).isTrue();
        assertThat(DueFilter.DUE_IN_7_DAYS.matches(
                item(Instant.parse("2026-03-18T13:00:00Z"), WorkItemStatus.OPEN), NOW, SP)).isFalse();
        // A fronteira é a DATA local, não agora+7x24h: 23:59:59 locais de hoje+7
        // (2026-03-18T02:59:59Z, mais de 7x24h depois de agora) ainda está DENTRO;
        // 00:00:00 locais de hoje+8 (2026-03-18T03:00:00Z) já está FORA.
        assertThat(DueFilter.DUE_IN_7_DAYS.matches(
                item(Instant.parse("2026-03-18T02:59:59Z"), WorkItemStatus.OPEN), NOW, SP)).isTrue();
        assertThat(DueFilter.DUE_IN_7_DAYS.matches(
                item(Instant.parse("2026-03-18T03:00:00Z"), WorkItemStatus.OPEN), NOW, SP)).isFalse();
    }

    @Test
    @DisplayName("visibilidade: dono vê, estranho não; delegado vigente do dono vê; delegação expirada não mostra mais")
    void visibilityByOwnerAndDelegation() {
        WorkItem item = item(Instant.parse("2026-03-12T13:00:00Z"), WorkItemStatus.OPEN);
        assertThat(WorkItemVisibility.isVisibleTo(item, OWNER, Set.of(), List.of(), Map.of(), NOW)).isTrue();
        assertThat(WorkItemVisibility.isVisibleTo(item, OTHER, Set.of(), List.of(), Map.of(), NOW)).isFalse();

        WorkDelegation active = new WorkDelegation(UUID.randomUUID(), UUID.randomUUID(), OWNER, OTHER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                Set.of(), "férias", OWNER, NOW, null, null);
        // Item endereçado por NOME ao delegante: a visibilidade por delegação não
        // depende de conjunto de permissões (mapa vazio basta) — quem depende de
        // escopo efetivo é a DECISÃO (ApprovalRule/M1), não enxergar o item.
        assertThat(WorkItemVisibility.isVisibleTo(item, OTHER, Set.of(), List.of(active), Map.of(), NOW)).isTrue();

        WorkDelegation expired = new WorkDelegation(UUID.randomUUID(), UUID.randomUUID(), OWNER, OTHER,
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-10T00:00:00Z"),
                Set.of(), "férias", OWNER, NOW, null, null);
        // NOW = 2026-03-10T13:00Z, depois do fim 2026-03-10T00:00Z: expirada, não mostra.
        assertThat(WorkItemVisibility.isVisibleTo(item, OTHER, Set.of(), List.of(expired), Map.of(), NOW)).isFalse();

        // Item encerrado não aparece na caixa de ninguém.
        assertThat(WorkItemVisibility.isVisibleTo(
                item(Instant.parse("2026-03-12T13:00:00Z"), WorkItemStatus.DONE), OWNER, Set.of(), List.of(), Map.of(), NOW)).isFalse();
    }

    @Test
    @DisplayName("CORREÇÃO A2 na visibilidade: item por PERMISSÃO some da caixa do delegado quando o delegante PERDE a permissão; permissão ganha depois pelo delegante não aparece (foto limita)")
    void visibilityByDelegationFollowsDelegatorCurrentPermissions() {
        WorkItem item = permissionItem("billing:analyze");
        UUID delegator = UUID.randomUUID();
        WorkDelegation delegation = new WorkDelegation(UUID.randomUUID(), UUID.randomUUID(), delegator, OTHER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                Set.of("billing:analyze"), "férias", delegator, NOW, null, null);
        // Delegante ainda tem a permissão concedida: delegado enxerga por delegação.
        assertThat(WorkItemVisibility.isVisibleTo(item, OTHER, Set.of(), List.of(delegation),
                Map.of(delegator, Set.of("billing:analyze")), NOW)).isTrue();
        // Delegante perdeu a permissão: escopo efetivo vazio, o item some da caixa
        // do delegado — antes da correção, a foto grantedPermissions mantinha a
        // visibilidade sozinha.
        assertThat(WorkItemVisibility.isVisibleTo(item, OTHER, Set.of(), List.of(delegation),
                Map.of(delegator, Set.of("billing:read")), NOW)).isFalse();
        assertThat(WorkItemVisibility.isVisibleTo(item, OTHER, Set.of(), List.of(delegation),
                Map.of(), NOW)).isFalse();
        // Foto limita no outro sentido: delegação concedeu outra permissão e o
        // delegante ganhou "billing:analyze" DEPOIS — o delegado não enxerga.
        WorkDelegation otherScope = new WorkDelegation(UUID.randomUUID(), UUID.randomUUID(), delegator, OTHER,
                Instant.parse("2026-03-09T00:00:00Z"), Instant.parse("2026-03-20T00:00:00Z"),
                Set.of("billing:read"), "férias", delegator, NOW, null, null);
        assertThat(WorkItemVisibility.isVisibleTo(item, OTHER, Set.of(), List.of(otherScope),
                Map.of(delegator, Set.of("billing:read", "billing:analyze")), NOW)).isFalse();
    }

    @Test
    @DisplayName("contrato: item sem dono (sem responsável, sem elegíveis, sem permissão) nem se constrói")
    void itemWithoutOwnerDoesNotExist() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new WorkItem(
                UUID.randomUUID(), UUID.randomUUID(), WorkItemType.HELD_BILL, "BILL", UUID.randomUUID(),
                "billing", UUID.randomUUID(), null, Set.of(), null, null, WorkItemStatus.OPEN,
                "/billing/analysis-queue", "billing.held-bill.registered", 1, null, null,
                NOW, null, null, null, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem dono");
    }
}
