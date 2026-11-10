package br.com.ziipsan.commercial.billing.catalog;

import br.com.ziipsan.commercial.billingrules.catalog.ChargeVersion.ChargeParameters;
import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeVersion;
import br.com.ziipsan.commercial.billingrules.servicecatalog.RevenueNature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registro do catalogo (Camada 1, proposta secoes 2.1/2.2): resolucao
 * por data de referencia nas fronteiras, troca de versao e as recusas
 * de montagem (sobreposicao, duplicidade, definicao inexistente);
 * na correcao da Camada 1, tambem a atomicidade do add (C1-01); na
 * reconciliacao do municipio (item 45), a unicidade por escopo
 * (coexistencia do mesmo codigo em escopos diferentes, duplicidade
 * no mesmo escopo) e a resolucao por codigo sensivel a escopo.
 */
class ChargeCatalogTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate JUN_END = LocalDate.of(2026, 6, 30);
    private static final LocalDate JUL = LocalDate.of(2026, 7, 1);

    private static ChargeDefinition fixedDef(UUID id, String code, boolean active) {
        return new ChargeDefinition(id, code, "Cobranca " + code, ChargeOwner.OWN,
                RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT, null, false, active);
    }

    private static ChargeVersion fixedVersion(UUID defId, int version, LocalDate from, LocalDate to, String amount) {
        return new ChargeVersion(UUID.randomUUID(), defId, version, from, to,
                CalculationModel.FIXED_PER_UNIT,
                new ChargeParameters(null, new BigDecimal(amount), null));
    }

    /** Catalogo com a cobranca TAXA_X em duas versoes que se sucedem sem sobreposicao. */
    private static ChargeCatalog catalogWithTwoVersions() {
        UUID defId = ChargeDefinitionTest.id(10);
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(fixedDef(defId, "TAXA_X", true), fixedVersion(defId, 1, JAN, JUN_END, "10.00"));
        catalog.addVersion(fixedVersion(defId, 2, JUL, null, "12.00"));
        return catalog;
    }

    @Test
    @DisplayName("resolucao por data de referencia nas fronteiras de vigencia")
    void resolutionAtValidityBoundaries() {
        ChargeCatalog catalog = catalogWithTwoVersions();
        UUID defId = ChargeDefinitionTest.id(10);

        assertThat(catalog.resolveVersion(defId, JAN.minusDays(1)).isEmpty()).isTrue();
        assertThat(catalog.resolveVersion(defId, JAN).orElseThrow().version()).isEqualTo(1);
        assertThat(catalog.resolveVersion(defId, JUN_END).orElseThrow().version()).isEqualTo(1);
        assertThat(catalog.resolveVersion(defId, JUL).orElseThrow().version()).isEqualTo(2);
        assertThat(catalog.resolveVersion(defId, LocalDate.of(2030, 5, 5)).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    @DisplayName("troca de versao: cada data resolve a sua versao, por id e por codigo")
    void versionSwitchByIdAndCode() {
        ChargeCatalog catalog = catalogWithTwoVersions();
        UUID defId = ChargeDefinitionTest.id(10);

        assertThat(catalog.resolve(defId, LocalDate.of(2026, 3, 1)).orElseThrow()
                .version().parameters().fixedAmount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(catalog.resolveByCode("TAXA_X", LocalDate.of(2026, 9, 1)).orElseThrow()
                .version().parameters().fixedAmount()).isEqualTo(new BigDecimal("12.00"));
        assertThat(catalog.resolveVersionByCode("TAXA_X", LocalDate.of(2026, 9, 1)).orElseThrow().version())
                .isEqualTo(2);
        assertThat(catalog.resolveByCode("INEXISTENTE", JUL).isEmpty()).isTrue();
        assertThat(catalog.resolve(ChargeDefinitionTest.id(999), JUL).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("sobreposicao de vigencias da mesma cobranca e recusada; encostadas sao aceitas")
    void overlappingValidityIsRefused() {
        UUID defId = ChargeDefinitionTest.id(11);
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(fixedDef(defId, "TAXA_Y", true), fixedVersion(defId, 1, JAN, JUN_END, "10.00"));

        // sobreposicao parcial
        assertThatThrownBy(() -> catalog.addVersion(fixedVersion(defId, 2, LocalDate.of(2026, 6, 1), null, "11.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobrepostas");
        // versao aberta sobreposta a qualquer futura
        assertThatThrownBy(() -> catalog.addVersion(fixedVersion(defId, 2, LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31), "11.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobrepostas");
        // encostada (comeca no dia seguinte ao fim) e aceita
        catalog.addVersion(fixedVersion(defId, 2, JUL, null, "11.00"));
        assertThat(catalog.versionsOf(defId)).hasSize(2);
    }

    @Test
    @DisplayName("versao duplicada (definicao, versao) e recusada mesmo sem sobreposicao")
    void duplicateVersionIsRefused() {
        UUID defId = ChargeDefinitionTest.id(12);
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(fixedDef(defId, "TAXA_Z", true), fixedVersion(defId, 1, JAN, JUN_END, "10.00"));

        assertThatThrownBy(() -> catalog.addVersion(fixedVersion(defId, 1, JUL, null, "11.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicada");
    }

    @Test
    @DisplayName("versao de definicao inexistente e recusada")
    void versionOfUnknownDefinitionIsRefused() {
        ChargeCatalog catalog = new ChargeCatalog();
        UUID unknown = ChargeDefinitionTest.id(777);
        assertThatThrownBy(() -> catalog.addVersion(fixedVersion(unknown, 1, JAN, null, "10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inexistente");

        ChargeDefinition def = fixedDef(ChargeDefinitionTest.id(13), "TAXA_W", true);
        assertThatThrownBy(() -> catalog.add(def, fixedVersion(unknown, 1, JAN, null, "10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inexistente");
    }

    @Test
    @DisplayName("definicao duplicada (id ou codigo) e modelo divergente sao recusados")
    void duplicateDefinitionAndDivergentModelAreRefused() {
        UUID defId = ChargeDefinitionTest.id(14);
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(fixedDef(defId, "TAXA_V", true), fixedVersion(defId, 1, JAN, null, "10.00"));

        assertThatThrownBy(() -> catalog.addDefinition(fixedDef(defId, "OUTRA", true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicada");
        assertThatThrownBy(() -> catalog.addDefinition(fixedDef(ChargeDefinitionTest.id(15), "TAXA_V", true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicada");

        // vigencia encostada para isolar a recusa por modelo (nao por sobreposicao)
        ChargeCatalog other = new ChargeCatalog();
        UUID otherId = ChargeDefinitionTest.id(16);
        other.add(fixedDef(otherId, "TAXA_U", true), fixedVersion(otherId, 1, JAN, JUN_END, "10.00"));
        ChargeVersion divergent2 = new ChargeVersion(UUID.randomUUID(), otherId, 2, JUL, null,
                CalculationModel.PERCENT_OF_BASE,
                new ChargeParameters(new BigDecimal("5"), null, null));
        assertThatThrownBy(() -> other.addVersion(divergent2))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("diverge");
    }

    @Test
    @DisplayName("C1-01: add com versao invalida e atomico — a definicao NAO fica registrada")
    void addWithInvalidVersionIsAtomic() {
        ChargeCatalog catalog = new ChargeCatalog();
        UUID defId = ChargeDefinitionTest.id(40);
        ChargeDefinition def = fixedDef(defId, "TAXA_ATOMICA", true);
        ChargeVersion divergent = new ChargeVersion(UUID.randomUUID(), defId, 1, JAN, null,
                CalculationModel.PERCENT_OF_BASE,
                new ChargeParameters(new BigDecimal("5"), null, null));

        assertThatThrownBy(() -> catalog.add(def, divergent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diverge");

        assertThat(catalog.definitions()).isEmpty();
        assertThat(catalog.listActive(JAN)).isEmpty();
        assertThat(catalog.resolve(defId, JAN)).isEmpty();
        assertThat(catalog.resolveByCode("TAXA_ATOMICA", JAN)).isEmpty();
        assertThat(catalog.versionsOf(defId)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Reconciliacao do municipio (item 45 / D-01.2, PM-03):
    // unicidade por (codigo, escopo) e resolucao por codigo por escopo
    // ------------------------------------------------------------------

    private static ChargeDefinition scopedFixedDef(UUID id, String code, String municipalityIbge) {
        return new ChargeDefinition(id, code, "Cobranca " + code, ChargeOwner.OWN,
                RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, false, null, municipalityIbge);
    }

    @Test
    @DisplayName("mesmo codigo coexiste em 3 escopos: empresa, municipio A e municipio B")
    void sameCodeCoexistsInThreeScopes() {
        ChargeCatalog catalog = new ChargeCatalog();
        UUID companyId = ChargeDefinitionTest.id(50);
        UUID munAId = ChargeDefinitionTest.id(51);
        UUID munBId = ChargeDefinitionTest.id(52);
        catalog.add(scopedFixedDef(companyId, "TAXA_ESCOPO", null),
                fixedVersion(companyId, 1, JAN, null, "10.00"));
        catalog.add(scopedFixedDef(munAId, "TAXA_ESCOPO", "1111111"),
                fixedVersion(munAId, 1, JAN, null, "12.00"));
        catalog.add(scopedFixedDef(munBId, "TAXA_ESCOPO", "2222222"),
                fixedVersion(munBId, 1, JAN, null, "15.00"));

        assertThat(catalog.definitions()).hasSize(3);
        LocalDate date = LocalDate.of(2026, 3, 1);
        assertThat(catalog.resolveByCode("TAXA_ESCOPO", null, date).orElseThrow()
                .definition().id()).isEqualTo(companyId);
        assertThat(catalog.resolveByCode("TAXA_ESCOPO", "1111111", date).orElseThrow()
                .definition().id()).isEqualTo(munAId);
        assertThat(catalog.resolveByCode("TAXA_ESCOPO", "2222222", date).orElseThrow()
                .definition().id()).isEqualTo(munBId);
    }

    @Test
    @DisplayName("duplicada no MESMO escopo e recusada: municipal x municipal e empresa (NULL) x empresa (NULL)")
    void duplicateInSameScopeIsRefused() {
        ChargeCatalog catalog = new ChargeCatalog();
        UUID munAId = ChargeDefinitionTest.id(53);
        catalog.add(scopedFixedDef(munAId, "TAXA_DUP", "1111111"),
                fixedVersion(munAId, 1, JAN, null, "10.00"));
        assertThatThrownBy(() -> catalog.addDefinition(
                scopedFixedDef(ChargeDefinitionTest.id(54), "TAXA_DUP", "1111111")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicada");

        ChargeCatalog companyCatalog = new ChargeCatalog();
        UUID companyId = ChargeDefinitionTest.id(55);
        companyCatalog.add(scopedFixedDef(companyId, "TAXA_DUP", null),
                fixedVersion(companyId, 1, JAN, null, "10.00"));
        assertThatThrownBy(() -> companyCatalog.addDefinition(
                scopedFixedDef(ChargeDefinitionTest.id(56), "TAXA_DUP", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicada");
    }

    @Test
    @DisplayName("resolveByCode por escopo: municipal vence, outro municipio e excluido, nulo so ve empresa")
    void resolveByCodeAppliesScopePrecedenceAndExclusion() {
        ChargeCatalog catalog = new ChargeCatalog();
        UUID companyId = ChargeDefinitionTest.id(57);
        UUID munAId = ChargeDefinitionTest.id(58);
        catalog.add(scopedFixedDef(companyId, "TAXA_PREC", null),
                fixedVersion(companyId, 1, JAN, null, "10.00"));
        catalog.add(scopedFixedDef(munAId, "TAXA_PREC", "1111111"),
                fixedVersion(munAId, 1, JAN, null, "12.00"));

        LocalDate date = LocalDate.of(2026, 3, 1);
        // municipio A: a definicao municipal vence a de empresa
        assertThat(catalog.resolveByCode("TAXA_PREC", "1111111", date).orElseThrow()
                .version().parameters().fixedAmount()).isEqualTo(new BigDecimal("12.00"));
        // municipio sem definicao propria: a municipal de A e excluida,
        // resolve a de empresa (fallback)
        assertThat(catalog.resolveByCode("TAXA_PREC", "9999999", date).orElseThrow()
                .version().parameters().fixedAmount()).isEqualTo(new BigDecimal("10.00"));
        // sem municipio derivado: so o escopo empresa
        assertThat(catalog.resolveByCode("TAXA_PREC", null, date).orElseThrow()
                .version().parameters().fixedAmount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(catalog.resolveByCode("TAXA_PREC", date).orElseThrow()
                .version().parameters().fixedAmount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(catalog.resolveVersionByCode("TAXA_PREC", "1111111", date).orElseThrow()
                .parameters().fixedAmount()).isEqualTo(new BigDecimal("12.00"));
    }

    @Test
    @DisplayName("listActive: so definicoes ativas com versao vigente, ordenadas por codigo")
    void listActiveFiltersAndSorts() {
        UUID activeId = ChargeDefinitionTest.id(20);
        UUID inactiveId = ChargeDefinitionTest.id(21);
        UUID noVersionId = ChargeDefinitionTest.id(22);
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(fixedDef(activeId, "B_TAXA", true), fixedVersion(activeId, 1, JAN, null, "10.00"));
        catalog.add(fixedDef(inactiveId, "A_TAXA", false), fixedVersion(inactiveId, 1, JAN, null, "10.00"));
        catalog.add(fixedDef(noVersionId, "C_TAXA", true),
                fixedVersion(noVersionId, 1, LocalDate.of(2027, 1, 1), null, "10.00"));

        List<ChargeResolution> active = catalog.listActive(LocalDate.of(2026, 6, 1));
        assertThat(active).extracting(r -> r.definition().code()).containsExactly("B_TAXA");
    }

    @Test
    @DisplayName("listas devolvidas sao imutaveis")
    void returnedListsAreImmutable() {
        ChargeCatalog catalog = catalogWithTwoVersions();
        UUID defId = ChargeDefinitionTest.id(10);
        LocalDate date = LocalDate.of(2026, 3, 1);

        assertThatThrownBy(() -> catalog.listActive(date).add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.definitions().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.versionsOf(defId).add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("construtor em lote monta o registro e aplica as mesmas recusas")
    void bulkConstructorAppliesSameRules() {
        UUID defId = ChargeDefinitionTest.id(30);
        ChargeDefinition def = fixedDef(defId, "TAXA_LOTE", true);
        ChargeVersion v1 = fixedVersion(defId, 1, JAN, null, "10.00");
        ChargeCatalog catalog = new ChargeCatalog(List.of(def), List.of(v1));
        assertThat(catalog.resolveVersion(defId, JAN).isPresent()).isTrue();

        ChargeVersion overlap = fixedVersion(defId, 2, LocalDate.of(2026, 6, 1), null, "11.00");
        assertThatThrownBy(() -> new ChargeCatalog(List.of(def), List.of(v1, overlap)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobrepostas");
    }
}
