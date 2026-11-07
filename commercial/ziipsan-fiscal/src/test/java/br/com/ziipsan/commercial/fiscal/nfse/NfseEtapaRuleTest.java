package br.com.ziipsan.commercial.fiscal.nfse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste por etapa do Ato Conjunto RFB/CGIBS 4/2026 (aceite da P3.6): NFS-e emitida na data
 * da etapa do serviço ou depois sai com os grupos de IBS e CBS; antes, sem eles.
 */
class NfseEtapaRuleTest {

    @Test
    @DisplayName("etapas registradas são exatamente as 3 datas do Ato Conjunto 4/2026")
    void etapaDates() {
        assertThat(NfseEtapa.ISS_FORA_HIPOTESES_ESPECIAIS.inicioObrigatoriedade()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(NfseEtapa.DEMAIS_HIPOTESES.inicioObrigatoriedade()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(NfseEtapa.SIMPLES_NACIONAL.inicioObrigatoriedade()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(NfseEtapa.ISS_FORA_HIPOTESES_ESPECIAIS.dispositivo()).isEqualTo("art. 1º, III, d");
        assertThat(NfseEtapa.DEMAIS_HIPOTESES.dispositivo()).isEqualTo("art. 1º, III, h");
        assertThat(NfseEtapa.SIMPLES_NACIONAL.dispositivo()).isEqualTo("art. 1º, § 1º");
        assertThat(NfseEtapa.values()).hasSize(3);
    }

    @Test
    @DisplayName("etapa 1 (ISS fora das hipóteses especiais, 01/10/2026): véspera sem grupos, no dia e depois com grupos")
    void etapaIssGeral() {
        NfseEtapa e = NfseEtapaRule.etapaAplicavel(NfseEtapa.ISS_FORA_HIPOTESES_ESPECIAIS, false);
        assertThat(NfseEtapaRule.incluiGruposIbsCbs(LocalDate.of(2026, 9, 30), e)).isFalse();
        assertThat(NfseEtapaRule.incluiGruposIbsCbs(LocalDate.of(2026, 10, 1), e)).isTrue();
        assertThat(NfseEtapaRule.incluiGruposIbsCbs(LocalDate.of(2026, 10, 2), e)).isTrue();
    }

    @Test
    @DisplayName("etapa 2 (demais hipóteses de NFS-e, 01/12/2026): véspera sem grupos, no dia e depois com grupos")
    void etapaDemaisHipoteses() {
        NfseEtapa e = NfseEtapaRule.etapaAplicavel(NfseEtapa.DEMAIS_HIPOTESES, false);
        assertThat(NfseEtapaRule.incluiGruposIbsCbs(LocalDate.of(2026, 11, 30), e)).isFalse();
        assertThat(NfseEtapaRule.incluiGruposIbsCbs(LocalDate.of(2026, 12, 1), e)).isTrue();
        assertThat(NfseEtapaRule.incluiGruposIbsCbs(LocalDate.of(2026, 12, 2), e)).isTrue();
    }

    @Test
    @DisplayName("etapa 3 (Simples Nacional, 01/01/2027): prestador optante usa 01/01/2027 qualquer que seja a etapa do tipo")
    void etapaSimplesNacional() {
        for (NfseEtapa doTipo : NfseEtapa.values()) {
            NfseEtapa e = NfseEtapaRule.etapaAplicavel(doTipo, true);
            assertThat(e).isEqualTo(NfseEtapa.SIMPLES_NACIONAL);
            assertThat(NfseEtapaRule.incluiGruposIbsCbs(LocalDate.of(2026, 12, 31), e)).isFalse();
            assertThat(NfseEtapaRule.incluiGruposIbsCbs(LocalDate.of(2027, 1, 1), e)).isTrue();
            assertThat(NfseEtapaRule.incluiGruposIbsCbs(LocalDate.of(2027, 1, 2), e)).isTrue();
        }
    }

    @Test
    @DisplayName("tipo de serviço sem etapa registrada é erro explícito, nunca default")
    void missingStageIsAnError() {
        assertThatThrownBy(() -> NfseEtapaRule.etapaAplicavel(null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> NfseEtapaRule.etapaAplicavel(null, true))
                .isInstanceOf(NullPointerException.class);
    }
}
