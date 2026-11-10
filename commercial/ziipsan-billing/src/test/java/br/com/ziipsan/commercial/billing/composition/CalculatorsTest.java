package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billing.catalog.ChargeVersion;
import br.com.ziipsan.commercial.billing.servicecatalog.RevenueNature;
import br.com.ziipsan.commercial.billing.servicecatalog.ServicePriceItem;
import br.com.ziipsan.commercial.billing.tariff.SewerFactorPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import br.com.ziipsan.commercial.billing.tariff.TariffEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.COMPETENCE;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.FROM;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.TO;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.definition;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.id;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As seis calculadoras, uma por modelo: delegacao no motor vigente
 * (INV-C2-09), regimes vigentes (INV-C2-10), coerencia D-03 e as regras
 * de fronteira (i)/(iii)/(iv)/(v). Valores e codigos sao fixtures
 * ficticias de teste.
 */
class CalculatorsTest {

    static ChargeCalculationContext.Builder baseBuilder() {
        return ChargeCalculationContext.builder()
                .unitRef("UC-FIXTURE").competence(COMPETENCE)
                .schedule(t1Schedule()).periodFrom(FROM).periodTo(TO).groups(List.of(res(1)))
                .waterConsumption(27)
                .sewer(new BillInput.SewerBilling(27,
                        new SewerParameters(d("80"), d("100"), null, SewerFactorPolicy.EXACT, false)));
    }

    static ChargeResolution resolution(ChargeDefinition def, UUID tariffId, String itemCode,
                                       UUID externalId, ChargeVersion.ChargeParameters params) {
        return new ChargeResolution(def, new ChargeVersion(UUID.randomUUID(), def.id(), 1,
                LocalDate.of(2026, 1, 1), null, def.calculationModel(), tariffId, itemCode,
                externalId, params));
    }

    static Map<UUID, String> scopes(String scope) {
        Map<UUID, String> scopes = new HashMap<>();
        scopes.put(id(100), scope);
        return scopes;
    }

