package br.com.ziipsan.commercial.fiscal.nfse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regra de rejeição vigente (aceite da P3.6): a ausência de IBS e CBS na NFS-e não gera
 * rejeição até 31/12/2026 (nota do CGNFS-e de 07/08/2026). O teste confere a regra como ela
 * vale na produção restrita — sem transmissão real ao ambiente nacional.
 */
class NfseRejeicaoRuleTest {

    @Test
    @DisplayName("sem IBS/CBS até 31/12/2026 (inclusive) não rejeita, em nenhuma data de 2026")
    void absenceDoesNotRejectUntilEndOf2026() {
        assertThat(NfseEtapaRule.ausenciaDeIbsCbsGeraRejeicao(LocalDate.of(2026, 1, 1), false)).isFalse();
        assertThat(NfseEtapaRule.ausenciaDeIbsCbsGeraRejeicao(LocalDate.of(2026, 10, 1), false)).isFalse();
        assertThat(NfseEtapaRule.ausenciaDeIbsCbsGeraRejeicao(LocalDate.of(2026, 12, 31), false)).isFalse();
    }

    @Test
    @DisplayName("sem IBS/CBS a partir de 01/01/2027 rejeita")
    void absenceRejectsFrom2027() {
        assertThat(NfseEtapaRule.ausenciaDeIbsCbsGeraRejeicao(LocalDate.of(2027, 1, 1), false)).isTrue();
        assertThat(NfseEtapaRule.ausenciaDeIbsCbsGeraRejeicao(LocalDate.of(2027, 6, 15), false)).isTrue();
    }

    @Test
    @DisplayName("com IBS/CBS nunca rejeita por ausência, em data nenhuma")
    void presenceNeverRejects() {
        assertThat(NfseEtapaRule.ausenciaDeIbsCbsGeraRejeicao(LocalDate.of(2026, 12, 31), true)).isFalse();
        assertThat(NfseEtapaRule.ausenciaDeIbsCbsGeraRejeicao(LocalDate.of(2027, 1, 1), true)).isFalse();
    }

    @Test
    @DisplayName("fronteira exata da tolerância é 31/12/2026")
    void toleranceBoundary() {
        assertThat(NfseEtapaRule.FIM_DA_TOLERANCIA_SEM_IBS_CBS).isEqualTo(LocalDate.of(2026, 12, 31));
    }
}
