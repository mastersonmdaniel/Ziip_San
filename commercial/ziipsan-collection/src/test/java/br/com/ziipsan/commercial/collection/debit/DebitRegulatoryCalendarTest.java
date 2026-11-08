package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.legacydebit.BcbDebitRegimeRules;
import br.com.ziipsan.commercial.collection.pix.HybridChargeRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static br.com.ziipsan.commercial.collection.debit.DebitRegulatoryCalendar.AgreementKind.GENERAL;
import static br.com.ziipsan.commercial.collection.debit.DebitRegulatoryCalendar.AgreementKind.PUBLIC_SERVICE;
import static org.assertj.core.api.Assertions.assertThat;

class DebitRegulatoryCalendarTest {

    @Test
    void desligamentoDeConvenioDeServicoPublicoEPrimeiroDeJaneiroDe2027() {
        // Res. CMN 5.278/2025; Res. BCB 544/2025 (aceite da P3.24, um teste por regra)
        assertThat(DebitRegulatoryCalendar.legacyShutdownDate(PUBLIC_SERVICE))
                .isEqualTo(LocalDate.of(2027, 1, 1))
                // correcao D3: o valor vem da FONTE UNICA da P3.24, nao de literal local
                .isEqualTo(BcbDebitRegimeRules.PUBLIC_SERVICE_PIX_DEADLINE)
                .isEqualTo(BcbDebitRegimeRules.deducedShutdownDate(
                        BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO));
    }

    @Test
    void desligamentoDoRegimeGeralEPrimeiroDeJulhoDe2027() {
        // Res. BCB 565/2026 (aceite da P3.24, um teste por regra)
        assertThat(DebitRegulatoryCalendar.legacyShutdownDate(GENERAL))
                .isEqualTo(LocalDate.of(2027, 7, 1))
                // correcao D3: o valor vem da FONTE UNICA da P3.24, nao de literal local
                .isEqualTo(BcbDebitRegimeRules.GENERAL_REGIME_START)
                .isEqualTo(BcbDebitRegimeRules.deducedShutdownDate(
                        BcbDebitRegimeRules.Framing.REGIME_GERAL));
    }

    @Test
    void legadoOperaEstritamenteAntesDoDesligamento() {
        assertThat(DebitRegulatoryCalendar.legacyOperatesOn(PUBLIC_SERVICE, LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(DebitRegulatoryCalendar.legacyOperatesOn(PUBLIC_SERVICE, LocalDate.of(2027, 1, 1))).isFalse();
        assertThat(DebitRegulatoryCalendar.legacyOperatesOn(GENERAL, LocalDate.of(2027, 6, 30))).isTrue();
        assertThat(DebitRegulatoryCalendar.legacyOperatesOn(GENERAL, LocalDate.of(2027, 7, 1))).isFalse();
    }

    @Test
    void vigenciaDaHibridaVemDaFonteDaP32SemDuplicarLiteral() {
        assertThat(DebitRegulatoryCalendar.hybridEffectiveFrom())
                .isEqualTo(HybridChargeRule.EFFECTIVE_FROM)
                .isEqualTo(LocalDate.of(2027, 2, 1));
    }

    @Test
    void calendarioListaOsTresMarcosEmOrdemComAsFontes() {
        List<DebitRegulatoryCalendar.RegulatoryEvent> events = DebitRegulatoryCalendar.events();
        assertThat(events).hasSize(3);
        assertThat(events.get(0).date()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(events.get(0).source()).contains("5.278/2025").contains("544/2025");
        assertThat(events.get(1).date()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(events.get(1).source()).contains("587/2026");
        assertThat(events.get(2).date()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(events.get(2).source()).contains("565/2026");
    }
}