    // ------------------------------------------------------------------
    // PROGRESSIVE_BY_CONSUMPTION — delegacao (INV-C2-09)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("INV-C2-09: progressiva delega em TariffEngine.calculate (valor e detalhe do motor)")
    void progressiveDelegatesToTariffEngine() {
        ChargeDefinition def = definition("AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, null);
        ChargeResolution progResolution = resolution(def, id(100), null, null,
                ChargeVersion.ChargeParameters.empty());
        ChargeCalculationContext ctx = baseBuilder().tariffVersionScopes(scopes(null)).build();
        BillChargeLine line = new ProgressiveConsumptionCalculator().calculate(ctx.withResolution(progResolution));
        assertThat(line.amount()).isEqualTo(
                TariffEngine.calculate(t1Schedule(), FROM, TO, List.of(res(1)), 27).net());
        assertThat(line.amount()).isEqualTo(d("112.00"));
        assertThat(line.detail()).isInstanceOf(br.com.ziipsan.commercial.billing.tariff.TariffCharge.class);
    }

    @Test
    @DisplayName("INV-C2-09: derivada delega em TariffEngine.calculateSewer/sewerVolume")
    void derivedDelegatesToTariffEngine() {
        ChargeDefinition def = definition("ESGOTO", CalculationModel.DERIVED_FROM_VOLUME, null);
        ChargeResolution derivedResolution = resolution(def, id(100), null, null,
                new ChargeVersion.ChargeParameters(d("80"), null, null));
        ChargeCalculationContext ctx = baseBuilder().tariffVersionScopes(scopes(null)).build();
        BillChargeLine line = new DerivedVolumePercentCalculator().calculate(ctx.withResolution(derivedResolution));
        SewerParameters params = new SewerParameters(d("80"), d("100"), null,
                SewerFactorPolicy.EXACT, false);
        assertThat(line.amount()).isEqualTo(TariffEngine.calculateSewer(
                t1Schedule(), FROM, TO, List.of(res(1)), 27, params).net());
        assertThat(line.amount()).isEqualTo(d("89.60"));
        assertThat(TariffEngine.sewerVolume(27, d("100"))).isEqualTo(27);
    }

    // ------------------------------------------------------------------
    // FIXED_PER_UNIT — fronteira (iv)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fronteira (iv)/INV-C2-02: fixa designada transporta o fixedPartValue resolvido, nem mais nem menos")
    void designatedFixedTransportsResolvedValue() {
        ChargeDefinition def = definition("TFDI", CalculationModel.FIXED_PER_UNIT, null);
        ChargeResolution res = resolution(def, null, null, null,
                new ChargeVersion.ChargeParameters(null, d("99.00"), null));
        ChargeCalculationContext ctx = baseBuilder()
                .fixedPartValue(d("25.00")).fixedPartChargeCode("TFDI").fixedUnits(Map.of("TFDI", 7))
                .build();
        BillChargeLine line = new FixedPerUnitCalculator().calculate(ctx.withResolution(res));
        assertThat(line.amount()).isEqualTo(d("25.00"));
    }

    @Test
    @DisplayName("fixa generica: fixedAmount x unidades pre-resolvidas, HALF_UP; sem unidades = 1")
    void genericFixedMultipliesByUnits() {
        ChargeDefinition def = definition("TAXA-FIXTURE", CalculationModel.FIXED_PER_UNIT, null);
        ChargeResolution res = resolution(def, null, null, null,
                new ChargeVersion.ChargeParameters(null, d("10.00"), null));
        ChargeCalculationContext withUnits = baseBuilder().fixedUnits(Map.of("TAXA-FIXTURE", 3)).build();
        assertThat(new FixedPerUnitCalculator().calculate(withUnits.withResolution(res)).amount())
                .isEqualTo(d("30.00"));
        ChargeCalculationContext noUnits = baseBuilder().build();
        assertThat(new FixedPerUnitCalculator().calculate(noUnits.withResolution(res)).amount())
                .isEqualTo(d("10.00"));
    }

    // ------------------------------------------------------------------
    // CATALOG_PRICE — fronteira (v)
    // ------------------------------------------------------------------

    static ChargeResolution catalogResolution() {
        ChargeDefinition def = definition("SERVICO-FIXTURE", CalculationModel.CATALOG_PRICE, null);
        return resolution(def, null, "ITEM-FIXTURE", null,
                new ChargeVersion.ChargeParameters(null, null, d("5.00")));
    }

    @Test
    @DisplayName("fronteira (v): item resolvido no contexto vence o unitPrice dos parametros")
    void catalogPricePrefersResolvedItem() {
        ServicePriceItem item = new ServicePriceItem("ITEM-FIXTURE", "Fixture item",
                RevenueNature.TARIFF, d("7.50"), LocalDate.of(2026, 1, 1), null, false, null, null);
        ChargeCalculationContext ctx = baseBuilder()
                .priceItems(Map.of("ITEM-FIXTURE", item))
                .catalogQuantities(Map.of("SERVICO-FIXTURE", d("2")))
                .build();
        BillChargeLine line = new CatalogPriceCalculator().calculate(ctx.withResolution(catalogResolution()));
        assertThat(line.amount()).isEqualTo(d("15.00"));
    }

    @Test
    @DisplayName("fronteira (v): sem item no contexto, vale o unitPrice dos parametros (HALF_UP)")
    void catalogPriceFallsBackToParameterUnitPrice() {
        ChargeCalculationContext ctx = baseBuilder()
                .catalogQuantities(Map.of("SERVICO-FIXTURE", d("2")))
                .build();
        BillChargeLine line = new CatalogPriceCalculator().calculate(ctx.withResolution(catalogResolution()));
        assertThat(line.amount()).isEqualTo(d("10.00"));
    }

    @Test
    @DisplayName("catalogo: quantidade ausente = falha fechada; preco ausente nos dois canais = falha fechada")
    void catalogPriceFailsClosedWithoutQuantityOrPrice() {
        ChargeCalculationContext noQty = baseBuilder().build();
        assertThatThrownBy(() -> new CatalogPriceCalculator().calculate(noQty.withResolution(catalogResolution())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantidade");
        ChargeDefinition def = definition("SERVICO-FIXTURE", CalculationModel.CATALOG_PRICE, null);
        ChargeResolution noPrice = resolution(def, null, "ITEM-FIXTURE", null,
                ChargeVersion.ChargeParameters.empty());
        ChargeCalculationContext ctx = baseBuilder()
                .catalogQuantities(Map.of("SERVICO-FIXTURE", d("1"))).build();
        assertThatThrownBy(() -> new CatalogPriceCalculator().calculate(ctx.withResolution(noPrice)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("preco resolvido");
    }

    // ------------------------------------------------------------------
    // PERCENT_OF_BASE — fronteira (iii)
    // ------------------------------------------------------------------

    static ChargeResolution percentResolution() {
        ChargeDefinition def = definition("PERCENTUAL-FIXTURE", CalculationModel.PERCENT_OF_BASE, null);
        return resolution(def, null, null, null,
                new ChargeVersion.ChargeParameters(d("10"), null, null));
    }

    @Test
    @DisplayName("fronteira (iii): percentual usa Money.percentOf com o modo EXIGIDO do contexto")
    void percentUsesDeclaredRoundingMode() {
        ChargeCalculationContext down = baseBuilder()
                .percentBases(Map.of("PERCENTUAL-FIXTURE", d("10.05")))
                .percentRoundingModes(Map.of("PERCENTUAL-FIXTURE", RoundingMode.DOWN))
                .build();
        assertThat(new PercentOnBaseCalculator().calculate(down.withResolution(percentResolution())).amount())
                .isEqualTo(d("1.00"));
        ChargeCalculationContext halfUp = baseBuilder()
                .percentBases(Map.of("PERCENTUAL-FIXTURE", d("10.05")))
                .percentRoundingModes(Map.of("PERCENTUAL-FIXTURE", RoundingMode.HALF_UP))
                .build();
        assertThat(new PercentOnBaseCalculator().calculate(halfUp.withResolution(percentResolution())).amount())
                .isEqualTo(d("1.01"));
    }

    @Test
    @DisplayName("fronteira (iii): modo de arredondamento AUSENTE = excecao, nunca implicito")
    void percentWithoutRoundingModeIsRefused() {
        ChargeCalculationContext ctx = baseBuilder()
                .percentBases(Map.of("PERCENTUAL-FIXTURE", d("200.00")))
                .build();
        assertThatThrownBy(() -> new PercentOnBaseCalculator().calculate(ctx.withResolution(percentResolution())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("arredondamento");
    }

    // ------------------------------------------------------------------
    // EXTERNAL_VALUE — transporte via porta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("externo: transporta o valor da porta sem calcular; linha nasce THIRD_PARTY")
    void externalTransportsProviderValue() {
        ChargeDefinition def = new ChargeDefinition(UUID.randomUUID(), "CONVENIO-FIXTURE",
                "Fixture convenio", ChargeOwner.THIRD_PARTY, RevenueNature.ANCILLARY,
                CalculationModel.EXTERNAL_VALUE, UUID.randomUUID(), false, true, false, null, null);
        ChargeResolution res = resolution(def, null, null, UUID.randomUUID(),
                ChargeVersion.ChargeParameters.empty());
        ChargeCalculationContext ctx = baseBuilder()
                .externalValueProvider((resolutionArg, contextArg) -> d("42.50"))
                .build();
        BillChargeLine line = new ExternalValueCalculator().calculate(ctx.withResolution(res));
        assertThat(line.amount()).isEqualTo(d("42.50"));
        assertThat(line.owner()).isEqualTo(ChargeOwner.THIRD_PARTY);
    }

    @Test
    @DisplayName("externo: porta ausente = falha fechada")
    void externalWithoutProviderFailsClosed() {
        ChargeDefinition def = new ChargeDefinition(UUID.randomUUID(), "CONVENIO-FIXTURE",
                "Fixture convenio", ChargeOwner.THIRD_PARTY, RevenueNature.ANCILLARY,
                CalculationModel.EXTERNAL_VALUE, UUID.randomUUID(), false, true, false, null, null);
        ChargeResolution res = resolution(def, null, null, UUID.randomUUID(),
                ChargeVersion.ChargeParameters.empty());
        ChargeCalculationContext ctx = baseBuilder().build();
        assertThatThrownBy(() -> new ExternalValueCalculator().calculate(ctx.withResolution(res)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("porta de valor externo");
    }

    // ------------------------------------------------------------------
    // Coerencia D-03 — fronteira (ii)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("D-03: cobranca municipal x tarifa do MESMO municipio = admitida")
    void coherenceSameMunicipalityIsAdmitted() {
        ChargeDefinition def = definition("AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, "4200001");
        ChargeResolution res = resolution(def, id(100), null, null,
                ChargeVersion.ChargeParameters.empty());
        ChargeCalculationContext ctx = baseBuilder().municipalityIbge("4200001")
                .tariffVersionScopes(scopes("4200001")).build();
        assertThat(new ProgressiveConsumptionCalculator().calculate(ctx.withResolution(res)).amount())
                .isEqualTo(d("112.00"));
    }

    @Test
    @DisplayName("D-03: cobranca municipal x tarifa EMPRESA (scope null) = admitida")
    void coherenceMunicipalChargeWithCompanyTariffIsAdmitted() {
        ChargeDefinition def = definition("AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, "4200001");
        ChargeResolution res = resolution(def, id(100), null, null,
                ChargeVersion.ChargeParameters.empty());
        ChargeCalculationContext ctx = baseBuilder().municipalityIbge("4200001")
                .tariffVersionScopes(scopes(null)).build();
        assertThat(new ProgressiveConsumptionCalculator().calculate(ctx.withResolution(res)).amount())
                .isEqualTo(d("112.00"));
    }

    @Test
    @DisplayName("D-03: cruzamento municipio A x tarifa do municipio B = excecao fechada")
    void coherenceCrossMunicipalityIsRefused() {
        ChargeDefinition def = definition("AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, "4200001");
        ChargeResolution res = resolution(def, id(100), null, null,
                ChargeVersion.ChargeParameters.empty());
        ChargeCalculationContext ctx = baseBuilder().municipalityIbge("4200001")
                .tariffVersionScopes(scopes("4200002")).build();
        assertThatThrownBy(() -> new ProgressiveConsumptionCalculator().calculate(ctx.withResolution(res)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("D-03");
    }

    @Test
    @DisplayName("D-03: escopo da tarifa nao pre-resolvido pelo chamador = falha fechada")
    void coherenceUnresolvedScopeFailsClosed() {
        ChargeDefinition def = definition("AGUA", CalculationModel.PROGRESSIVE_BY_CONSUMPTION, null);
        ChargeResolution res = resolution(def, id(100), null, null,
                ChargeVersion.ChargeParameters.empty());
        ChargeCalculationContext ctx = baseBuilder().tariffVersionScopes(Map.of()).build();
        assertThatThrownBy(() -> new ProgressiveConsumptionCalculator().calculate(ctx.withResolution(res)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pre-resolvido");
    }

    // ------------------------------------------------------------------
    // Fronteira (i) — formato do municipalityIbge da UC
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fronteira (i): municipalityIbge malformado da UC = falha fechada na construcao do contexto")
    void malformedMunicipalityFailsClosed() {
        for (String bad : List.of("123", "42000001", "420000A", " 4200001", "")) {
            assertThatThrownBy(() -> baseBuilder().municipalityIbge(bad).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformado");
        }
    }

    @Test
    @DisplayName("fronteira (i): municipalityIbge de 7 digitos e null (empresa) sao aceites")
    void wellFormedMunicipalityIsAdmitted() {
        assertThat(baseBuilder().municipalityIbge("4200001").build().municipalityIbge())
                .isEqualTo("4200001");
        assertThat(baseBuilder().municipalityIbge(null).build().municipalityIbge()).isNull();
    }

    @Test
    @DisplayName("maestro regista exactamente uma calculadora por modelo (seis modelos)")
    void composerRegistersOneCalculatorPerModel() {
        for (CalculationModel model : CalculationModel.values()) {
            assertThat(BillComposer.calculatorFor(model).model()).isEqualTo(model);
        }
        assertThat(CalculationModel.values()).hasSize(6);
    }
}
