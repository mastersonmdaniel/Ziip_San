package br.com.ziipsan.commercial.billing.catalog;

import br.com.ziipsan.commercial.billing.catalog.ChargeVersion.ChargeParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Versao da cobranca (Camada 1 — catalogo unico, proposta secao 2.2):
 * vigencia, appliesTo nas fronteiras e coerencia modelo x canal
 * nomeado/parametro para os seis modelos (um caso valido e um invalido
 * por modelo), na forma reconciliada da correcao da Camada 1
 * (INV-C1-03 na forma do desenho da Camada 0: canais nomeados
 * exclusivos por modelo + ChargeParameters de valor).
 */
class ChargeVersionTest {

    private static final UUID DEF = ChargeDefinitionTest.id(1);
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private static ChargeVersion version(CalculationModel model, UUID tariffVersionId,
                                         String priceItemCode, UUID externalContractId,
                                         ChargeParameters params) {
        return new ChargeVersion(UUID.randomUUID(), DEF, 1, FROM, TO, model,
                tariffVersionId, priceItemCode, externalContractId, params);
    }

    private static ChargeParameters params(String percent, String fixed, String unitPrice) {
        return new ChargeParameters(
                percent == null ? null : new BigDecimal(percent),
                fixed == null ? null : new BigDecimal(fixed),
                unitPrice == null ? null : new BigDecimal(unitPrice));
    }

