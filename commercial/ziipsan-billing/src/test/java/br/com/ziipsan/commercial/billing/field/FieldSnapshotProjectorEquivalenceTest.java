package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeCatalog;
import br.com.ziipsan.commercial.billing.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolver;
import br.com.ziipsan.commercial.billing.catalog.ChargeVersion;
import br.com.ziipsan.commercial.billing.catalog.ChargeVersion.ChargeParameters;
import br.com.ziipsan.commercial.billing.field.FieldSnapshotProjector.FieldTariffData;
import br.com.ziipsan.commercial.billing.servicecatalog.RevenueNature;
import br.com.ziipsan.commercial.billingrules.FieldBillCalculator;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldBillResult;
import br.com.ziipsan.commercial.billingrules.FieldTariffSnapshot;
import br.com.ziipsan.commercial.billingrules.TariffBracket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DIFERENCIAL de projeccao (INV-C4-01, tolerancia zero): o snapshot
 * projectado a partir do catalogo (resolucao por unidade + projector
 * da Camada 4) tem de ser igual, campo a campo, ao snapshot montado
 * pelo caminho directo da tarifa do mesmo escopo — empresa e
 * municipal. Cobre tambem a falha fechada fora do retrato
 * (INV-C4-05), a re-verificacao do convenio (INV-C4-02) e a
 * fotografia estavel (INV-C4-06).
 */
class FieldSnapshotProjectorEquivalenceTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate COMPETENCE = LocalDate.of(2026, 6, 1);
    private static final String UNIT = "UNIDADE_OPACA_1";
    private static final String MUN_A = "1111111";

    private static final UUID TARIFF_EMPRESA = FieldChargeResolverTest.id(900);
    private static final UUID TARIFF_MUN_A = FieldChargeResolverTest.id(901);
    private static final UUID CATEGORY = FieldChargeResolverTest.id(950);

    private static UUID id(int n) {
        return FieldChargeResolverTest.id(n);
    }

    // ---------- massa: catalogo + dados tarifarios ----------

    private static ChargeDefinition aguaDef(UUID defId, UUID tariffVersionId, String scope) {
        ChargeDefinition def = new ChargeDefinition(defId, "AGUA", "Tarifa de agua",
                ChargeOwner.OWN, RevenueNature.TARIFF,
                CalculationModel.PROGRESSIVE_BY_CONSUMPTION, null, false, true,
                true, null, scope);
        return def;
    }

    private static ChargeVersion aguaVersion(UUID defId, UUID tariffVersionId) {
        return new ChargeVersion(UUID.randomUUID(), defId, 1, JAN, null,
                CalculationModel.PROGRESSIVE_BY_CONSUMPTION, tariffVersionId, null, null,
                ChargeParameters.empty());
    }

    private static ChargeDefinition esgotoDef(UUID defId, String scope) {
        return new ChargeDefinition(defId, "ESGOTO", "Esgoto derivado", ChargeOwner.OWN,
                RevenueNature.TARIFF, CalculationModel.DERIVED_FROM_VOLUME, null, false, true,
                true, null, scope);
    }

    private static ChargeVersion esgotoVersion(UUID defId) {
        return new ChargeVersion(UUID.randomUUID(), defId, 1, JAN, null,
                CalculationModel.DERIVED_FROM_VOLUME,
                new ChargeParameters(new BigDecimal("80"), null, null));
    }

    /**
     * Versao derivada sustentada SO pela referencia de tarifa (correccao
     * R-02): canal {@code tariffVersionId} preenchido e {@code percent}
     * nulo — a forma que a Camada 1 admite para DERIVED_FROM_VOLUME sem
     * percentual declarado (ver caracterizacao em
     * {@code derivedSustainedOnlyByDivergentTariffVersionIdIsNotConfronted}).
     */
    private static ChargeVersion esgotoVersionByTariffOnly(UUID defId, UUID tariffVersionId) {
        return new ChargeVersion(UUID.randomUUID(), defId, 1, JAN, null,
                CalculationModel.DERIVED_FROM_VOLUME, tariffVersionId, null, null,
                ChargeParameters.empty());
    }

    private static ChargeDefinition tfdiDef(UUID defId, String scope) {
        return new ChargeDefinition(defId, "TFDI", "Taxa fixa", ChargeOwner.OWN,
                RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT, null, false, true,
                true, null, scope);
    }

    private static ChargeVersion tfdiVersion(UUID defId) {
        return new ChargeVersion(UUID.randomUUID(), defId, 1, JAN, null,
                CalculationModel.FIXED_PER_UNIT,
                new ChargeParameters(null, new BigDecimal("12.50"), null));
    }

    /** Catalogo empresa: AGUA + ESGOTO + TFDI, todos calculaveis em campo. */
    private static ChargeCatalog companyCatalog() {
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(aguaDef(id(1), TARIFF_EMPRESA, null), aguaVersion(id(1), TARIFF_EMPRESA));
        catalog.add(esgotoDef(id(2), null), esgotoVersion(id(2)));
        catalog.add(tfdiDef(id(3), null), tfdiVersion(id(3)));
        return catalog;
    }

    /** Dados da tarifa-empresa (o caminho directo monta o snapshot deles). */
    private static FieldTariffData companyTariffData() {
        return new FieldTariffData(TARIFF_EMPRESA, CATEGORY, 10, new BigDecimal("25.00"),
                List.of(new TariffBracket(10, new BigDecimal("3.00")),
                        TariffBracket.open(new BigDecimal("5.00"))),
                true, new BigDecimal("80"), Map.of("reajuste", "2026-01"));
    }

    /** Snapshot pelo caminho DIRECTO da tarifa (sem catalogo): a referencia. */
    private static FieldTariffSnapshot directSnapshot(FieldTariffData data) {
        return new FieldTariffSnapshot(data.tariffVersionId(), data.categoryId(),
                data.minimumM3PerEconomy(), data.fixedChargePerEconomy(), data.brackets(),
                data.sewerEnabled(), data.sewerPct(), data.parameterVersions());
    }

    private static void assertFieldByFieldEqual(FieldTariffSnapshot projected,
                                                FieldTariffSnapshot direct) {
        assertThat(projected.tariffVersionId()).isEqualTo(direct.tariffVersionId());
        assertThat(projected.categoryId()).isEqualTo(direct.categoryId());
        assertThat(projected.minimumM3PerEconomy()).isEqualTo(direct.minimumM3PerEconomy());
        assertThat(projected.fixedChargePerEconomy())
                .isEqualByComparingTo(direct.fixedChargePerEconomy());
        assertThat(projected.brackets()).isEqualTo(direct.brackets());
        assertThat(projected.sewerEnabled()).isEqualTo(direct.sewerEnabled());
        assertThat(projected.sewerPct()).isEqualByComparingTo(direct.sewerPct());
        assertThat(projected.parameterVersions()).isEqualTo(direct.parameterVersions());
        assertThat(projected).isEqualTo(direct);
    }

    private static List<ChargeResolution> resolveField(ChargeCatalog catalog, String municipality) {
        return new FieldChargeResolver(new ChargeResolver(catalog))
                .resolveFieldCharges(UNIT, municipality, COMPETENCE);
    }

    // ---------- INV-C4-01: diferencial ----------

    @Test
    @DisplayName("INV-C4-01: projectado do catalogo (escopo empresa) == directo da tarifa, campo a campo")
    void equivalenceCompanyScope() {
        FieldTariffData data = companyTariffData();
        FieldTariffSnapshot projected = new FieldSnapshotProjector()
                .project(resolveField(companyCatalog(), null), data);

        assertFieldByFieldEqual(projected, directSnapshot(data));
    }

    @Test
    @DisplayName("INV-C4-01: projectado do catalogo (escopo municipal de A) == directo da tarifa de A")
    void equivalenceMunicipalScope() {
        ChargeCatalog catalog = companyCatalog();
        // Definicoes municipais de A para os mesmos codigos, com OUTRA versao de tarifa.
        catalog.add(aguaDef(id(11), TARIFF_MUN_A, MUN_A), aguaVersion(id(11), TARIFF_MUN_A));
        catalog.add(esgotoDef(id(12), MUN_A), esgotoVersion(id(12)));
        FieldTariffData dataA = new FieldTariffData(TARIFF_MUN_A, CATEGORY, 12,
                new BigDecimal("30.00"),
                List.of(new TariffBracket(10, new BigDecimal("3.50")),
                        TariffBracket.open(new BigDecimal("5.50"))),
                true, new BigDecimal("80"), Map.of("reajuste", "2026-02"));

        List<ChargeResolution> resolved = resolveField(catalog, MUN_A);
        assertThat(resolved.stream().filter(r -> r.definition().code().equals("AGUA")).findFirst()
                .orElseThrow().definition().municipalityIbge()).isEqualTo(MUN_A);

        FieldTariffSnapshot projected = new FieldSnapshotProjector().project(resolved, dataA);
        assertFieldByFieldEqual(projected, directSnapshot(dataA));
    }

    @Test
    @DisplayName("INV-C4-01: UC de A sem definicao municipal projecta o snapshot da tarifa-empresa (fallback)")
    void equivalenceFallbackToCompany() {
        FieldTariffData data = companyTariffData();
        FieldTariffSnapshot projected = new FieldSnapshotProjector()
                .project(resolveField(companyCatalog(), MUN_A), data);

        assertFieldByFieldEqual(projected, directSnapshot(data));
    }

    @Test
    @DisplayName("INV-C4-01 via calculador: conta de campo do projectado == conta do directo (ED-19)")
    void equivalenceThroughFieldCalculator() {
        FieldTariffData data = companyTariffData();
        FieldSnapshotProjector projector = new FieldSnapshotProjector();
        FieldTariffSnapshot projected = projector.project(resolveField(companyCatalog(), null), data);
        FieldTariffSnapshot direct = directSnapshot(data);

        FieldBillInput inProjected = new FieldBillInput(id(700), id(701), id(702),
                100, 135, 5, 1, projected, List.of(), List.of(), LocalDate.of(2026, 7, 10));
        FieldBillInput inDirect = new FieldBillInput(id(700), id(701), id(702),
                100, 135, 5, 1, direct, List.of(), List.of(), LocalDate.of(2026, 7, 10));

        FieldBillResult rProjected = FieldBillCalculator.calculate(inProjected);
        FieldBillResult rDirect = FieldBillCalculator.calculate(inDirect);
        assertThat(rProjected).isEqualTo(rDirect);
        assertThat(rProjected.fingerprint()).isEqualTo(rDirect.fingerprint());
    }

    // ---------- INV-C4-05: falha fechada fora do retrato ----------

    @Test
    @DisplayName("INV-C4-05: versao de tarifa dos dados diferente da resolvida falha fechado")
    void failsClosedOnTariffVersionMismatch() {
        FieldTariffData wrong = new FieldTariffData(TARIFF_MUN_A, CATEGORY, 10,
                new BigDecimal("25.00"),
                List.of(TariffBracket.open(new BigDecimal("5.00"))),
                true, new BigDecimal("80"), Map.of());
        FieldSnapshotProjector projector = new FieldSnapshotProjector();
        List<ChargeResolution> resolved = resolveField(companyCatalog(), null);

        assertThatThrownBy(() -> projector.project(resolved, wrong))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dados tarifarios nao sao os da tarifa resolvida");
    }

    @Test
    @DisplayName("INV-C4-05: sem cobranca progressiva resolvida nao ha ancora — falha fechado")
    void failsClosedWithoutProgressiveAnchor() {
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(tfdiDef(id(3), null), tfdiVersion(id(3)));
        FieldSnapshotProjector projector = new FieldSnapshotProjector();
        List<ChargeResolution> resolved = resolveField(catalog, null);
        FieldTariffData data = companyTariffData();

        assertThatThrownBy(() -> projector.project(resolved, data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactamente uma cobranca progressiva");
    }

    @Test
    @DisplayName("INV-C4-05: percentual de esgoto dos dados divergente do resolvido falha fechado")
    void failsClosedOnSewerPctMismatch() {
        FieldTariffData wrongPct = new FieldTariffData(TARIFF_EMPRESA, CATEGORY, 10,
                new BigDecimal("25.00"),
                List.of(new TariffBracket(10, new BigDecimal("3.00")),
                        TariffBracket.open(new BigDecimal("5.00"))),
                true, new BigDecimal("70"), Map.of());
        FieldSnapshotProjector projector = new FieldSnapshotProjector();
        List<ChargeResolution> resolved = resolveField(companyCatalog(), null);

        assertThatThrownBy(() -> projector.project(resolved, wrongPct))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("percentual de esgoto");
    }

    @Test
    @DisplayName("INV-C4-05: esgoto faturado nos dados sem cobranca derivada resolvida falha fechado")
    void failsClosedOnSewerWithoutDerivedCharge() {
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(aguaDef(id(1), TARIFF_EMPRESA, null), aguaVersion(id(1), TARIFF_EMPRESA));
        FieldSnapshotProjector projector = new FieldSnapshotProjector();
        List<ChargeResolution> resolved = resolveField(catalog, null);
        FieldTariffData data = companyTariffData();

        assertThatThrownBy(() -> projector.project(resolved, data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem cobranca derivada resolvida");
    }

    @Test
    @DisplayName("INV-C4-02/05: resolucao de convenio entregue directamente ao projector e recusada")
    void failsClosedOnExternalResolutionHandedDirectly() {
        ChargeDefinition convenio = new ChargeDefinition(id(9), "CONVENIO_RESIDUOS",
                "Convenio de residuos", ChargeOwner.THIRD_PARTY, RevenueNature.TARIFF,
                CalculationModel.EXTERNAL_VALUE, id(777), false, true, true, null, null);
        ChargeVersion convenioVersion = new ChargeVersion(UUID.randomUUID(), id(9), 1, JAN, null,
                CalculationModel.EXTERNAL_VALUE, null, null, id(778), ChargeParameters.empty());
        ChargeResolution smuggled = new ChargeResolution(convenio, convenioVersion);
        List<ChargeResolution> resolved = new ArrayList<>(
                resolveField(companyCatalog(), null));
        resolved.add(smuggled);
        FieldSnapshotProjector projector = new FieldSnapshotProjector();
        FieldTariffData data = companyTariffData();

        assertThatThrownBy(() -> projector.project(resolved, data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao calculavel em campo");
    }

    @Test
    @DisplayName("INV-C4-05: PERCENT_OF_BASE entregue directamente ao projector e recusado [A DEFINIR]")
    void failsClosedOnPercentResolutionHandedDirectly() {
        ChargeDefinition percent = new ChargeDefinition(id(8), "JUROS_BASE", "Percentual",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.PERCENT_OF_BASE,
                null, false, true, true, null, null);
        ChargeVersion percentVersion = new ChargeVersion(UUID.randomUUID(), id(8), 1, JAN, null,
                CalculationModel.PERCENT_OF_BASE,
                new ChargeParameters(new BigDecimal("5"), null, null));
        List<ChargeResolution> resolved = new ArrayList<>(resolveField(companyCatalog(), null));
        resolved.add(new ChargeResolution(percent, percentVersion));
        FieldSnapshotProjector projector = new FieldSnapshotProjector();

        assertThatThrownBy(() -> projector.project(resolved, companyTariffData()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao calculavel em campo");
    }

    // ---------- Correccao da revisao: R-01, R-02, R-04 ----------

    @Test
    @DisplayName("R-01/INV-C4-01: projectado municipal == snapshot esperado construido por valores LITERAIS independentes")
    void equivalenceMunicipalAgainstLiteralExpectedSnapshot() {
        ChargeCatalog catalog = companyCatalog();
        catalog.add(aguaDef(id(11), TARIFF_MUN_A, MUN_A), aguaVersion(id(11), TARIFF_MUN_A));
        catalog.add(esgotoDef(id(12), MUN_A), esgotoVersion(id(12)));
        FieldTariffData dataA = new FieldTariffData(TARIFF_MUN_A, CATEGORY, 12,
                new BigDecimal("30.00"),
                List.of(new TariffBracket(10, new BigDecimal("3.50")),
                        TariffBracket.open(new BigDecimal("5.50"))),
                true, new BigDecimal("80"), Map.of("reajuste", "2026-02"));

        List<ChargeResolution> resolved = resolveField(catalog, MUN_A);
        assertThat(resolved.stream().filter(r -> r.definition().code().equals("AGUA")).findFirst()
                .orElseThrow().definition().municipalityIbge()).isEqualTo(MUN_A);

        FieldTariffSnapshot projected = new FieldSnapshotProjector().project(resolved, dataA);

        // Lado "directo" INDEPENDENTE (mitigacao de R-01): o esperado e
        // construido a mao, campo a campo, com valores literais escritos
        // neste teste — NAO deriva do FieldTariffData entregue ao
        // projector, NAO passa por directSnapshot(...) nem por
        // companyTariffData()/helpers de massa partilhados com o lado do
        // projector. Os UUIDs sao os literais de id(901)/id(950) escritos
        // por extenso. Assim, qualquer desvio de copia do projector
        // (campo trocado, valor de outro escopo) reprova contra uma
        // expectativa que nao partilha a fonte com o lado projectado.
        FieldTariffSnapshot expectedLiteral = new FieldTariffSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000901"),
                UUID.fromString("00000000-0000-0000-0000-000000000950"),
                12, new BigDecimal("30.00"),
                List.of(new TariffBracket(10, new BigDecimal("3.50")),
                        TariffBracket.open(new BigDecimal("5.50"))),
                true, new BigDecimal("80"), Map.of("reajuste", "2026-02"));

        assertFieldByFieldEqual(projected, expectedLiteral);
    }

    @Test
    @DisplayName("R-04/INV-C4-05: mais de uma cobranca derivada resolvida falha fechado (fonte do esgoto ambigua)")
    void failsClosedOnMoreThanOneDerivedCharge() {
        List<ChargeResolution> resolved = new ArrayList<>(
                resolveField(companyCatalog(), null));
        ChargeDefinition secondDerived = new ChargeDefinition(id(31), "ESGOTO_2",
                "Esgoto derivado 2", ChargeOwner.OWN, RevenueNature.TARIFF,
                CalculationModel.DERIVED_FROM_VOLUME, null, false, true, true, null, null);
        resolved.add(new ChargeResolution(secondDerived, esgotoVersion(id(31))));
        FieldSnapshotProjector projector = new FieldSnapshotProjector();

        assertThatThrownBy(() -> projector.project(resolved, companyTariffData()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mais de uma cobranca derivada resolvida");
    }

    @Test
    @DisplayName("R-04/INV-C4-05: cobranca derivada resolvida com dados sem esgoto falha fechado")
    void failsClosedOnDerivedChargeWithSewerDisabledData() {
        FieldTariffData noSewerData = new FieldTariffData(TARIFF_EMPRESA, CATEGORY, 10,
                new BigDecimal("25.00"),
                List.of(new TariffBracket(10, new BigDecimal("3.00")),
                        TariffBracket.open(new BigDecimal("5.00"))),
                false, null, Map.of("reajuste", "2026-01"));
        FieldSnapshotProjector projector = new FieldSnapshotProjector();
        List<ChargeResolution> resolved = resolveField(companyCatalog(), null);

        assertThatThrownBy(() -> projector.project(resolved, noSewerData))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao faturam esgoto");
    }

    @Test
    @DisplayName("R-02 CARACTERIZACAO (P-C4-03 alargada): derivada so por tariffVersionId divergente da ancora NAO e confrontada — o projector aceita")
    void derivedSustainedOnlyByDivergentTariffVersionIdIsNotConfronted() {
        // Caracterizacao do comportamento ACTUAL (correccao R-02), sem
        // mudanca de semantica: a versao derivada e sustentada so pelo
        // canal tariffVersionId, com um valor DIFERENTE do da ancora
        // progressiva e do dos dados, e sem percent. O projector so
        // confronta o tariffVersionId da ANCORA e o percent da derivada
        // (quando declarado); o tariffVersionId da DERIVADA nunca e
        // confrontado — nao ha fonte na Camada 1 que mande essa
        // igualdade, pelo que exigir-la aqui seria inventar regra.
        // Fica registado na pendencia P-C4-03 alargada (relatorio de
        // correccao): se um dia se decidir o confronto, a fonte/regra
        // tem de vir do desenho ou da resolucao da tarifa (P2.5).
        ChargeCatalog catalog = new ChargeCatalog();
        catalog.add(aguaDef(id(1), TARIFF_EMPRESA, null), aguaVersion(id(1), TARIFF_EMPRESA));
        catalog.add(esgotoDef(id(2), null), esgotoVersionByTariffOnly(id(2), TARIFF_MUN_A));

        List<ChargeResolution> resolved = resolveField(catalog, null);
        ChargeResolution derived = resolved.stream()
                .filter(r -> r.definition().calculationModel()
                        == CalculationModel.DERIVED_FROM_VOLUME)
                .findFirst().orElseThrow();
        // Pre-condicoes da caracterizacao: derivada presente, sustentada
        // so por referencia, referencia divergente da ancora/dados.
        assertThat(derived.version().tariffVersionId()).isEqualTo(TARIFF_MUN_A);
        assertThat(derived.version().tariffVersionId())
                .isNotEqualTo(companyTariffData().tariffVersionId());
        assertThat(derived.version().parameters().percent()).isNull();

        // Comportamento actual provado: a projeccao ACEITA e devolve o
        // snapshot dos dados (o sewerPct dos dados e aceite sem
        // confronto, como na forma so-por-percent da P-C4-03 original).
        FieldTariffSnapshot projected = new FieldSnapshotProjector()
                .project(resolved, companyTariffData());
        assertFieldByFieldEqual(projected, directSnapshot(companyTariffData()));
    }

    // ---------- INV-C4-06: fotografia estavel ----------

    @Test
    @DisplayName("INV-C4-06: catalogo alterado depois da projeccao nao altera o snapshot projectado")
    void stablePhotographAgainstCatalogMutation() {
        ChargeCatalog catalog = companyCatalog();
        FieldSnapshotProjector projector = new FieldSnapshotProjector();
        FieldTariffSnapshot projected = projector.project(
                resolveField(catalog, null), companyTariffData());
        FieldTariffSnapshot reference = directSnapshot(companyTariffData());

        // Alteracao posterior do catalogo: nova cobranca vigente entra.
        ChargeDefinition nova = new ChargeDefinition(id(21), "TAXA_NOVA", "Taxa nova",
                ChargeOwner.OWN, RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT,
                null, false, true, true, null, null);
        catalog.add(nova, tfdiVersion(id(21)));

        assertFieldByFieldEqual(projected, reference);
    }

    @Test
    @DisplayName("INV-C4-06: coleccoes do chamador mutadas depois nao alteram o snapshot projectado")
    void stablePhotographAgainstCallerCollectionMutation() {
        List<TariffBracket> brackets = new ArrayList<>(List.of(
                new TariffBracket(10, new BigDecimal("3.00")),
                TariffBracket.open(new BigDecimal("5.00"))));
        Map<String, String> params = new LinkedHashMap<>(Map.of("reajuste", "2026-01"));
        FieldTariffData data = new FieldTariffData(TARIFF_EMPRESA, CATEGORY, 10,
                new BigDecimal("25.00"), brackets, true, new BigDecimal("80"), params);

        FieldTariffSnapshot projected = new FieldSnapshotProjector()
                .project(resolveField(companyCatalog(), null), data);

        brackets.clear();
        params.put("reajuste", "2099-99");

        assertThat(projected.brackets()).hasSize(2);
        assertThat(projected.parameterVersions()).containsEntry("reajuste", "2026-01");
        assertThatThrownBy(() -> projected.brackets().add(TariffBracket.open(BigDecimal.ONE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
