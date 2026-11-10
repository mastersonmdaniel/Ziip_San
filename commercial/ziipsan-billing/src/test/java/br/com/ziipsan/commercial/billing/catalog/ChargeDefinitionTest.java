package br.com.ziipsan.commercial.billing.catalog;

import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billingrules.servicecatalog.RevenueNature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Definicao da cobranca (Camada 1 — catalogo unico, proposta secoes
 * 2.1/2.2): dono propria x terceiro e a regra do prestador de terceiro;
 * na correcao da Camada 1, tambem os campos novos reconciliados com o
 * desenho da Camada 0 (fieldCalculable, accountingKeyRef e, desde a
 * reconciliacao pos-item 45, o escopo municipal tipado
 * municipalityIbge).
 */
class ChargeDefinitionTest {

    static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    static ChargeDefinition own(String code) {
        return new ChargeDefinition(id(1), code, "Cobranca " + code, ChargeOwner.OWN,
                RevenueNature.TARIFF, CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                null, false, true);
    }

    @Test
    @DisplayName("cobranca propria valida: sem prestador de terceiro")
    void ownDefinitionIsValid() {
        ChargeDefinition def = own("AGUA");
        assertThat(def.owner()).isEqualTo(ChargeOwner.OWN);
        assertThat(def.thirdPartyProviderId()).isNull();
        assertThat(def.isThirdParty()).isFalse();
        assertThat(def.active()).isTrue();
    }

    @Test
    @DisplayName("cobranca de terceiro valida: com prestador identificado")
    void thirdPartyDefinitionIsValid() {
        ChargeDefinition def = new ChargeDefinition(id(2), "CONVENIO_X", "Convenio X",
                ChargeOwner.THIRD_PARTY, RevenueNature.ANCILLARY, CalculationModel.EXTERNAL_VALUE,
                id(900), false, true);
        assertThat(def.isThirdParty()).isTrue();
        assertThat(def.thirdPartyProviderId()).isEqualTo(id(900));
    }

