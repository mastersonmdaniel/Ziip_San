package br.com.ziipsan.commercial.collection.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapa do item 2.15 do SAMAE na fatia da ED-29: completo e sem duplicidade. */
class Samae215CoverageTest {

    @Test
    @DisplayName("fatia ED-29: 12 SG mapeados sem duplicidade — 6 na faixa do 2.15 (SG-343..428) e 6 irmaos de 18e-E9/18f-ENT-04 fora dela")
    void sliceIsCompleteAndUnique() {
        assertThat(Samae215Coverage.ED29_SLICE).hasSize(12);
        assertThat(Samae215Coverage.sgCodes()).doesNotHaveDuplicates();
        // SG-375..400 estao dentro do 2.15 (SG-343..428); SG-486/501-504/558 sao
        // os requisitos irmaos de corte/esgoto citados para a ED-29 em 18e-E9 e 18f-ENT-04.
        assertThat(Samae215Coverage.sgCodes()).contains(
                "SG-375", "SG-386", "SG-393", "SG-394", "SG-398", "SG-400",
                "SG-486", "SG-501", "SG-502", "SG-503", "SG-504", "SG-558");
        assertThat(Samae215Coverage.ED29_SLICE)
                .allSatisfy(e -> assertThat(e.feature()).isNotBlank());
    }
}
