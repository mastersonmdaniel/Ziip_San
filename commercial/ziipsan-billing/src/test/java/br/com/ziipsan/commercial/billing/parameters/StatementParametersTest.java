package br.com.ziipsan.commercial.billing.parameters;

import br.com.ziipsan.commercial.billing.parameters.StatementParameters.StatementChannel;
import br.com.ziipsan.commercial.billing.parameters.StatementParameters.WaterQualitySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parametros da P2.24 (quit.statement_channel e bill.water_quality_source,
 * classe d): os dois tem padrao de fabrica declarado no catalogo — ausencia
 * vale o padrao; valor presente fora do catalogo e erro explicito.
 */
class StatementParametersTest {

    @Test
    @DisplayName("ausencia dos dois parametros vale o padrao de fabrica declarado")
    void absentParametersUseDeclaredFactoryDefaults() {
        StatementParameters params = StatementParameters.fromValues(Map.of());
        assertThat(params.statementChannel()).isEqualTo(StatementChannel.INVOICE_SPACE);
        assertThat(params.waterQualitySource()).isEqualTo(WaterQualitySource.LAB_SPREADSHEET);
    }

    @Test
    @DisplayName("canal explicito: documento anexo e portal sao lidos do JSON do parametro")
    void explicitChannelsAreParsed() {
        assertThat(StatementParameters.fromValues(
                Map.of(StatementParameters.STATEMENT_CHANNEL, "\"ANNEX_DOCUMENT\"")).statementChannel())
                .isEqualTo(StatementChannel.ANNEX_DOCUMENT);
        assertThat(StatementParameters.fromValues(
                Map.of(StatementParameters.STATEMENT_CHANNEL, "\"PORTAL\"")).statementChannel())
                .isEqualTo(StatementChannel.PORTAL);
        assertThat(StatementParameters.fromValues(
                Map.of(StatementParameters.STATEMENT_CHANNEL, "\"INVOICE_SPACE\"")).statementChannel())
                .isEqualTo(StatementChannel.INVOICE_SPACE);
    }

    @Test
    @DisplayName("fonte explicita: modulo de qualidade (ED-50) e lido do JSON do parametro")
    void explicitWaterQualitySourceIsParsed() {
        assertThat(StatementParameters.fromValues(
                Map.of(StatementParameters.WATER_QUALITY_SOURCE, "\"QUALITY_MODULE\"")).waterQualitySource())
                .isEqualTo(WaterQualitySource.QUALITY_MODULE);
        assertThat(StatementParameters.fromValues(
                Map.of(StatementParameters.WATER_QUALITY_SOURCE, "\"LAB_SPREADSHEET\"")).waterQualitySource())
                .isEqualTo(WaterQualitySource.LAB_SPREADSHEET);
    }

    @Test
    @DisplayName("valor fora do catalogo e erro explicito, nunca um canal assumido")
    void valueOutsideCatalogIsExplicitError() {
        StatementParameters params = StatementParameters.fromValues(
                Map.of(StatementParameters.STATEMENT_CHANNEL, "\"EMAIL\""));
        assertThatThrownBy(params::statementChannel)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quit.statement_channel");
        StatementParameters source = StatementParameters.fromValues(
                Map.of(StatementParameters.WATER_QUALITY_SOURCE, "\"PLANILHA\""));
        assertThatThrownBy(source::waterQualitySource)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.water_quality_source");
    }

    @Test
    @DisplayName("valor que nao e texto (numero) e erro explicito")
    void nonTextValueIsExplicitError() {
        StatementParameters params = StatementParameters.fromValues(
                Map.of(StatementParameters.STATEMENT_CHANNEL, "123"));
        assertThatThrownBy(params::statementChannel)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quit.statement_channel");
    }

    @Test
    @DisplayName("mapa nulo e recusado; JSON nulo vale o padrao de fabrica")
    void nullMapRejectedAndJsonNullUsesDefault() {
        assertThatThrownBy(() -> StatementParameters.fromValues(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(StatementParameters.fromValues(
                Map.of(StatementParameters.STATEMENT_CHANNEL, "null")).statementChannel())
                .isEqualTo(StatementChannel.INVOICE_SPACE);
    }
}
