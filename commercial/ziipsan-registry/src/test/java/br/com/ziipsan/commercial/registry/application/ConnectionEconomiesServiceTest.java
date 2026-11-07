package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.ConnectionEconomies.UnitEconomies;
import br.com.ziipsan.commercial.registry.application.ConnectionEconomiesService.EconomyRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-13 / POC-10 (correção M3): a montagem das economias a partir das
 * linhas resolvidas da consulta real (unidades atendidas x
 * unit_subcategories VIGENTES x categoria pelo catálogo) — a soma por
 * unidade/categoria, a rejeição de linha sem categoria de catálogo e
 * o caso misto demonstrável sobre a montagem, não sobre dados soltos.
 */
class ConnectionEconomiesServiceTest {

    @Test
    @DisplayName("montagem: linhas do catálogo viram economias por unidade e a ligação é mista (POC-10)")
    void assembleFromCatalogRows() {
        UUID unitA = UUID.randomUUID();
        UUID unitB = UUID.randomUUID();
        UUID unitC = UUID.randomUUID();
        List<UnitEconomies> served = ConnectionEconomiesService.assemble(List.of(
                new EconomyRow(unitA, "RESIDENTIAL", 2),
                new EconomyRow(unitB, "RESIDENTIAL", 1),
                new EconomyRow(unitB, "COMMERCIAL", 3),
                new EconomyRow(unitC, "INDUSTRIAL", 2)));
        assertThat(served).hasSize(3);
        assertThat(ConnectionEconomies.aggregate(served))
                .containsEntry("RESIDENTIAL", 3)
                .containsEntry("COMMERCIAL", 3)
                .containsEntry("INDUSTRIAL", 2)
                .hasSize(3);
        assertThat(ConnectionEconomies.totalEconomies(served)).isEqualTo(8);
        assertThat(ConnectionEconomies.isMixed(served)).isTrue();
    }

    @Test
    @DisplayName("montagem: mesma unidade e categoria em linhas distintas somam; sem linhas, sem economias")
    void assembleMergesAndHandlesEmpty() {
        UUID unit = UUID.randomUUID();
        List<UnitEconomies> served = ConnectionEconomiesService.assemble(List.of(
                new EconomyRow(unit, "RESIDENTIAL", 2),
                new EconomyRow(unit, "RESIDENTIAL", 3)));
        assertThat(served).hasSize(1);
        assertThat(served.get(0).total()).isEqualTo(5);
        assertThat(ConnectionEconomiesService.assemble(List.of())).isEmpty();
    }

    @Test
    @DisplayName("montagem: linha sem categoria de catálogo é erro explícito, nunca categoria inventada")
    void rowWithoutCatalogCategoryIsExplicitError() {
        assertThatThrownBy(() -> ConnectionEconomiesService.assemble(List.of(
                new EconomyRow(UUID.randomUUID(), " ", 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem categoria no catalogo");
        assertThatThrownBy(() -> ConnectionEconomiesService.assemble(List.of(
                new EconomyRow(UUID.randomUUID(), "RESIDENTIAL", -1))))
                .isInstanceOf(IllegalStateException.class);
    }
}