    // ------------------------------------------------------------------
    // Coerencia modelo x canal/parametro: 6 validos e 6 invalidos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PROGRESSIVE_BY_CONSUMPTION: valido com tariffVersionId; invalido sem")
    void progressiveCoherence() {
        assertThat(version(CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                ChargeDefinitionTest.id(50), null, null, ChargeParameters.empty())).isNotNull();
        assertThatThrownBy(() -> version(CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                null, null, null, ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tariffVersionId");
    }

    @Test
    @DisplayName("DERIVED_FROM_VOLUME: valido com percent ou com tariffVersionId; invalido sem nenhum")
    void derivedCoherence() {
        assertThat(version(CalculationModel.DERIVED_FROM_VOLUME,
                null, null, null, params("80", null, null))).isNotNull();
        assertThat(version(CalculationModel.DERIVED_FROM_VOLUME,
                ChargeDefinitionTest.id(51), null, null, ChargeParameters.empty())).isNotNull();
        assertThatThrownBy(() -> version(CalculationModel.DERIVED_FROM_VOLUME,
                null, null, null, ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percent");
    }

    @Test
    @DisplayName("FIXED_PER_UNIT: valido com fixedAmount; invalido sem")
    void fixedCoherence() {
        assertThat(version(CalculationModel.FIXED_PER_UNIT,
                null, null, null, params(null, "12.50", null))).isNotNull();
        assertThatThrownBy(() -> version(CalculationModel.FIXED_PER_UNIT,
                null, null, null, ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixedAmount");
    }

    @Test
    @DisplayName("CATALOG_PRICE: valido com priceItemCode ou com unitPrice resolvido; invalido sem nenhum")
    void catalogPriceCoherence() {
        assertThat(version(CalculationModel.CATALOG_PRICE,
                null, "ITEM_TABELA_ED34", null, ChargeParameters.empty())).isNotNull();
        assertThat(version(CalculationModel.CATALOG_PRICE,
                null, null, null, params(null, null, "300.00"))).isNotNull();
        assertThatThrownBy(() -> version(CalculationModel.CATALOG_PRICE,
                null, null, null, ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priceItemCode");
    }

    @Test
    @DisplayName("PERCENT_OF_BASE: valido com percent; invalido sem")
    void percentCoherence() {
        assertThat(version(CalculationModel.PERCENT_OF_BASE,
                null, null, null, params("5.5", null, null))).isNotNull();
        assertThatThrownBy(() -> version(CalculationModel.PERCENT_OF_BASE,
                null, null, null, ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percent");
    }

    @Test
    @DisplayName("EXTERNAL_VALUE: valido com externalContractId; invalido sem")
    void externalCoherence() {
        assertThat(version(CalculationModel.EXTERNAL_VALUE,
                null, null, ChargeDefinitionTest.id(52), ChargeParameters.empty())).isNotNull();
        assertThatThrownBy(() -> version(CalculationModel.EXTERNAL_VALUE,
                null, null, null, ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalContractId");
    }

    // ------------------------------------------------------------------
    // Exclusividade de canal nomeado por modelo (INV-C1-03)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("exclusividade: PROGRESSIVE e DERIVED proibem priceItemCode/externalContractId")
    void tariffChannelModelsRejectOtherChannels() {
        assertThatThrownBy(() -> version(CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                ChargeDefinitionTest.id(50), "ITEM_X", null, ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
        assertThatThrownBy(() -> version(CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                ChargeDefinitionTest.id(50), null, ChargeDefinitionTest.id(53),
                ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
        assertThatThrownBy(() -> version(CalculationModel.DERIVED_FROM_VOLUME,
                null, "ITEM_X", null, params("80", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
        assertThatThrownBy(() -> version(CalculationModel.DERIVED_FROM_VOLUME,
                ChargeDefinitionTest.id(51), null, ChargeDefinitionTest.id(53),
                ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
    }

    @Test
    @DisplayName("exclusividade: CATALOG_PRICE e EXTERNAL_VALUE proibem os canais um do outro e o de tarifa")
    void catalogAndExternalRejectOtherChannels() {
        assertThatThrownBy(() -> version(CalculationModel.CATALOG_PRICE,
                ChargeDefinitionTest.id(50), "ITEM_X", null, ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
        assertThatThrownBy(() -> version(CalculationModel.CATALOG_PRICE,
                null, "ITEM_X", ChargeDefinitionTest.id(53), ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
        assertThatThrownBy(() -> version(CalculationModel.EXTERNAL_VALUE,
                ChargeDefinitionTest.id(50), null, ChargeDefinitionTest.id(52),
                ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
        assertThatThrownBy(() -> version(CalculationModel.EXTERNAL_VALUE,
                null, "ITEM_X", ChargeDefinitionTest.id(52), ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
    }

    @Test
    @DisplayName("exclusividade: FIXED_PER_UNIT e PERCENT_OF_BASE proibem os tres canais nomeados")
    void valueOnlyModelsRejectNamedChannels() {
        assertThatThrownBy(() -> version(CalculationModel.FIXED_PER_UNIT,
                ChargeDefinitionTest.id(50), null, null, params(null, "12.50", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
        assertThatThrownBy(() -> version(CalculationModel.FIXED_PER_UNIT,
                null, "ITEM_X", null, params(null, "12.50", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
        assertThatThrownBy(() -> version(CalculationModel.PERCENT_OF_BASE,
                null, null, ChargeDefinitionTest.id(53), params("5.5", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proibe");
    }

    // ------------------------------------------------------------------
    // Vigencia e parametros
    // ------------------------------------------------------------------

    @Test
    @DisplayName("versao < 1 e vigencia invertida sao recusadas")
    void invalidVersionAndValidityAreRefused() {
        assertThatThrownBy(() -> new ChargeVersion(UUID.randomUUID(), DEF, 0, FROM, TO,
                CalculationModel.FIXED_PER_UNIT, null, null, null, params(null, "1.00", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChargeVersion(UUID.randomUUID(), DEF, 1, TO, FROM,
                CalculationModel.FIXED_PER_UNIT, null, null, null, params(null, "1.00", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vigencia");
    }

    @Test
    @DisplayName("parametros negativos e priceItemCode em branco sao recusados; parametros vazios sao todos nulos")
    void invalidParametersAreRefused() {
        assertThatThrownBy(() -> params("-1", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params(null, "-0.01", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params(null, null, "-5"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> version(CalculationModel.CATALOG_PRICE,
                null, " ", null, ChargeParameters.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priceItemCode");
        assertThat(ChargeParameters.empty().percent()).isNull();
        assertThat(ChargeParameters.empty().fixedAmount()).isNull();
        assertThat(ChargeParameters.empty().unitPrice()).isNull();
    }

    @Test
    @DisplayName("appliesTo nas fronteiras: inicio e fim inclusivos, fora nao se aplica, aberta nao expira")
    void appliesToBoundaries() {
        ChargeVersion v = version(CalculationModel.FIXED_PER_UNIT,
                null, null, null, params(null, "10.00", null));
        assertThat(v.appliesTo(FROM.minusDays(1))).isFalse();
        assertThat(v.appliesTo(FROM)).isTrue();
        assertThat(v.appliesTo(LocalDate.of(2026, 6, 15))).isTrue();
        assertThat(v.appliesTo(TO)).isTrue();
        assertThat(v.appliesTo(TO.plusDays(1))).isFalse();

        ChargeVersion open = new ChargeVersion(UUID.randomUUID(), DEF, 2, FROM, null,
                CalculationModel.FIXED_PER_UNIT, null, null, null, params(null, "10.00", null));
        assertThat(open.appliesTo(LocalDate.of(2035, 1, 1))).isTrue();
    }

    @Test
    @DisplayName("canais nomeados viajam na versao (tariffVersionId/priceItemCode/externalContractId)")
    void namedChannelsAreCarried() {
        ChargeVersion progressive = version(CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                ChargeDefinitionTest.id(50), null, null, ChargeParameters.empty());
        assertThat(progressive.tariffVersionId()).isEqualTo(ChargeDefinitionTest.id(50));
        assertThat(progressive.priceItemCode()).isNull();
        assertThat(progressive.externalContractId()).isNull();

        ChargeVersion catalog = version(CalculationModel.CATALOG_PRICE,
                null, "ITEM_TABELA_ED34", null, ChargeParameters.empty());
        assertThat(catalog.priceItemCode()).isEqualTo("ITEM_TABELA_ED34");
        assertThat(catalog.tariffVersionId()).isNull();
        assertThat(catalog.externalContractId()).isNull();

        ChargeVersion external = version(CalculationModel.EXTERNAL_VALUE,
                null, null, ChargeDefinitionTest.id(52), ChargeParameters.empty());
        assertThat(external.externalContractId()).isEqualTo(ChargeDefinitionTest.id(52));
        assertThat(external.tariffVersionId()).isNull();
        assertThat(external.priceItemCode()).isNull();
    }
}