    @Test
    @DisplayName("THIRD_PARTY sem providerId e recusado")
    void thirdPartyWithoutProviderIsRefused() {
        assertThatThrownBy(() -> new ChargeDefinition(id(3), "CONVENIO_X", "Convenio X",
                ChargeOwner.THIRD_PARTY, RevenueNature.ANCILLARY, CalculationModel.EXTERNAL_VALUE,
                null, false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thirdPartyProviderId");
    }

    @Test
    @DisplayName("OWN com providerId e recusado (prestador deve ser nulo)")
    void ownWithProviderIsRefused() {
        assertThatThrownBy(() -> new ChargeDefinition(id(4), "AGUA", "Agua",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                id(900), false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thirdPartyProviderId");
    }

    @Test
    @DisplayName("codigo/nome em branco e campos obrigatorios nulos sao recusados")
    void blankAndNullFieldsAreRefused() {
        assertThatThrownBy(() -> new ChargeDefinition(id(5), " ", "Agua",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChargeDefinition(id(5), "AGUA", " ",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChargeDefinition(null, "AGUA", "Agua",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChargeDefinition(id(5), "AGUA", "Agua",
                null, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChargeDefinition(id(5), "AGUA", "Agua",
                ChargeOwner.OWN, null, CalculationModel.FIXED_PER_UNIT,
                null, false, true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChargeDefinition(id(5), "AGUA", "Agua",
                ChargeOwner.OWN, RevenueNature.TARIFF, null,
                null, false, true)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("homologationRequired e active viajam na definicao")
    void flagsAreCarried() {
        ChargeDefinition def = new ChargeDefinition(id(6), "SERVICO_X", "Servico X",
                ChargeOwner.OWN, RevenueNature.COMPLEMENTARY, CalculationModel.CATALOG_PRICE,
                null, true, false);
        assertThat(def.homologationRequired()).isTrue();
        assertThat(def.active()).isFalse();
    }

    // ------------------------------------------------------------------
    // Correcao da Camada 1: campos novos (desenho da Camada 0, secao 1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("construtor de compatibilidade: municipalityIbge nulo (escopo empresa), fieldCalculable falso, accountingKeyRef nulo")
    void compatibilityConstructorDefaultsNewFields() {
        ChargeDefinition def = own("AGUA");
        assertThat(def.municipalityIbge()).isNull();
        assertThat(def.hasMunicipalScope()).isFalse();
        assertThat(def.fieldCalculable()).isFalse();
        assertThat(def.accountingKeyRef()).isNull();
    }

    @Test
    @DisplayName("municipalityIbge valido (7 digitos) e preservado; construtor sem escopo delega com nulo (empresa)")
    void municipalityIbgeIsPreservedWhenInformed() {
        ChargeDefinition scoped = new ChargeDefinition(id(7), "TAXA_ESCOPO", "Taxa com escopo",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, false, null, "1234567");
        assertThat(scoped.municipalityIbge()).isEqualTo("1234567");
        assertThat(scoped.hasMunicipalScope()).isTrue();

        ChargeDefinition unscoped = new ChargeDefinition(id(8), "TAXA_SEM_ESCOPO", "Taxa sem escopo",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, true, "DEPARA_1");
        assertThat(unscoped.municipalityIbge()).isNull();
        assertThat(unscoped.hasMunicipalScope()).isFalse();
        assertThat(unscoped.fieldCalculable()).isTrue();
        assertThat(unscoped.accountingKeyRef()).isEqualTo("DEPARA_1");
    }

    // ------------------------------------------------------------------
    // Reconciliacao do municipio (item 45 / D-01): IBGE invalido recusado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("municipalityIbge com 6 digitos e recusado (IBGE tem exactamente 7)")
    void municipalityIbgeWithSixDigitsIsRefused() {
        assertThatThrownBy(() -> new ChargeDefinition(id(12), "TAXA_IBGE", "Taxa IBGE",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, false, null, "123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("municipalityIbge");
    }

    @Test
    @DisplayName("municipalityIbge com 8 digitos e recusado (IBGE tem exactamente 7)")
    void municipalityIbgeWithEightDigitsIsRefused() {
        assertThatThrownBy(() -> new ChargeDefinition(id(13), "TAXA_IBGE", "Taxa IBGE",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, false, null, "12345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("municipalityIbge");
    }

    @Test
    @DisplayName("municipalityIbge com letras, branco ou vazio e recusado (so digitos numericos)")
    void municipalityIbgeWithLettersOrBlankIsRefused() {
        assertThatThrownBy(() -> new ChargeDefinition(id(14), "TAXA_IBGE", "Taxa IBGE",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, false, null, "1234A67"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("municipalityIbge");
        assertThatThrownBy(() -> new ChargeDefinition(id(14), "TAXA_IBGE", "Taxa IBGE",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, false, null, "       "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("municipalityIbge");
        assertThatThrownBy(() -> new ChargeDefinition(id(14), "TAXA_IBGE", "Taxa IBGE",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, false, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("municipalityIbge");
    }

    @Test
    @DisplayName("fieldCalculable verdadeiro e preservado na forma canonica")
    void fieldCalculableIsPreservedWhenTrue() {
        ChargeDefinition def = new ChargeDefinition(id(9), "AGUA", "Agua",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                null, false, true, true, null, null);
        assertThat(def.fieldCalculable()).isTrue();
    }

    @Test
    @DisplayName("accountingKeyRef: nulo permitido e valor preservado (referencia opaca)")
    void accountingKeyRefNullAllowedAndValuePreserved() {
        ChargeDefinition without = new ChargeDefinition(id(10), "AGUA", "Agua",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                null, false, true, false, null, null);
        assertThat(without.accountingKeyRef()).isNull();

        ChargeDefinition with = new ChargeDefinition(id(11), "AGUA", "Agua",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                null, false, true, false, "CHAVE_CONTABIL_X", null);
        assertThat(with.accountingKeyRef()).isEqualTo("CHAVE_CONTABIL_X");
    }
}
