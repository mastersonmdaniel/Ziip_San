package br.com.ziipsan.intelligence.gis.drain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Símbolos §4.3: manifesto 20 tipos × 5 estados e regra de cobertura. */
class GisDrainSymbolsTest {

    @Test
    void manifestoTemCemChaves() {
        assertThat(GisDrainSymbols.SYMBOL_STATES).hasSize(5);
        assertThat(GisDrainSymbols.expectedKeys()).hasSize(100);
        assertThat(GisDrainSymbols.manifest()).hasSize(20);
        assertThat(GisDrainSymbols.manifest().get("INLET"))
                .containsEntry("IN_SERVICE", "storm-inlet@IN_SERVICE")
                .containsEntry("OUT_OF_TARGET", "storm-inlet@OUT_OF_TARGET");
    }

    @Test
    void bibliotecaVaziaAcusaTodasAsChavesDosTiposAtivos() {
        // Estado real hoje (arte da P1.30 pendente): declarado, nao maquiado.
        assertThat(GisDrainSymbols.missingSymbols(Set.of("INLET"), Set.of()))
                .hasSize(5)
                .contains("storm-inlet@PLANNED", "storm-inlet@ABANDONED");
        assertThat(GisDrainSymbols.missingSymbols(
                Set.of("INLET"), Set.copyOf(GisDrainSymbols.expectedKeys()))).isEmpty();
    }

    @Test
    void tipoInativoNaoEntraNaCobranca() {
        assertThat(GisDrainSymbols.missingSymbols(Set.of(), Set.of())).isEmpty();
    }
}
