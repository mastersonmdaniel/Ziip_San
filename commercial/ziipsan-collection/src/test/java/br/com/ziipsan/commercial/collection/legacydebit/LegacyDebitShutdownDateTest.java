package br.com.ziipsan.commercial.collection.legacydebit;

import br.com.ziipsan.commercial.collection.parameters.LegacyDebitParameters;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Um teste por regra de data do BCB (aceite da P3.24) + data nunca digitada. */
class LegacyDebitShutdownDateTest {

    @Test
    void regraConvenioServicoPublicoDesligaEmPrimeiroDeJaneiroDe2027() {
        // Res. CMN 5.278/2025; Res. BCB 544/2025: fim da adequacao ao Pix Automatico.
        assertThat(BcbDebitRegimeRules.deducedShutdownDate(
                BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO))
                .isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(BcbDebitRegimeRules.isShutdownOn(
                BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO, LocalDate.of(2026, 12, 31)))
                .isFalse();
        assertThat(BcbDebitRegimeRules.isShutdownOn(
                BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO, LocalDate.of(2027, 1, 1)))
                .isTrue();
    }

    @Test
    void regraRegimeGeralDesligaEmPrimeiroDeJulhoDe2027() {
        // Res. BCB 565/2026: vigencia do novo regime geral do debito automatico.
        assertThat(BcbDebitRegimeRules.deducedShutdownDate(
                BcbDebitRegimeRules.Framing.REGIME_GERAL))
                .isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(BcbDebitRegimeRules.isShutdownOn(
                BcbDebitRegimeRules.Framing.REGIME_GERAL, LocalDate.of(2027, 6, 30))).isFalse();
        assertThat(BcbDebitRegimeRules.isShutdownOn(
                BcbDebitRegimeRules.Framing.REGIME_GERAL, LocalDate.of(2027, 7, 1))).isTrue();
    }

    @Test
    void dataDigitadaNoParametroNaoMudaADataDeduzida() {
        // Mesmo com uma chave de data "plantada" no mapa, a data vem so da regra.
        LegacyDebitParameters params = LegacyDebitParameters.fromValues(Map.of(
                LegacyDebitParameters.LEGACY_DEBIT_ENABLED, "true",
                "pay.legacy_debit_shutdown_date", "\"2099-12-31\""));
        LegacyDebitService svc = new LegacyDebitService(new LegacyDebitFixture.MemStore(),
                new LegacyDebitFixture.MemFile(), params,
                BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO);
        assertThat(svc.shutdownDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void parametroDesligadoRecusaOperacaoDoLegado() {
        LegacyDebitService svc = LegacyDebitFixture.service(new LegacyDebitFixture.MemStore(),
                new LegacyDebitFixture.MemFile(), false,
                BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO);
        assertThatThrownBy(() -> svc.importLegacyAuthorization(LegacyDebitFixture.TENANT, "M1",
                null, LegacyDebitFixture.PAYER_CPF, "001", "001", "LEGADO-M1",
                LegacyDebitFixture.NOW, LegacyDebitFixture.TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pay.legacy_debit_enabled");
    }

    @Test
    void aposADataDeduzidaRemessaERecusada() {
        LegacyDebitFixture.MemStore store = new LegacyDebitFixture.MemStore();
        LegacyDebitFixture.MemFile file = new LegacyDebitFixture.MemFile();
        LegacyDebitService svc = LegacyDebitFixture.publicService(store, file);
        LegacyDebitFixture.activeOptant(svc, "M1", "001", "001");
        assertThatThrownBy(() -> svc.generateRemittance(LegacyDebitFixture.TENANT, "001",
                java.util.List.of(new LegacyDebitService.DebitDraft("M1",
                        new java.math.BigDecimal("50.00"), LocalDate.of(2027, 1, 10))),
                java.util.List.of(), java.util.List.of(), LocalDate.of(2027, 1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2027-01-01");
        assertThat(file.written).isEmpty();
    }
}
