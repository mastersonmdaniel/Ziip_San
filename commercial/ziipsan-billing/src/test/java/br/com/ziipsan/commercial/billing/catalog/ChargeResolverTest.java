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

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resolvedor por unidade/competencia (correcao da Camada 1, desenho
 * da Camada 0, secao 1; reconciliacao do municipio pos-item 45,
 * adendo D-02): ordenacao por codigo, determinismo, lista vazia fora
 * de vigencia, falha fechada em ambiguidade no mesmo escopo
 * (INV-C1-04-v2) e precedencia municipal sobre empresa. A referencia
 * de unidade e opaca e nao interpretada; o municipio da unidade
 * chega derivado pelo chamador.
 */
class ChargeResolverTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final String UNIT = "UNIDADE_OPACA_1";
    private static final String MUN_A = "1111111";
    private static final String MUN_B = "2222222";

    private static ChargeDefinition fixedDef(UUID id, String code, boolean active) {
        return new ChargeDefinition(id, code, "Cobranca " + code, ChargeOwner.OWN,
                RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT, null, false, active);
    }

    private static ChargeDefinition scopedFixedDef(UUID id, String code, String municipalityIbge) {
        return new ChargeDefinition(id, code, "Cobranca " + code, ChargeOwner.OWN,
                RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, false, null, municipalityIbge);
    }

    private static ChargeVersion fixedVersion(UUID defId, int version, LocalDate from, LocalDate to) {
        return fixedVersion(defId, version, from, to, "10.00");
    }

    private static ChargeVersion fixedVersion(UUID defId, int version, LocalDate from, LocalDate to,
                                              String amount) {
        return new ChargeVersion(UUID.randomUUID(), defId, version, from, to,
                CalculationModel.FIXED_PER_UNIT,
                new ChargeParameters(null, new BigDecimal(amount), null));
    }

    private static ChargeCatalog catalogWithThreeCharges() {
        ChargeCatalog catalog = new ChargeCatalog();
        UUID cId = ChargeDefinitionTest.id(70);
        UUID aId = ChargeDefinitionTest.id(71);
        UUID bId = ChargeDefinitionTest.id(72);
        catalog.add(fixedDef(cId, "C_TAXA", true), fixedVersion(cId, 1, JAN, null));
        catalog.add(fixedDef(aId, "A_TAXA", true), fixedVersion(aId, 1, JAN, null));
        catalog.add(fixedDef(bId, "B_TAXA", true), fixedVersion(bId, 1, JAN, null));
        return catalog;
    }

    @Test
    @DisplayName("resolve devolve as vigentes ordenadas por codigo, como o listActive do catalogo (so escopo empresa)")
    void resolveOrdersByCode() {
        ChargeCatalog catalog = catalogWithThreeCharges();
        ChargeResolver resolver = new ChargeResolver(catalog);
        LocalDate competence = LocalDate.of(2026, 6, 1);

        List<ChargeResolution> resolved = resolver.resolve(UNIT, null, competence);
        assertThat(resolved).extracting(r -> r.definition().code())
                .containsExactly("A_TAXA", "B_TAXA", "C_TAXA");
        assertThat(resolved).isEqualTo(catalog.listActive(competence));
    }

    @Test
    @DisplayName("resolve e deterministico: duas execucoes devolvem a mesma lista")
    void resolveIsDeterministic() {
        ChargeResolver resolver = new ChargeResolver(catalogWithThreeCharges());
        LocalDate competence = LocalDate.of(2026, 6, 1);

        List<ChargeResolution> first = resolver.resolve(UNIT, null, competence);
        List<ChargeResolution> second = resolver.resolve(UNIT, null, competence);
        assertThat(second).isEqualTo(first);
        assertThatThrownBy(() -> first.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("resolve fora de qualquer vigencia devolve lista vazia")
    void resolveEmptyOutsideValidity() {
        ChargeResolver resolver = new ChargeResolver(catalogWithThreeCharges());
        assertThat(resolver.resolve(UNIT, null, LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    @DisplayName("ambiguidade no mesmo escopo (duas versoes vigentes vindas de fora do add) falha fechada, nunca escolhe")
    void resolveFailsClosedOnAmbiguity() throws Exception {
        UUID defId = ChargeDefinitionTest.id(73);
        ChargeVersion v1 = fixedVersion(defId, 1, JAN, null);
        ChargeVersion v2 = fixedVersion(defId, 2, LocalDate.of(2026, 7, 1), null);
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(fixedDef(defId, "TAXA_AMBIGUA", true), v1);

        // Simula dados que NAO passaram pelas recusas do add (ex.:
        // leitura directa do banco): injeta a versao sobreposta no
        // mapa interno do catalogo. Pelos metodos add, a sobreposicao
        // seria recusada e esta situacao nao existiria.
        Field field = ChargeCatalog.class.getDeclaredField("versionsByDefinition");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, List<ChargeVersion>> versions = (Map<UUID, List<ChargeVersion>>) field.get(catalog);
        versions.put(defId, List.of(v1, v2));

        ChargeResolver resolver = new ChargeResolver(catalog);
        assertThatThrownBy(() -> resolver.resolve(UNIT, null, LocalDate.of(2026, 9, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ambiguidade");
    }

    // ------------------------------------------------------------------
    // Precedencia municipal sobre empresa (INV-C1-04-v2 / D-02.2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("precedencia: a definicao municipal do municipio da UC vence a de empresa para o mesmo codigo")
    void resolveMunicipalScopeWinsOverCompany() {
        ChargeCatalog catalog = new ChargeCatalog();
        UUID companyId = ChargeDefinitionTest.id(80);
        UUID munAId = ChargeDefinitionTest.id(81);
        catalog.add(scopedFixedDef(companyId, "TAXA_X", null),
                fixedVersion(companyId, 1, JAN, null, "10.00"));
        catalog.add(scopedFixedDef(munAId, "TAXA_X", MUN_A),
                fixedVersion(munAId, 1, JAN, null, "12.00"));

        ChargeResolver resolver = new ChargeResolver(catalog);
        List<ChargeResolution> resolved = resolver.resolve(UNIT, MUN_A, LocalDate.of(2026, 6, 1));
        // Uma so resolucao para o codigo: a municipal; a de empresa e ignorada.
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).definition().id()).isEqualTo(munAId);
        assertThat(resolved.get(0).definition().municipalityIbge()).isEqualTo(MUN_A);
        assertThat(resolved.get(0).version().parameters().fixedAmount())
                .isEqualTo(new BigDecimal("12.00"));
    }

    @Test
    @DisplayName("fallback: sem definicao municipal vigente para o municipio da UC, resolve a de empresa")
    void resolveFallsBackToCompanyWhenNoMunicipal() {
        // Caso 1: nao ha definicao do municipio da UC (so de outro municipio).
        ChargeCatalog catalog = new ChargeCatalog();
        UUID companyId = ChargeDefinitionTest.id(82);
        UUID munBId = ChargeDefinitionTest.id(83);
        catalog.add(scopedFixedDef(companyId, "TAXA_X", null),
                fixedVersion(companyId, 1, JAN, null, "10.00"));
        catalog.add(scopedFixedDef(munBId, "TAXA_X", MUN_B),
                fixedVersion(munBId, 1, JAN, null, "15.00"));

        ChargeResolver resolver = new ChargeResolver(catalog);
        List<ChargeResolution> resolved = resolver.resolve(UNIT, MUN_A, LocalDate.of(2026, 6, 1));
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).definition().id()).isEqualTo(companyId);
        assertThat(resolved.get(0).version().parameters().fixedAmount())
                .isEqualTo(new BigDecimal("10.00"));

        // Caso 2: ha definicao municipal da UC, mas sem versao vigente
        // na competencia — a precedencia e por versao vigente (D-02.2),
        // logo cai para a de empresa.
        ChargeCatalog notYet = new ChargeCatalog();
        UUID company2Id = ChargeDefinitionTest.id(84);
        UUID munA2Id = ChargeDefinitionTest.id(85);
        notYet.add(scopedFixedDef(company2Id, "TAXA_X", null),
                fixedVersion(company2Id, 1, JAN, null, "10.00"));
        notYet.add(scopedFixedDef(munA2Id, "TAXA_X", MUN_A),
                fixedVersion(munA2Id, 1, LocalDate.of(2027, 1, 1), null, "12.00"));

        List<ChargeResolution> fallback = new ChargeResolver(notYet)
                .resolve(UNIT, MUN_A, LocalDate.of(2026, 6, 1));
        assertThat(fallback).hasSize(1);
        assertThat(fallback.get(0).definition().id()).isEqualTo(company2Id);
    }

    @Test
    @DisplayName("outro municipio excluido: codigo que so existe noutro municipio nao resolve para esta UC")
    void resolveExcludesOtherMunicipalities() {
        ChargeCatalog catalog = new ChargeCatalog();
        UUID munBId = ChargeDefinitionTest.id(86);
        catalog.add(scopedFixedDef(munBId, "TAXA_SO_B", MUN_B),
                fixedVersion(munBId, 1, JAN, null, "15.00"));

        ChargeResolver resolver = new ChargeResolver(catalog);
        LocalDate competence = LocalDate.of(2026, 6, 1);
        // Nem vence, nem bloqueia, nem aparece: lista vazia para A e para empresa.
        assertThat(resolver.resolve(UNIT, MUN_A, competence)).isEmpty();
        assertThat(resolver.resolve(UNIT, null, competence)).isEmpty();
        // E resolve normalmente para a UC do seu proprio municipio.
        List<ChargeResolution> own = resolver.resolve(UNIT, MUN_B, competence);
        assertThat(own).hasSize(1);
        assertThat(own.get(0).definition().id()).isEqualTo(munBId);
    }
}
