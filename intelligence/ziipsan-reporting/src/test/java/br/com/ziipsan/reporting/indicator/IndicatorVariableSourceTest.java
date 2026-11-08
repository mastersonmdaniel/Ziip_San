package br.com.ziipsan.reporting.indicator;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste nomeado do aceite da P3.41: "indicador com variavel sem fonte
 * recusado". Cobre as quatro formas de ficar sem fonte: tipo nulo, view em
 * branco, view fora do nucleo da P3.22 e manual sem responsavel — e a
 * ambiguidade de duas fontes.
 */
class IndicatorVariableSourceTest {

    @Test
    void variableWithoutAnySourceIsRefused() {
        assertThatThrownBy(() -> new IndicatorVariable("volume", null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("VARIABLE_WITHOUT_SOURCE");
    }

    @Test
    void reportViewSourceWithoutViewIsRefused() {
        assertThatThrownBy(() -> IndicatorVariable.fromReportView("volume", " "))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("VARIABLE_WITHOUT_SOURCE");
    }

    @Test
    void viewOutsideReportingCoreIsRefused() {
        assertThatThrownBy(() -> IndicatorVariable.fromReportView("volume", "vw_inventada"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("VARIABLE_VIEW_NOT_FROM_REPORTING_CORE");
    }

    @Test
    void manualSourceWithoutResponsibleIsRefused() {
        assertThatThrownBy(() -> IndicatorVariable.manual("leitura_campo", null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("VARIABLE_WITHOUT_SOURCE");
    }

    @Test
    void twoSourcesAtOnceAreRefused() {
        assertThatThrownBy(() -> new IndicatorVariable("volume",
                IndicatorVariable.SourceType.REPORT_VIEW, "rpt_volume", UUID.randomUUID()))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("VARIABLE_SOURCE_AMBIGUOUS");
    }
}
