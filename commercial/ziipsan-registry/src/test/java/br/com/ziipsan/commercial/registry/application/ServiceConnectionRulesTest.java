package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus.SystemRole;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras puras da ligação (GIS-05, P2.23): âncora do imóvel e papel de
 * sistema — a mesma tabela de casos que a V3_14 aplica em SQL na carga
 * inicial (os dois lados têm de concordar).
 */
class ServiceConnectionRulesTest {

    private static ConsumerUnit unit(UUID id, UUID parentId, String relation) {
        ConsumerUnit u = new ConsumerUnit();
        u.setId(id);
        u.setParentUnitId(parentId);
        u.setParentRelationType(relation);
        return u;
    }

    @Test
    @DisplayName("âncora: unidade avulsa é o próprio imóvel")
    void anchorIsSelfForStandaloneUnit() {
        UUID id = UUID.randomUUID();
        assertThat(ServiceConnectionRules.propertyAnchorId(unit(id, null, null))).isEqualTo(id);
    }

    @Test
    @DisplayName("âncora: filha de condomínio ancora na unidade-mãe")
    void anchorIsParentForCondominiumChild() {
        UUID parent = UUID.randomUUID();
        assertThat(ServiceConnectionRules.propertyAnchorId(
                unit(UUID.randomUUID(), parent, "CONDOMINIUM_MACRO"))).isEqualTo(parent);
    }

    @Test
    @DisplayName("âncora: anexo (ANNEX) não ancora na mãe — só condomínio partilha a ligação")
    void anchorIsSelfForAnnex() {
        UUID id = UUID.randomUUID();
        assertThat(ServiceConnectionRules.propertyAnchorId(
                unit(id, UUID.randomUUID(), "ANNEX"))).isEqualTo(id);
    }

    @Test
    @DisplayName("âncora: filha de condomínio sem mãe cai para si mesma")
    void anchorFallsBackWhenParentMissing() {
        UUID id = UUID.randomUUID();
        assertThat(ServiceConnectionRules.propertyAnchorId(
                unit(id, null, "CONDOMINIUM_MACRO"))).isEqualTo(id);
    }

    @Test
    @DisplayName("papel: inativo vence qualquer flag")
    void roleInactiveWins() {
        assertThat(ServiceConnectionRules.systemRoleOf(true, false, true, true, true))
                .isEqualTo(SystemRole.INACTIVE);
    }

    @Test
    @DisplayName("papel: água ligada e abastecendo é ACTIVE; ligada sem abastecer é CUT")
    void roleWaterActiveAndCut() {
        assertThat(ServiceConnectionRules.systemRoleOf(true, true, true, true, true))
                .isEqualTo(SystemRole.ACTIVE);
        assertThat(ServiceConnectionRules.systemRoleOf(true, true, true, false, true))
                .isEqualTo(SystemRole.CUT);
    }

    @Test
    @DisplayName("papel: esgoto ligado é ACTIVE (o legado de esgoto não tem supplies)")
    void roleSewerActive() {
        assertThat(ServiceConnectionRules.systemRoleOf(false, true, true, false, true))
                .isEqualTo(SystemRole.ACTIVE);
    }

    @Test
    @DisplayName("papel: com ligação física mas desligada é DISCONNECTED; sem ligação é NO_CONNECTION")
    void roleDisconnectedAndNoConnection() {
        assertThat(ServiceConnectionRules.systemRoleOf(true, true, false, false, true))
                .isEqualTo(SystemRole.DISCONNECTED);
        assertThat(ServiceConnectionRules.systemRoleOf(true, true, false, false, false))
                .isEqualTo(SystemRole.NO_CONNECTION);
    }

    @Test
    @DisplayName("ligação aberta é a sem endedOn")
    void openMeansNotEnded() {
        assertThat(ServiceConnectionRules.isOpen(null)).isTrue();
        assertThat(ServiceConnectionRules.isOpen(LocalDate.of(2026, 1, 1))).isFalse();
    }
}
