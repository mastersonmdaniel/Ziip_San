package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.bill.BillCalculator;
import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.bill.BillResult;
import br.com.ziipsan.commercial.billing.bill.CreditReceivable;
import br.com.ziipsan.commercial.billing.bill.DebitReceivable;
import br.com.ziipsan.commercial.billing.bill.ReceivableSourceType;
import br.com.ziipsan.commercial.billing.bill.TaxRate;
import br.com.ziipsan.commercial.billing.bill.TaxWithholdingPolicy;
import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billing.catalog.ChargeVersion;
import br.com.ziipsan.commercial.billing.math.Money;
import br.com.ziipsan.commercial.billing.servicecatalog.RevenueNature;
import br.com.ziipsan.commercial.billing.tariff.SewerFactorPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.id;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INV-C2-01 (central) e INV-C2-02/05/10: para toda a entrada classica,
 * o maestro + adapter produzem um {@link BillResult} IDENTICO ao do
 * {@code BillCalculator} vigente, campo a campo, com comparacao EXACTA
 * de {@code BigDecimal} (escala incluida — {@code isEqualTo}, nunca
 * {@code compareTo} tolerante nem {@code isEqualByComparingTo}).
 * Tolerancia ZERO: divergencia = defeito desta camada.
 *
 * <p>Os codigos de cobranca destes testes ("AGUA"/"ESGOTO"/"TFDI") sao
 * codigos FICTICIOS de fixture, como as tarifas da
 * {@code FictitiousTariffs}: o catalogo de producao nasce vazio e
 * nenhum codigo aqui e codigo de producao.
 */
class BillComposerEquivalenceTest {

    static final LocalDate FROM = LocalDate.of(2026, 9, 5);
    static final LocalDate TO = LocalDate.of(2026, 10, 4);
    static final LocalDate COMPETENCE = LocalDate.of(2026, 10, 1);

    // ------------------------------------------------------------------
    // Fixtures do catalogo (ficticias, so de teste)
    // ------------------------------------------------------------------

    static ChargeDefinition definition(String code, CalculationModel model, String municipalityIbge) {
        return new ChargeDefinition(UUID.randomUUID(), code, "Fixture " + code, ChargeOwner.OWN,
                RevenueNature.TARIFF, model, null, false, true, false, null, municipalityIbge);
    }

    static ChargeVersion version(ChargeDefinition definition, UUID tariffVersionId,
                                 ChargeVersion.ChargeParameters parameters) {
        return new ChargeVersion(UUID.randomUUID(), definition.id(), 1, LocalDate.of(2026, 1, 1),
                null, definition.calculationModel(), tariffVersionId, null, null, parameters);
    }

