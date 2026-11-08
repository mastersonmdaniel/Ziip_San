package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.platform.work.WorkDelegation;
import br.com.ziipsan.platform.work.WorkItem;
import br.com.ziipsan.platform.work.WorkItemStatus;
import br.com.ziipsan.platform.work.WorkItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite: "o mesmo filtro vale para a caixa de minhas tarefas da P2.28,
 * inclusive para item delegado (teste: item da localidade B não aparece a
 * quem só tem escopo na A)".
 */
class ScopedWorkVisibilityTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DELEGATOR = UUID.randomUUID();
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID LOC_A = UUID.randomUUID();
    private static final UUID LOC_B = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static WorkItem itemAddressedTo(UUID assignee) {
        return new WorkItem(UUID.randomUUID(), TENANT, WorkItemType.SERVICE_ORDER_APPROVAL,
                "SERVICE_ORDER", UUID.randomUUID(), "core", REQUESTER, assignee,
                Set.of(), null, null, WorkItemStatus.OPEN, "/ordens/1",
                "service-order.approval-requested", 1, null, null, NOW, null, null, null, 0, null);
    }

    private static WorkDelegation activeDelegation() {
        return new WorkDelegation(UUID.randomUUID(), TENANT, DELEGATOR, VIEWER,
                NOW.minusSeconds(3600), NOW.plusSeconds(3600), Set.of(),
                "Cobertura de férias.", DELEGATOR, NOW, null, null);
    }

    private static EffectiveScope scopeA() {
        return new EffectiveScope(false, Set.of(), Set.of(LOC_A), Set.of(), Set.of());
    }

    @Test
    @DisplayName("item DELEGADO da localidade B não aparece a quem só tem escopo na A; o da A aparece")
    void delegatedItemFilteredByViewerScope() {
        WorkItem item = itemAddressedTo(DELEGATOR);
        List<WorkDelegation> delegations = List.of(activeDelegation());
        // Sem o filtro de escopo, a P2.28 mostraria o item ao delegado.
        boolean inB = ScopedWorkVisibility.isVisible(item, VIEWER, Set.of(), delegations,
                Map.of(), NOW, scopeA(), new AreaAnchor(LOC_B, null, null));
        boolean inA = ScopedWorkVisibility.isVisible(item, VIEWER, Set.of(), delegations,
                Map.of(), NOW, scopeA(), new AreaAnchor(LOC_A, null, null));
        assertThat(inB).isFalse();
        assertThat(inA).isTrue();
    }

    @Test
    @DisplayName("item PRÓPRIO da localidade B também não aparece a quem só tem escopo na A")
    void ownItemFilteredByScope() {
        WorkItem item = itemAddressedTo(VIEWER);
        boolean inB = ScopedWorkVisibility.isVisible(item, VIEWER, Set.of(), List.of(),
                Map.of(), NOW, scopeA(), new AreaAnchor(LOC_B, null, null));
        assertThat(inB).isFalse();
    }

    @Test
    @DisplayName("filterInbox: da lista visível da P2.28 só ficam os itens dentro do escopo")
    void filterInboxKeepsOnlyInScope() {
        WorkItem inA = itemAddressedTo(VIEWER);
        WorkItem inB = itemAddressedTo(VIEWER);
        List<WorkItem> filtered = ScopedWorkVisibility.filterInbox(List.of(inA, inB), scopeA(),
                item -> item.id().equals(inA.id())
                        ? new AreaAnchor(LOC_A, null, null) : new AreaAnchor(LOC_B, null, null));
        assertThat(filtered).containsExactly(inA);
    }

    @Test
    @DisplayName("irrestrito vê item delegado de qualquer localidade")
    void unrestrictedSeesDelegatedItem() {
        WorkItem item = itemAddressedTo(DELEGATOR);
        boolean visible = ScopedWorkVisibility.isVisible(item, VIEWER, Set.of(),
                List.of(activeDelegation()), Map.of(), NOW,
                EffectiveScope.UNRESTRICTED, new AreaAnchor(LOC_B, null, null));
        assertThat(visible).isTrue();
    }
}
