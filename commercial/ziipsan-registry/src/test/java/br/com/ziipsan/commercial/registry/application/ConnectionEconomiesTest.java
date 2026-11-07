package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.ConnectionEconomies.UnitEconomies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ED-13 / POC-10 (alínea c do roteiro VISAN): ligação com economias
 * mistas demonstrável — uma ligação, várias unidades atendidas,
 * categorias somadas. Caso calculado à mão: mãe com 2 residenciais,
 * filha com 1 residencial + 3 comerciais, filha com 2 industriais =
 * RES 3, COM 3, IND 2, total 8, mista.
 */
class ConnectionEconomiesTest {

    @Test
    @DisplayName("POC-10: ligação de condomínio com economias mistas agrega por categoria")
    void mixedEconomiesAggregate() {
        List<UnitEconomies> served = List.of(
                new UnitEconomies(UUID.randomUUID(), Map.of("RESIDENTIAL", 2)),
                new UnitEconomies(UUID.randomUUID(), Map.of("RESIDENTIAL", 1, "COMMERCIAL", 3)),
                new UnitEconomies(UUID.randomUUID(), Map.of("INDUSTRIAL", 2)));
        assertThat(ConnectionEconomies.aggregate(served))
                .containsEntry("RESIDENTIAL", 3)
                .containsEntry("COMMERCIAL", 3)
                .containsEntry("INDUSTRIAL", 2)
                .hasSize(3);
        assertThat(ConnectionEconomies.totalEconomies(served)).isEqualTo(8);
        assertThat(ConnectionEconomies.isMixed(served)).isTrue();
    }

    @Test
    @DisplayName("ligação de categoria única não é mista; ligação sem unidades não tem economias")
    void singleCategoryIsNotMixed() {
        List<UnitEconomies> served = List.of(
                new UnitEconomies(UUID.randomUUID(), Map.of("RESIDENTIAL", 4)));
        assertThat(ConnectionEconomies.isMixed(served)).isFalse();
        assertThat(ConnectionEconomies.totalEconomies(List.of())).isZero();
        assertThat(ConnectionEconomies.isMixed(List.of())).isFalse();
    }
}
