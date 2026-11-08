package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolTransferRuleTest {

    private static final UUID UNIT_A = UUID.randomUUID();
    private static final UUID UNIT_B = UUID.randomUUID();
    private static final UUID LOC_A = UUID.randomUUID();
    private static final UUID LOC_B = UUID.randomUUID();

    private static EffectiveScope areasOf(UUID unit, UUID locality) {
        return new EffectiveScope(false, Set.of(unit), Set.of(locality), Set.of(), Set.of());
    }

    @Test
    @DisplayName("aceite: OS programada (SCHEDULED) bloqueia o trâmite com 409, mesmo com transfer-any-unit")
    void scheduledOrderBlocks() {
        assertThatThrownBy(() -> ProtocolTransferRule.assertCanTransfer(UNIT_A, UNIT_B,
                Set.of(OrgScopePermissions.PROTOCOL_TRANSFER_ANY_UNIT), EffectiveScope.EMPTY,
                areasOf(UNIT_A, LOC_A), areasOf(UNIT_B, LOC_B), 1))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("sem OS programada e com escopo nas DUAS unidades, o trâmite passa sem permissão especial")
    void scopedOnBothEndsPasses() {
        EffectiveScope both = new EffectiveScope(false, Set.of(UNIT_A, UNIT_B),
                Set.of(LOC_A, LOC_B), Set.of(), Set.of());
        assertThatCode(() -> ProtocolTransferRule.assertCanTransfer(UNIT_A, UNIT_B,
                Set.of(), both, areasOf(UNIT_A, LOC_A), areasOf(UNIT_B, LOC_B), 0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("regressão da revisão cruzada: escopo só por ÁREA (sem unidade) tramita entre unidades cujas áreas cobre")
    void areaOnlyScopeCanTransfer() {
        EffectiveScope areaOnlyBoth = new EffectiveScope(false, Set.of(),
                Set.of(LOC_A, LOC_B), Set.of(), Set.of());
        assertThatCode(() -> ProtocolTransferRule.assertCanTransfer(UNIT_A, UNIT_B,
                Set.of(), areaOnlyBoth, areasOf(UNIT_A, LOC_A), areasOf(UNIT_B, LOC_B), 0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sem transfer-any-unit e sem escopo no destino, recusa; com a permissão, passa")
    void destinationScopeOrPermission() {
        EffectiveScope onlyOrigin = areasOf(UNIT_A, LOC_A);
        assertThatThrownBy(() -> ProtocolTransferRule.assertCanTransfer(UNIT_A, UNIT_B,
                Set.of(), onlyOrigin, areasOf(UNIT_A, LOC_A), areasOf(UNIT_B, LOC_B), 0))
                .isInstanceOf(ApiException.Validation.class);
        assertThatCode(() -> ProtocolTransferRule.assertCanTransfer(UNIT_A, UNIT_B,
                Set.of(OrgScopePermissions.PROTOCOL_TRANSFER_ANY_UNIT), onlyOrigin,
                areasOf(UNIT_A, LOC_A), areasOf(UNIT_B, LOC_B), 0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("correção D3: sem escopo nas pontas, a resposta é a validação de escopo — nunca o 409 com a contagem de OS programadas")
    void scopeCheckedBeforeScheduledCount() {
        assertThatThrownBy(() -> ProtocolTransferRule.assertCanTransfer(UNIT_A, UNIT_B,
                Set.of(), EffectiveScope.EMPTY,
                areasOf(UNIT_A, LOC_A), areasOf(UNIT_B, LOC_B), 3))
                .isInstanceOf(ApiException.Validation.class);
        // As partes separadas mantêm a mesma ordem quando chamadas uma a uma:
        assertThatThrownBy(() -> ProtocolTransferRule.assertTransferScope(UNIT_A, UNIT_B,
                Set.of(), EffectiveScope.EMPTY,
                areasOf(UNIT_A, LOC_A), areasOf(UNIT_B, LOC_B)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> ProtocolTransferRule.assertNoScheduledOrders(3))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("tramitar para a própria unidade é validação, não no-op")
    void sameUnitRejected() {
        assertThatThrownBy(() -> ProtocolTransferRule.assertCanTransfer(UNIT_A, UNIT_A,
                Set.of(OrgScopePermissions.PROTOCOL_TRANSFER_ANY_UNIT), EffectiveScope.UNRESTRICTED,
                areasOf(UNIT_A, LOC_A), areasOf(UNIT_A, LOC_A), 0))
                .isInstanceOf(ApiException.Validation.class);
    }
}