    static ChargeResolution waterResolution() {
        ChargeDefinition def = definition("AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, null);
        return new ChargeResolution(def, version(def, id(100), ChargeVersion.ChargeParameters.empty()));
    }

    static ChargeResolution sewerResolution() {
        ChargeDefinition def = definition("ESGOTO", CalculationModel.DERIVED_FROM_VOLUME, null);
        return new ChargeResolution(def, version(def, id(100),
                new ChargeVersion.ChargeParameters(d("80"), null, null)));
    }

    static ChargeResolution fixedResolution() {
        ChargeDefinition def = definition("TFDI", CalculationModel.FIXED_PER_UNIT, null);
        // fixedAmount DIFERENTE do fixedPartValue resolvido de proposito:
        // a linha designada transporta o valor resolvido (INV-C2-02).
        return new ChargeResolution(def, version(def, null,
                new ChargeVersion.ChargeParameters(null, d("99.00"), null)));
    }

    static ChargeResolution genericFixedResolution() {
        // Taxa fixa GENERICA de catalogo (nao e a TFDI designada):
        // fixedAmount 10,00 x 1 unidade (sem fixedUnits = 1).
        ChargeDefinition def = definition("TAXA-X", CalculationModel.FIXED_PER_UNIT, null);
        return new ChargeResolution(def, version(def, null,
                new ChargeVersion.ChargeParameters(null, d("10.00"), null)));
    }

    static Map<UUID, String> companyScopes() {
        Map<UUID, String> scopes = new HashMap<>();
        scopes.put(id(100), null);
        return scopes;
    }

    static SewerParameters sewerParameters() {
        return new SewerParameters(d("80"), d("100"), null, SewerFactorPolicy.EXACT, false);
    }

    static DebitReceivable debit(String total, int installments, int done) {
        return new DebitReceivable(UUID.randomUUID(), UUID.randomUUID(), "FIXTURE-DEBIT", true,
                new BigDecimal(total), installments, done, ReceivableSourceType.MANUAL, null, null,
                null, LocalDate.of(2026, 9, 1), null, null);
    }

    static CreditReceivable credit(String total, int installments, int done, String residual) {
        return new CreditReceivable(UUID.randomUUID(), UUID.randomUUID(), "FIXTURE-CREDIT",
                new BigDecimal(total), installments, done, new BigDecimal(residual),
                ReceivableSourceType.MANUAL, null, null, null, LocalDate.of(2026, 9, 1), null, null);
    }

    static TaxWithholdingPolicy taxPolicy() {
        return new TaxWithholdingPolicy(List.of(
                new TaxRate("FIXTURE-T1", d("4.65")), new TaxRate("FIXTURE-T2", d("1.00"))));
    }

    // ------------------------------------------------------------------
    // A comparacao exacta, campo a campo (INV-C2-01)
    // ------------------------------------------------------------------

    static void assertExactlyEqual(BillResult expected, BillResult actual) {
        assertThat(actual.water()).isEqualTo(expected.water());
        assertThat(actual.sewer()).isEqualTo(expected.sewer());
        assertThat(actual.waterValue()).isEqualTo(expected.waterValue());
        assertThat(actual.sewerValue()).isEqualTo(expected.sewerValue());
        assertThat(actual.fixedPartValue()).isEqualTo(expected.fixedPartValue());
        assertThat(actual.debitLines()).isEqualTo(expected.debitLines());
        assertThat(actual.debitsValue()).isEqualTo(expected.debitsValue());
        assertThat(actual.creditLines()).isEqualTo(expected.creditLines());
        assertThat(actual.creditsValue()).isEqualTo(expected.creditsValue());
        assertThat(actual.taxBase()).isEqualTo(expected.taxBase());
        assertThat(actual.taxLines()).isEqualTo(expected.taxLines());
        assertThat(actual.taxesValue()).isEqualTo(expected.taxesValue());
        assertThat(actual.total()).isEqualTo(expected.total());
        assertThat(actual.charges()).isEqualTo(expected.charges());
    }

    static BillResult viaMaestro(BillInput input, List<ChargeResolution> resolutions,
                                 String fixedPartChargeCode) {
        ChargeCalculationContext context = BillCalculatorAdapter.toContext(input, "UC-FIXTURE", COMPETENCE)
                .resolvedCharges(resolutions)
                .fixedPartChargeCode(fixedPartChargeCode)
                .tariffVersionScopes(companyScopes())
                .build();
        return BillCalculatorAdapter.toBillResult(BillComposer.compose(context));
    }

    // ------------------------------------------------------------------
    // Casos classicos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("INV-C2-01: so agua — maestro reproduz o vigente campo a campo")
    void waterOnlyIsIdentical() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27, null,
                List.of(), List.of(), null);
        assertExactlyEqual(BillCalculator.calculate(input),
                viaMaestro(input, List.of(waterResolution()), null));
    }

    @Test
    @DisplayName("INV-C2-01: agua + esgoto — maestro reproduz o vigente campo a campo")
    void waterAndSewerAreIdentical() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27,
                new BillInput.SewerBilling(27, sewerParameters()), List.of(), List.of(), null);
        assertExactlyEqual(BillCalculator.calculate(input),
                viaMaestro(input, List.of(waterResolution(), sewerResolution()), null));
    }

    @Test
    @DisplayName("INV-C2-01/02: agua + esgoto + TFDI de catalogo = fixedPartValue vigente")
    void waterSewerAndFixedAreIdentical() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27,
                new BillInput.SewerBilling(27, sewerParameters()), List.of(), List.of(), null,
                d("25.00"));
        BillResult expected = BillCalculator.calculate(input);
        BillResult actual = viaMaestro(input,
                List.of(waterResolution(), sewerResolution(), fixedResolution()), "TFDI");
        assertExactlyEqual(expected, actual);
        assertThat(actual.fixedPartValue()).isEqualTo(d("25.00"));
    }

    @Test
    @DisplayName("INV-C2-02: TFDI null no vigente (ZERO) — linha de catalogo nasce com ZERO")
    void fixedNullBecomesZeroLine() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), null, null,
                List.of(), List.of(), null, null);
        ChargeCalculationContext context = BillCalculatorAdapter.toContext(input, "UC-FIXTURE", COMPETENCE)
                .resolvedCharges(List.of(fixedResolution()))
                .fixedPartChargeCode("TFDI")
                .build();
        BillComposition composition = BillComposer.compose(context);
        assertThat(composition.fixedPartValue()).isEqualTo(Money.ZERO);
        assertThat(composition.lines()).singleElement()
                .satisfies(line -> assertThat(line.amount()).isEqualTo(Money.ZERO));
        assertExactlyEqual(BillCalculator.calculate(input), BillCalculatorAdapter.toBillResult(composition));
    }

    @Test
    @DisplayName("INV-C2-01: debitos parcelados (Installments vigente) identicos")
    void debitsAreIdentical() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27,
                new BillInput.SewerBilling(27, sewerParameters()),
                List.of(debit("100.00", 3, 0), debit("35.00", 6, 5)), List.of(), null);
        assertExactlyEqual(BillCalculator.calculate(input),
                viaMaestro(input, List.of(waterResolution(), sewerResolution()), null));
    }

    @Test
    @DisplayName("INV-C2-01: creditos abaixo, iguais e acima do teto (CreditApplier vigente)")
    void creditsBelowEqualAndAboveCapAreIdentical() {
        for (String creditTotal : List.of("50.00", "201.60", "500.00")) {
            BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27,
                    new BillInput.SewerBilling(27, sewerParameters()), List.of(),
                    List.of(credit(creditTotal, 1, 0, "0.00")), null);
            assertExactlyEqual(BillCalculator.calculate(input),
                    viaMaestro(input, List.of(waterResolution(), sewerResolution()), null));
        }
    }

    @Test
    @DisplayName("INV-C2-01/10: retencao presente (TaxWithholding vigente, residuo no ultimo)")
    void withholdingIsIdentical() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27,
                new BillInput.SewerBilling(27, sewerParameters()),
                List.of(debit("100.00", 3, 0)), List.of(credit("50.00", 1, 0, "0.00")), taxPolicy(),
                d("25.00"));
        assertExactlyEqual(BillCalculator.calculate(input),
                viaMaestro(input, List.of(waterResolution(), sewerResolution(), fixedResolution()),
                        "TFDI"));
    }

    @Test
    @DisplayName("INV-C2-01: conta completa (agua+esgoto+fixa+debitos+creditos+retencao)")
    void fullClassicBillIsIdentical() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO,
                List.of(res(2), br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.com(1)),
                55, new BillInput.SewerBilling(55, sewerParameters()),
                List.of(debit("100.00", 3, 1)), List.of(credit("80.00", 2, 0, "5.00")), taxPolicy(),
                d("25.00"));
        assertExactlyEqual(BillCalculator.calculate(input),
                viaMaestro(input, List.of(waterResolution(), sewerResolution(), fixedResolution()),
                        "TFDI"));
    }

    @Test
    @DisplayName("INV-C2-05: debito sem parcela seleccionada lanca a MESMA excecao do vigente")
    void debitWithoutInstallmentThrowsSameException() {
        DebitReceivable exhausted = debit("100.00", 3, 3);
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27, null,
                List.of(exhausted), List.of(), null);
        assertThatThrownBy(() -> BillCalculator.calculate(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não tem parcela a cobrar");
        ChargeCalculationContext context = BillCalculatorAdapter.toContext(input, "UC-FIXTURE", COMPETENCE)
                .resolvedCharges(List.of(waterResolution()))
                .tariffVersionScopes(companyScopes())
                .build();
        assertThatThrownBy(() -> BillComposer.compose(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não tem parcela a cobrar")
                .hasMessageContaining(exhausted.id().toString());
    }

    // ------------------------------------------------------------------
    // Correccao R2-01 — TFDI avulsa nunca se perde em silencio:
    // a decisao e pela LINHA DESIGNADA, nao por qualquer linha fixa
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R2-01: migracao gradual — taxa fixa generica de catalogo + TFDI avulsa 25,00: o total inclui os 25,00 E a taxa")
    void genericFixedPlusLooseTfdiIncludesBoth() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27,
                new BillInput.SewerBilling(27, sewerParameters()), List.of(), List.of(), null,
                d("25.00"));
        ChargeCalculationContext context = BillCalculatorAdapter.toContext(input, "UC-FIXTURE", COMPETENCE)
                .resolvedCharges(List.of(waterResolution(), sewerResolution(), genericFixedResolution()))
                .fixedPartChargeCode("TFDI")
                .tariffVersionScopes(companyScopes())
                .build();
        BillComposition composition = BillComposer.compose(context);
        // agua 112,00 + esgoto 89,60 + taxa generica 10,00 + TFDI avulsa 25,00
        assertThat(composition.ownSubtotal()).isEqualTo(d("236.60"));
        assertThat(composition.fixedPartValue()).isEqualTo(d("25.00"));
        assertThat(composition.total()).isEqualTo(d("236.60"));
        // Projectada no BillResult, a TFDI avulsa esta presente (nao se perdeu):
        BillResult projected = BillCalculatorAdapter.toBillResult(composition);
        assertThat(projected.fixedPartValue()).isEqualTo(d("25.00"));
        assertThat(projected.total()).isEqualTo(
                BillCalculator.calculate(input).total().add(d("10.00")));
    }

    @Test
    @DisplayName("R2-01: linha designada DIVERGENTE do fixedPartValue resolvido = excecao fechada (sem escolha silenciosa)")
    void designatedFixedLineDivergingFromResolvedIsRefused() {
        // A guarda do maestro exercida directamente com uma linha
        // construida a mao: com as calculadoras desta camada a linha
        // designada transporta o resolvido por construcao (INV-C2-02),
        // pelo que a divergencia so pode nascer de outra origem de
        // linha — e e para essa origem que a guarda fecha.
        BillChargeLine divergent = new BillChargeLine("TFDI", UUID.randomUUID(), ChargeOwner.OWN,
                RevenueNature.TARIFF, CalculationModel.FIXED_PER_UNIT, d("99.00"), null, null);
        assertThatThrownBy(() -> BillComposer.validateDesignatedFixedLine(divergent, d("25.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TFDI")
                .hasMessageContaining("diverge");
        // E a deteccao da linha designada e pelo codigo declarado:
        assertThat(BillComposer.designatedFixedLine(List.of(divergent), "TFDI")).isEqualTo(divergent);
        assertThat(BillComposer.designatedFixedLine(List.of(divergent), "OUTRA")).isNull();
        assertThat(BillComposer.designatedFixedLine(List.of(divergent), null)).isNull();
    }

    @Test
    @DisplayName("R2-01/INV-C2-02: linha designada IGUAL ao resolvido — entra uma unica vez, sem duplicacao")
    void designatedFixedLineEqualToResolvedIsNotDuplicated() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27,
                new BillInput.SewerBilling(27, sewerParameters()), List.of(), List.of(), null,
                d("25.00"));
        ChargeCalculationContext context = BillCalculatorAdapter.toContext(input, "UC-FIXTURE", COMPETENCE)
                .resolvedCharges(List.of(waterResolution(), sewerResolution(), fixedResolution()))
                .fixedPartChargeCode("TFDI")
                .tariffVersionScopes(companyScopes())
                .build();
        BillComposition composition = BillComposer.compose(context);
        // 112,00 + 89,60 + 25,00 = 226,60 — NAO 251,60 (sem somar a TFDI outra vez)
        assertThat(composition.ownSubtotal()).isEqualTo(d("226.60"));
        assertThat(composition.fixedPartValue()).isEqualTo(d("25.00"));
        assertThat(composition.total()).isEqualTo(d("226.60"));
        assertExactlyEqual(BillCalculator.calculate(input),
                BillCalculatorAdapter.toBillResult(composition));
    }

    // ------------------------------------------------------------------
    // Correccao R2-02 — espelhamento resolucoes <-> servicos classicos
    // (pre-condicao da INV-C2-01): os 4 ramos falham fechados
    // ------------------------------------------------------------------

    static ChargeCalculationContext.Builder mirrorBuilder() {
        return ChargeCalculationContext.builder()
                .unitRef("UC-FIXTURE").competence(COMPETENCE)
                .schedule(t1Schedule()).periodFrom(FROM).periodTo(TO).groups(List.of(res(1)))
                .tariffVersionScopes(companyScopes());
    }

    @Test
    @DisplayName("R2-02: resolucao progressiva presente com waterConsumption null = excecao fechada nomeada")
    void progressiveResolutionWithoutWaterConsumptionIsRefused() {
        ChargeCalculationContext context = mirrorBuilder()
                .waterConsumption(null)
                .resolvedCharges(List.of(waterResolution()))
                .build();
        assertThatThrownBy(() -> BillComposer.compose(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("espelhamento INV-C2-01")
                .hasMessageContaining("PROGRESSIVE_BY_CONSUMPTION");
    }

    @Test
    @DisplayName("R2-02: waterConsumption presente SEM resolucao progressiva = excecao (nunca agua 0,00 silenciosa)")
    void waterConsumptionWithoutProgressiveResolutionIsRefused() {
        ChargeCalculationContext context = mirrorBuilder()
                .waterConsumption(27)
                .resolvedCharges(List.of())
                .build();
        assertThatThrownBy(() -> BillComposer.compose(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("espelhamento INV-C2-01")
                .hasMessageContaining("PROGRESSIVE_BY_CONSUMPTION")
                .hasMessageContaining("0,00");
    }

    @Test
    @DisplayName("R2-02: resolucao derivada presente com sewer null = excecao fechada nomeada")
    void derivedResolutionWithoutSewerIsRefused() {
        ChargeCalculationContext context = mirrorBuilder()
                .waterConsumption(null).sewer(null)
                .resolvedCharges(List.of(sewerResolution()))
                .build();
        assertThatThrownBy(() -> BillComposer.compose(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("espelhamento INV-C2-01")
                .hasMessageContaining("DERIVED_FROM_VOLUME");
    }

    @Test
    @DisplayName("R2-02: sewer presente SEM resolucao derivada = excecao (nunca esgoto 0,00 silencioso)")
    void sewerWithoutDerivedResolutionIsRefused() {
        ChargeCalculationContext context = mirrorBuilder()
                .waterConsumption(27)
                .sewer(new BillInput.SewerBilling(27, sewerParameters()))
                .resolvedCharges(List.of(waterResolution()))
                .build();
        assertThatThrownBy(() -> BillComposer.compose(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("espelhamento INV-C2-01")
                .hasMessageContaining("DERIVED_FROM_VOLUME")
                .hasMessageContaining("0,00");
    }

    @Test
    @DisplayName("adapter: fromBillResult projecta composicao com ownSubtotal = agua+esgoto+fixa")
    void fromBillResultProjectsComposition() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27,
                new BillInput.SewerBilling(27, sewerParameters()), List.of(), List.of(), null,
                d("25.00"));
        BillResult result = BillCalculator.calculate(input);
        BillComposition composition = BillCalculatorAdapter.fromBillResult(result);
        assertThat(composition.ownSubtotal()).isEqualTo(
                result.waterValue().add(result.sewerValue()).add(result.fixedPartValue()));
        assertThat(composition.thirdPartyTotal()).isEqualTo(Money.ZERO);
        assertThat(composition.total()).isEqualTo(result.total());
        assertExactlyEqual(result, BillCalculatorAdapter.toBillResult(composition));
    }
}
