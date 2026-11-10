package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeCatalog;
import br.com.ziipsan.commercial.billing.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolver;
import br.com.ziipsan.commercial.billing.catalog.ChargeVersion;
import br.com.ziipsan.commercial.billing.catalog.ChargeVersion.ChargeParameters;
import br.com.ziipsan.commercial.billing.servicecatalog.RevenueNature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resolucao de campo por unidade (Camada 4, D-06): politica de
 * calculabilidade (modelos reproduzidos pelo calculador vigente +
 * marcacao), exclusoes estruturais (convenio nunca no aparelho,
 * INV-C4-02), minimo por unidade (INV-C4-03) e resolucao pelo
 * municipio da UC (item 45: precedencia municipal, fallback empresa,
 * outro municipio fora).
 */
class FieldChargeResolverTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate COMPETENCE = LocalDate.of(2026, 6, 1);
    private static final String UNIT = "UNIDADE_OPACA_1";
    private static final String MUN_A = "1111111";
    private static final String MUN_B = "2222222";
    private static final UUID TARIFF_V1 = id(900);
    private static final UUID TARIFF_V2 = id(901);

    static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    // ---------- fabricas ----------

    private static ChargeDefinition def(UUID defId, String code, CalculationModel model,
                                        ChargeOwner owner, boolean fieldCalculable, String scope) {
        UUID provider = owner == ChargeOwner.THIRD_PARTY ? id(777) : null;
        return new ChargeDefinition(defId, code, "Cobranca " + code, owner,
                RevenueNature.TARIFF, model, provider, false, true,
                fieldCalculable, null, scope);
    }

    private static ChargeVersion version(UUID defId, CalculationModel model) {
        return switch (model) {
            case PROGRESSIVE_BY_CONSUMPTION -> new ChargeVersion(UUID.randomUUID(), defId, 1,
                    JAN, null, model, TARIFF_V1, null, null, ChargeParameters.empty());
            case DERIVED_FROM_VOLUME -> new ChargeVersion(UUID.randomUUID(), defId, 1,
                    JAN, null, model,
                    new ChargeParameters(new BigDecimal("80"), null, null));
            case FIXED_PER_UNIT -> new ChargeVersion(UUID.randomUUID(), defId, 1,
                    JAN, null, model,
                    new ChargeParameters(null, new BigDecimal("12.50"), null));
            case CATALOG_PRICE -> new ChargeVersion(UUID.randomUUID(), defId, 1,
                    JAN, null, model, null, "ITEM-1", null, ChargeParameters.empty());
            case PERCENT_OF_BASE -> new ChargeVersion(UUID.randomUUID(), defId, 1,
                    JAN, null, model,
                    new ChargeParameters(new BigDecimal("5"), null, null));
            case EXTERNAL_VALUE -> new ChargeVersion(UUID.randomUUID(), defId, 1,
                    JAN, null, model, null, null, id(778), ChargeParameters.empty());
        };
    }

    private static ChargeCatalog catalogOf(ChargeDefinition... definitions) {
        ChargeCatalog catalog = new ChargeCatalog();
        for (ChargeDefinition d : definitions) {
            catalog.add(d, version(d.id(), d.calculationModel()));
        }
        return catalog;
    }

    private static FieldChargeResolver fieldResolver(ChargeCatalog catalog) {
        return new FieldChargeResolver(new ChargeResolver(catalog));
    }

    private static List<String> codes(List<ChargeResolution> resolved) {
        return resolved.stream().map(r -> r.definition().code()).toList();
    }

    // ---------- politica: o que entra ----------

    @Test
    @DisplayName("progressiva, derivada e fixa marcadas entram na resolucao de campo")
    void reproducibleMarkedModelsEnter() {
        ChargeCatalog catalog = catalogOf(
                def(id(1), "AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, ChargeOwner.OWN, true, null),
                def(id(2), "ESGOTO", CalculationModel.DERIVED_FROM_VOLUME, ChargeOwner.OWN, true, null),
                def(id(3), "TFDI", CalculationModel.FIXED_PER_UNIT, ChargeOwner.OWN, true, null));

        List<ChargeResolution> resolved = fieldResolver(catalog)
                .resolveFieldCharges(UNIT, null, COMPETENCE);

        assertThat(codes(resolved)).containsExactly("AGUA", "ESGOTO", "TFDI");
    }

    @Test
    @DisplayName("modelo reproduzivel SEM marcacao fieldCalculable fica fora")
    void unmarkedReproducibleStaysOut() {
        ChargeCatalog catalog = catalogOf(
                def(id(1), "AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, ChargeOwner.OWN, false, null),
                def(id(3), "TFDI", CalculationModel.FIXED_PER_UNIT, ChargeOwner.OWN, true, null));

        assertThat(codes(fieldResolver(catalog).resolveFieldCharges(UNIT, null, COMPETENCE)))
                .containsExactly("TFDI");
    }

    // ---------- politica: exclusoes estruturais (INV-C4-02) ----------

    @Test
    @DisplayName("INV-C4-02: EXTERNAL_VALUE fica fora SEMPRE, mesmo marcado fieldCalculable")
    void externalValueAlwaysOut() {
        ChargeCatalog catalog = catalogOf(
                def(id(1), "AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, ChargeOwner.OWN, true, null),
                def(id(4), "CONVENIO_RESIDUOS", CalculationModel.EXTERNAL_VALUE,
                        ChargeOwner.THIRD_PARTY, true, null));

        List<ChargeResolution> resolved = fieldResolver(catalog)
                .resolveFieldCharges(UNIT, null, COMPETENCE);

        assertThat(codes(resolved)).containsExactly("AGUA");
        assertThat(resolved).noneMatch(r -> r.definition().calculationModel()
                == CalculationModel.EXTERNAL_VALUE);
    }

    @Test
    @DisplayName("INV-C4-02: dono THIRD_PARTY fica fora mesmo com modelo reproduzivel e marcacao")
    void thirdPartyOwnerAlwaysOut() {
        ChargeCatalog catalog = catalogOf(
                def(id(1), "AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, ChargeOwner.OWN, true, null),
                def(id(5), "TAXA_TERCEIRO", CalculationModel.FIXED_PER_UNIT,
                        ChargeOwner.THIRD_PARTY, true, null));

        List<ChargeResolution> resolved = fieldResolver(catalog)
                .resolveFieldCharges(UNIT, null, COMPETENCE);

        assertThat(codes(resolved)).containsExactly("AGUA");
        assertThat(resolved).noneMatch(r -> r.definition().owner() == ChargeOwner.THIRD_PARTY);
    }

    @Test
    @DisplayName("PERCENT_OF_BASE fica fora (calculador vigente nao reproduz) mesmo marcado [A DEFINIR]")
    void percentOfBaseOut() {
        ChargeCatalog catalog = catalogOf(
                def(id(1), "AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, ChargeOwner.OWN, true, null),
                def(id(6), "JUROS_BASE", CalculationModel.PERCENT_OF_BASE, ChargeOwner.OWN, true, null));

        assertThat(codes(fieldResolver(catalog).resolveFieldCharges(UNIT, null, COMPETENCE)))
                .containsExactly("AGUA");
    }

    @Test
    @DisplayName("CATALOG_PRICE fica fora (calculador vigente nao reproduz) mesmo marcado [A DEFINIR]")
    void catalogPriceOut() {
        ChargeCatalog catalog = catalogOf(
                def(id(1), "AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, ChargeOwner.OWN, true, null),
                def(id(7), "SERVICO_CATALOGO", CalculationModel.CATALOG_PRICE, ChargeOwner.OWN, true, null));

        assertThat(codes(fieldResolver(catalog).resolveFieldCharges(UNIT, null, COMPETENCE)))
                .containsExactly("AGUA");
    }

    @Test
    @DisplayName("politica directa: matriz modelo x marcacao x dono")
    void policyMatrix() {
        assertThat(FieldCalculablePolicy.isFieldCalculable(
                def(id(1), "A", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, ChargeOwner.OWN, true, null))).isTrue();
        assertThat(FieldCalculablePolicy.isFieldCalculable(
                def(id(1), "A", CalculationModel.DERIVED_FROM_VOLUME, ChargeOwner.OWN, true, null))).isTrue();
        assertThat(FieldCalculablePolicy.isFieldCalculable(
                def(id(1), "A", CalculationModel.FIXED_PER_UNIT, ChargeOwner.OWN, true, null))).isTrue();
        assertThat(FieldCalculablePolicy.isFieldCalculable(
                def(id(1), "A", CalculationModel.FIXED_PER_UNIT, ChargeOwner.OWN, false, null))).isFalse();
        assertThat(FieldCalculablePolicy.isFieldCalculable(
                def(id(1), "A", CalculationModel.EXTERNAL_VALUE, ChargeOwner.OWN, true, null))).isFalse();
        assertThat(FieldCalculablePolicy.isFieldCalculable(
                def(id(1), "A", CalculationModel.PERCENT_OF_BASE, ChargeOwner.OWN, true, null))).isFalse();
        assertThat(FieldCalculablePolicy.isFieldCalculable(
                def(id(1), "A", CalculationModel.CATALOG_PRICE, ChargeOwner.OWN, true, null))).isFalse();
        assertThat(FieldCalculablePolicy.isReproducibleByFieldCalculator(
                CalculationModel.PROGRESSIVE_BY_CONSUMPTION)).isTrue();
        assertThat(FieldCalculablePolicy.isReproducibleByFieldCalculator(
                CalculationModel.EXTERNAL_VALUE)).isFalse();
    }

    // ---------- municipio da UC (item 45 / D-02) ----------

    @Test
    @DisplayName("UC do municipio A resolve a definicao municipal de A (precedencia sobre empresa)")
    void municipalDefinitionWinsForItsMunicipality() {
        ChargeDefinition empresa = def(id(10), "TFDI", CalculationModel.FIXED_PER_UNIT,
                ChargeOwner.OWN, true, null);
        ChargeDefinition municipalA = def(id(11), "TFDI", CalculationModel.FIXED_PER_UNIT,
                ChargeOwner.OWN, true, MUN_A);
        ChargeCatalog catalog = catalogOf(empresa, municipalA);

        List<ChargeResolution> resolved = fieldResolver(catalog)
                .resolveFieldCharges(UNIT, MUN_A, COMPETENCE);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).definition().id()).isEqualTo(id(11));
        assertThat(resolved.get(0).definition().municipalityIbge()).isEqualTo(MUN_A);
    }

    @Test
    @DisplayName("UC sem definicao municipal para o seu municipio cai para a de empresa")
    void fallsBackToCompanyScope() {
        ChargeDefinition empresa = def(id(10), "TFDI", CalculationModel.FIXED_PER_UNIT,
                ChargeOwner.OWN, true, null);
        ChargeDefinition municipalB = def(id(12), "TFDI", CalculationModel.FIXED_PER_UNIT,
                ChargeOwner.OWN, true, MUN_B);
        ChargeCatalog catalog = catalogOf(empresa, municipalB);

        List<ChargeResolution> resolved = fieldResolver(catalog)
                .resolveFieldCharges(UNIT, MUN_A, COMPETENCE);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).definition().id()).isEqualTo(id(10));
        assertThat(resolved.get(0).definition().municipalityIbge()).isNull();
    }

    @Test
    @DisplayName("definicao de OUTRO municipio nao entra: UC de A com so definicao de B resolve vazio nesse codigo")
    void otherMunicipalityNeverEnters() {
        ChargeDefinition municipalB = def(id(12), "TFDI", CalculationModel.FIXED_PER_UNIT,
                ChargeOwner.OWN, true, MUN_B);
        ChargeCatalog catalog = catalogOf(municipalB);

        assertThat(fieldResolver(catalog).resolveFieldCharges(UNIT, MUN_A, COMPETENCE)).isEmpty();
        assertThat(fieldResolver(catalog).resolveFieldCharges(UNIT, null, COMPETENCE)).isEmpty();
    }

    @Test
    @DisplayName("UC sem municipio derivado resolve so o escopo empresa")
    void nullMunicipalityResolvesCompanyOnly() {
        ChargeDefinition empresa = def(id(10), "TFDI", CalculationModel.FIXED_PER_UNIT,
                ChargeOwner.OWN, true, null);
        ChargeDefinition municipalA = def(id(11), "TFDI", CalculationModel.FIXED_PER_UNIT,
                ChargeOwner.OWN, true, MUN_A);
        ChargeCatalog catalog = catalogOf(empresa, municipalA);

        List<ChargeResolution> resolved = fieldResolver(catalog)
                .resolveFieldCharges(UNIT, null, COMPETENCE);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).definition().id()).isEqualTo(id(10));
    }

    // ---------- contrato da lista ----------

    @Test
    @DisplayName("resultado imutavel e deterministico (ordem por codigo da Camada 1 preservada)")
    void resultImmutableAndDeterministic() {
        ChargeCatalog catalog = catalogOf(
                def(id(3), "TFDI", CalculationModel.FIXED_PER_UNIT, ChargeOwner.OWN, true, null),
                def(id(1), "AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, ChargeOwner.OWN, true, null));
        FieldChargeResolver resolver = fieldResolver(catalog);

        List<ChargeResolution> first = resolver.resolveFieldCharges(UNIT, null, COMPETENCE);
        List<ChargeResolution> second = resolver.resolveFieldCharges(UNIT, null, COMPETENCE);

        assertThat(second).isEqualTo(first);
        assertThat(codes(first)).containsExactly("AGUA", "TFDI");
        assertThatThrownBy(() -> first.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("argumentos nulos recusados")
    void nullArgumentsRejected() {
        FieldChargeResolver resolver = fieldResolver(new ChargeCatalog());
        assertThatThrownBy(() -> resolver.resolveFieldCharges(null, null, COMPETENCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.resolveFieldCharges(UNIT, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FieldChargeResolver(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- R9-C4-2: fail-fast do IBGE na fronteira ----------

    @Test
    @DisplayName("R9-C4-2: IBGE malformado (6/8 digitos, letras) recusado fail-fast, sem cair para o escopo empresa")
    void malformedMunicipalityIbgeIsRejectedFailFast() {
        ChargeCatalog catalog = catalogOf(
                def(id(10), "TFDI", CalculationModel.FIXED_PER_UNIT, ChargeOwner.OWN, true, null));
        FieldChargeResolver resolver = fieldResolver(catalog);

        for (String malformed : List.of("123456", "12345678", "12345AB", "ABCDEFG")) {
            assertThatThrownBy(() -> resolver.resolveFieldCharges(UNIT, malformed, COMPETENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("7 digitos");
        }
        // O valor valido continua a resolver (controlo positivo do mesmo teste).
        assertThat(codes(resolver.resolveFieldCharges(UNIT, MUN_A, COMPETENCE)))
                .containsExactly("TFDI");
    }

    @Test
    @DisplayName("R9-C4-2: IBGE em branco ou com espacos recusado (o formato e exactamente 7 digitos)")
    void blankOrPaddedMunicipalityIbgeIsRejected() {
        ChargeCatalog catalog = catalogOf(
                def(id(10), "TFDI", CalculationModel.FIXED_PER_UNIT, ChargeOwner.OWN, true, null));
        FieldChargeResolver resolver = fieldResolver(catalog);

        for (String malformed : List.of("", " ", " 1111111", "1111111 ")) {
            assertThatThrownBy(() -> resolver.resolveFieldCharges(UNIT, malformed, COMPETENCE))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
