package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.bill.BillCalculator;
import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.bill.BillResult;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billing.tariff.CategoryRule;
import br.com.ziipsan.commercial.billing.tariff.SewerFactorPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import br.com.ziipsan.commercial.billing.tariff.TariffGroup;
import br.com.ziipsan.commercial.billing.tariff.TariffSchedule;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.COMPETENCE;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.FROM;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.TO;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.assertExactlyEqual;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.credit;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.debit;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.fixedResolution;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.sewerResolution;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.taxPolicy;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.waterResolution;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.band;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.block;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.com;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.id;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.scheduleOf;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.social;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.socialRes;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1WithSocial;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova diferencial da Camada 2 (desenho, secao 2.5; adendo D-05):
 * novo (maestro) x vigente ({@code BillCalculator}) sobre a MESMA
 * massa, campo a campo, tolerancia ZERO — divergencia = defeito desta
 * camada.
 *
 * <p>O gerador e DETERMINISTICO (semente fixa) e PARAMETRIZAVEL:
 * {@link #generate(long, int)} produz sempre os mesmos casos para a
 * mesma semente/quantidade. O volume-alvo e de 10.000 casos
 * [ESTIMATIVA do desenho, secao 2.5], afinavel pela propriedade de
 * sistema {@code c2.differential.cases} para corridas rapidas locais.
 *
 * <p>Massa: varredura de consumo (0, minimo, fronteiras de faixa),
 * categorias simples/multi/social, TFDI presente/ausente, retencao
 * presente/ausente, debitos com/sem parcela, creditos
 * abaixo/igual/acima do teto, terceiro presente/ausente (terceiro e
 * exercitado em {@code BillComposerThirdPartyTest}) e o caso-ancora
 * E-01. Casos D-05: (a) mono-municipio/empresa, (b) 2 municipios com
 * tarifas distintas sobre perfis derivados da base ficticia 12k,
 * (c) precedencia, (d) coerencia cruzada recusada, e a invariante de
 * regressao INV-C2-11.
 *
 * <p>LIMITE: esta prova e CODIGO de prova — executa no harness/CI do
 * PC do dono (esta VM nao tem JVM); ver RELATORIO-CAMADA-2.
 */
class DifferentialHarnessTest {

    static final long SEED = 20261109L;
    static final int TARGET_CASES = Integer.getInteger("c2.differential.cases", 10_000);
    static final String MUNICIPALITY_A = "4200001";
    static final String MUNICIPALITY_B = "4200002";

    /** Caso gerado: todos os parametros de uma conta classica. */
    record GeneratedCase(int consumption, boolean multiCategory, boolean social, boolean sewer,
                         boolean fixed, boolean debit, int creditKind, boolean tax) {
    }

    /** Gerador deterministico: mesma semente + mesma quantidade = mesma lista, sempre. */
    static List<GeneratedCase> generate(long seed, int count) {
        Random random = new Random(seed);
        List<GeneratedCase> cases = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cases.add(new GeneratedCase(
                    random.nextInt(0, 90),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextInt(0, 3),
                    random.nextBoolean()));
        }
        return List.copyOf(cases);
    }

    static BillInput inputFor(GeneratedCase c) {
        TariffSchedule schedule;
        List<TariffGroup> groups;
        if (c.social()) {
            schedule = scheduleOf(t1WithSocial(social("50", band(0, 15, "50"))));
            groups = List.of(socialRes(2, 1));
        } else if (c.multiCategory()) {
            schedule = t1Schedule();
            groups = List.of(res(2), com(1));
        } else {
            schedule = t1Schedule();
            groups = List.of(res(1));
        }
        SewerParameters sewerParams = new SewerParameters(d("80"), d("100"), null,
                SewerFactorPolicy.EXACT, false);
        return new BillInput(schedule, FROM, TO, groups, c.consumption(),
                c.sewer() ? new BillInput.SewerBilling(c.consumption(), sewerParams) : null,
                c.debit() ? List.of(debit("100.00", 3, 1)) : List.of(),
                switch (c.creditKind()) {
                    case 0 -> List.of(credit("10.00", 1, 0, "0.00"));
                    case 1 -> List.of(credit("90.00", 2, 0, "5.00"));
                    default -> List.of(credit("900.00", 1, 0, "0.00"));
                },
                c.tax() ? taxPolicy() : null,
                c.fixed() ? d("25.00") : null);
    }

    static BillResult viaMaestro(BillInput input, boolean fixed) {
        List<ChargeResolution> resolutions = new ArrayList<>();
        if (input.waterConsumption() != null) {
            resolutions.add(waterResolution());
        }
        if (input.sewer() != null) {
            resolutions.add(sewerResolution());
        }
        if (fixed) {
            resolutions.add(fixedResolution());
        }
        Map<UUID, String> scopes = new HashMap<>();
        scopes.put(id(100), null);
        ChargeCalculationContext context = BillCalculatorAdapter.toContext(input, "UC-FIXTURE", COMPETENCE)
                .resolvedCharges(resolutions)
                .fixedPartChargeCode(fixed ? "TFDI" : null)
                .tariffVersionScopes(scopes)
                .build();
        return BillCalculatorAdapter.toBillResult(BillComposer.compose(context));
    }

    // ------------------------------------------------------------------
    // Diferencial principal + ancora
    // ------------------------------------------------------------------

    @Test
    @DisplayName("diferencial: gerador deterministico (semente fixa) reproduz a mesma massa")
    void generatorIsDeterministic() {
        assertThat(generate(SEED, 500)).isEqualTo(generate(SEED, 500));
        assertThat(generate(SEED, 500)).isNotEqualTo(generate(SEED + 1, 500));
    }

    @Test
    @DisplayName("diferencial: volume parametrizavel (alvo 10.000 casos [ESTIMATIVA] por omissao)")
    void generatorIsParametrizable() {
        assertThat(generate(SEED, 10)).hasSize(10);
        assertThat(TARGET_CASES).isPositive();
    }

    @Test
    @DisplayName("diferencial D-05(a): massa gerada mono-municipio/empresa — novo x vigente, tolerancia zero (INV-C2-01)")
    void differentialOverGeneratedMass() {
        for (GeneratedCase c : generate(SEED, TARGET_CASES)) {
            BillInput input = inputFor(c);
            BillResult expected = BillCalculator.calculate(input);
            BillResult actual = viaMaestro(input, c.fixed());
            assertExactlyEqual(expected, actual);
        }
    }

    @Test
    @DisplayName("diferencial: varredura de fronteiras de consumo (0, minimo, fronteiras de faixa, alem da ultima)")
    void differentialOverConsumptionBoundaries() {
        for (int consumption : List.of(0, 1, 9, 10, 11, 19, 20, 21, 49, 50, 51, 90)) {
            BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), consumption,
                    new BillInput.SewerBilling(consumption, new SewerParameters(d("80"), d("100"),
                            null, SewerFactorPolicy.EXACT, false)),
                    List.of(), List.of(), null);
            assertExactlyEqual(BillCalculator.calculate(input), viaMaestro(input, false));
        }
    }

    @Test
    @DisplayName("caso-ancora E-01: leitura 1000 -> 1027 (27 m3) = R$ 201,60 pelo maestro")
    void anchorCaseE01ThroughMaestro() {
        int consumption = 1027 - 1000;
        assertThat(consumption).isEqualTo(27);
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), consumption,
                new BillInput.SewerBilling(consumption, new SewerParameters(d("80"), d("100"),
                        null, SewerFactorPolicy.EXACT, false)),
                List.of(), List.of(), null);
        BillResult actual = viaMaestro(input, false);
        assertThat(actual.waterValue()).isEqualTo(d("112.00"));
        assertThat(actual.sewerValue()).isEqualTo(d("89.60"));
        assertThat(actual.total()).isEqualTo(d("201.60"));
    }

    // ------------------------------------------------------------------
    // D-05 (b)/(c)/(d) e INV-C2-11
    // ------------------------------------------------------------------

    /** Schedule alternativo (valores ficticios distintos) para o municipio B. */
    static TariffSchedule scheduleB() {
        CategoryRule resB = CategoryRule.forCategory(
                br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.RES, 10, d("36.00"),
                null, List.of(block(20, "5.00"), block(50, "7.00"), block(null, "10.00")));
        return scheduleOf(new TariffVersion(id(200), LocalDate.of(2026, 1, 1), null,
                List.of(resB), null));
    }

    static Map<UUID, String> scopesFor(UUID tariffVersionId, String scope) {
        Map<UUID, String> scopes = new HashMap<>();
        scopes.put(tariffVersionId, scope);
        return scopes;
    }

    static BillComposition composeForMunicipality(String municipality, TariffSchedule schedule,
                                                  UUID tariffVersionId, String tariffScope,
                                                  String definitionScope, int consumption) {
        var waterDef = BillComposerEquivalenceTest.definition("AGUA",
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                definitionScope);
        ChargeResolution water = new ChargeResolution(waterDef,
                new br.com.ziipsan.commercial.billing.catalog.ChargeVersion(UUID.randomUUID(),
                        waterDef.id(), 1, LocalDate.of(2026, 1, 1), null,
                        br.com.ziipsan.commercial.billing.catalog.CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                        tariffVersionId, null, null,
                        br.com.ziipsan.commercial.billing.catalog.ChargeVersion.ChargeParameters.empty()));
        ChargeCalculationContext context = ChargeCalculationContext.builder()
                .unitRef("UC-FIXTURE").municipalityIbge(municipality).competence(COMPETENCE)
                .schedule(schedule).periodFrom(FROM).periodTo(TO).groups(List.of(res(1)))
                .waterConsumption(consumption)
                .resolvedCharges(List.of(water))
                .tariffVersionScopes(scopesFor(tariffVersionId, tariffScope))
                .build();
        return BillComposer.compose(context);
    }

    @Test
    @DisplayName("D-05(b): 2 municipios com tarifas distintas — cada UC compoe com a tarifa do seu municipio")
    void twoMunicipalitiesComposeWithDistinctTariffs() {
        BillComposition a = composeForMunicipality(MUNICIPALITY_A, t1Schedule(), id(100),
                MUNICIPALITY_A, MUNICIPALITY_A, 27);
        BillComposition b = composeForMunicipality(MUNICIPALITY_B, scheduleB(), id(200),
                MUNICIPALITY_B, MUNICIPALITY_B, 27);
        assertThat(a.total()).isEqualTo(d("112.00"));
        // Municipio B (fixture distinta): 36,00 + 10 x 5,00 + 7 x 7,00 = 135,00
        assertThat(b.total()).isEqualTo(d("135.00"));
        assertThat(a.lines().get(0).municipalityIbge()).isEqualTo(MUNICIPALITY_A);
        assertThat(b.lines().get(0).municipalityIbge()).isEqualTo(MUNICIPALITY_B);
    }

    @Test
    @DisplayName("D-05(c): precedencia — resolucao municipal (a que o ChargeResolver devolve) e a que compoe")
    void municipalResolutionComposesForItsMunicipality() {
        // Para a UC do municipio A, o ChargeResolver da C1 devolve a
        // definicao MUNICIPAL (precedencia INV-C1-04-v2); o maestro
        // compoe essa — e o snapshot/linha carregam o escopo municipal.
        BillComposition municipal = composeForMunicipality(MUNICIPALITY_A, t1Schedule(), id(100),
                MUNICIPALITY_A, MUNICIPALITY_A, 27);
        assertThat(municipal.lines().get(0).municipalityIbge()).isEqualTo(MUNICIPALITY_A);
        // Para uma UC sem definicao municipal vigente, o resolvedor
        // devolve a de EMPRESA e a linha nasce com escopo null.
        BillComposition company = composeForMunicipality(MUNICIPALITY_B, t1Schedule(), id(100),
                null, null, 27);
        assertThat(company.lines().get(0).municipalityIbge()).isNull();
        assertThat(company.total()).isEqualTo(municipal.total());
    }

    @Test
    @DisplayName("D-05(c) INTEGRADO (R2-04): ChargeResolver REAL da C1 sobre catalogo empresa+municipal do mesmo codigo — a municipal devolvida e a que compoe")
    void resolverPrecedenceFlowsIntoComposition() {
        // Consumo da Camada 1 como o desenho de testes do projecto o
        // faz: ChargeCatalog/ChargeResolver/ChargeDefinition/
        // ChargeVersion sao classes de PRODUCAO do MESMO modulo
        // (ziipsan-billing, pacote catalog) — importadas e invocadas
        // pelo teste, nunca copiadas para esta camada. A producao da
        // C2 continua a receber List<ChargeResolution> pre-resolvida;
        // quem aqui resolve e o resolvedor real, nao uma escolha a mao.
        var companyDef = BillComposerEquivalenceTest.definition("AGUA",
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                null);
        var municipalDef = BillComposerEquivalenceTest.definition("AGUA",
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                MUNICIPALITY_A);
        br.com.ziipsan.commercial.billing.catalog.ChargeCatalog catalog =
                new br.com.ziipsan.commercial.billing.catalog.ChargeCatalog();
        catalog.add(companyDef, new br.com.ziipsan.commercial.billing.catalog.ChargeVersion(
                UUID.randomUUID(), companyDef.id(), 1, LocalDate.of(2026, 1, 1), null,
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                id(100), null, null,
                br.com.ziipsan.commercial.billing.catalog.ChargeVersion.ChargeParameters.empty()));
        catalog.add(municipalDef, new br.com.ziipsan.commercial.billing.catalog.ChargeVersion(
                UUID.randomUUID(), municipalDef.id(), 1, LocalDate.of(2026, 1, 1), null,
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                id(100), null, null,
                br.com.ziipsan.commercial.billing.catalog.ChargeVersion.ChargeParameters.empty()));

        // A resolucao devolvida pelo resolvedor real (precedencia
        // INV-C1-04-v2): a MUNICIPAL vence a de empresa no mesmo codigo.
        List<ChargeResolution> resolved =
                new br.com.ziipsan.commercial.billing.catalog.ChargeResolver(catalog)
                        .resolve("UC-FIXTURE", MUNICIPALITY_A, COMPETENCE);
        assertThat(resolved).singleElement().satisfies(r -> {
            assertThat(r.definition().id()).isEqualTo(municipalDef.id());
            assertThat(r.definition().municipalityIbge()).isEqualTo(MUNICIPALITY_A);
        });

        // E a composicao parte das resolucoes DEVOLVIDAS: a linha que
        // nasce e a municipal, com o valor da tarifa do municipio.
        ChargeCalculationContext context = ChargeCalculationContext.builder()
                .unitRef("UC-FIXTURE").municipalityIbge(MUNICIPALITY_A).competence(COMPETENCE)
                .schedule(t1Schedule()).periodFrom(FROM).periodTo(TO).groups(List.of(res(1)))
                .waterConsumption(27)
                .resolvedCharges(resolved)
                .tariffVersionScopes(scopesFor(id(100), MUNICIPALITY_A))
                .build();
        BillComposition composition = BillComposer.compose(context);
        assertThat(composition.lines()).singleElement().satisfies(line -> {
            assertThat(line.chargeCode()).isEqualTo("AGUA");
            assertThat(line.municipalityIbge()).isEqualTo(MUNICIPALITY_A);
            assertThat(line.amount()).isEqualTo(d("112.00"));
        });
        assertThat(composition.total()).isEqualTo(d("112.00"));
    }

    @Test
    @DisplayName("D-05(d): coerencia cruzada A x tarifa de B recusada na composicao (falha fechada)")
    void crossMunicipalityCoherenceIsRefusedInComposition() {
        assertThatThrownBy(() -> composeForMunicipality(MUNICIPALITY_A, t1Schedule(), id(100),
                MUNICIPALITY_B, MUNICIPALITY_A, 27))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("D-03");
    }

    @Test
    @DisplayName("INV-C2-11: totais do tenant identicos com/sem dimensao quando as tarifas municipais reproduzem a empresa")
    void tenantTotalsAreIdenticalWithAndWithoutDimension() {
        // Perfis derivados da base ficticia 12k (item 34): 9.000/3.000
        // UCs em 2 municipios ficticios — aqui em escala de prova, a
        // mesma proporcao 3:1 (750/250 perfis deterministicos).
        BigDecimal totalWithoutDimension = new BigDecimal("0.00");
        BigDecimal totalWithDimension = new BigDecimal("0.00");
        List<GeneratedCase> profiles = generate(SEED, 1_000);
        for (int i = 0; i < profiles.size(); i++) {
            GeneratedCase profile = profiles.get(i);
            String municipality = i < 750 ? MUNICIPALITY_A : MUNICIPALITY_B;
            BillInput input = inputFor(profile);
            totalWithoutDimension = totalWithoutDimension.add(BillCalculator.calculate(input).total());
            // Com dimensao: definicao e tarifa municipais que REPRODUZEM
            // os valores da empresa (mesmo schedule T1) — a atribuicao
            // muda, o total do tenant nao pode mudar.
            BillComposition composed = composeMunicipalEquivalent(input, municipality, profile.fixed());
            totalWithDimension = totalWithDimension.add(composed.total());
        }
        assertThat(totalWithDimension).isEqualTo(totalWithoutDimension);
    }

    /** Compoe o equivalente municipal de um caso classico (mesmos valores, escopo municipal). */
    static BillComposition composeMunicipalEquivalent(BillInput input, String municipality,
                                                      boolean fixed) {
        List<ChargeResolution> resolutions = new ArrayList<>();
        if (input.waterConsumption() != null) {
            resolutions.add(scopedWater(municipality));
        }
        if (input.sewer() != null) {
            resolutions.add(scopedSewer(municipality));
        }
        String fixedCode = null;
        if (fixed) {
            resolutions.add(scopedFixed(municipality));
            fixedCode = "TFDI";
        }
        Map<UUID, String> scopes = new HashMap<>();
        scopes.put(id(100), municipality);
        ChargeCalculationContext context = BillCalculatorAdapter.toContext(input, "UC-FIXTURE", COMPETENCE)
                .municipalityIbge(municipality)
                .resolvedCharges(resolutions)
                .fixedPartChargeCode(fixedCode)
                .tariffVersionScopes(scopes)
                .build();
        return BillComposer.compose(context);
    }

    static ChargeResolution scopedWater(String municipality) {
        var def = BillComposerEquivalenceTest.definition("AGUA",
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                municipality);
        return new ChargeResolution(def, new br.com.ziipsan.commercial.billing.catalog.ChargeVersion(
                UUID.randomUUID(), def.id(), 1, LocalDate.of(2026, 1, 1), null,
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                id(100), null, null,
                br.com.ziipsan.commercial.billing.catalog.ChargeVersion.ChargeParameters.empty()));
    }

    static ChargeResolution scopedSewer(String municipality) {
        var def = BillComposerEquivalenceTest.definition("ESGOTO",
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.DERIVED_FROM_VOLUME,
                municipality);
        return new ChargeResolution(def, new br.com.ziipsan.commercial.billing.catalog.ChargeVersion(
                UUID.randomUUID(), def.id(), 1, LocalDate.of(2026, 1, 1), null,
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.DERIVED_FROM_VOLUME,
                id(100), null, null,
                new br.com.ziipsan.commercial.billing.catalog.ChargeVersion.ChargeParameters(
                        d("80"), null, null)));
    }

    static ChargeResolution scopedFixed(String municipality) {
        var def = BillComposerEquivalenceTest.definition("TFDI",
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.FIXED_PER_UNIT,
                municipality);
        return new ChargeResolution(def, new br.com.ziipsan.commercial.billing.catalog.ChargeVersion(
                UUID.randomUUID(), def.id(), 1, LocalDate.of(2026, 1, 1), null,
                br.com.ziipsan.commercial.billing.catalog.CalculationModel.FIXED_PER_UNIT,
                null, null, null,
                new br.com.ziipsan.commercial.billing.catalog.ChargeVersion.ChargeParameters(
                        null, d("99.00"), null)));
    }
}
