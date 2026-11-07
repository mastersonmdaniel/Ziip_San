package br.com.ziipsan.commercial.billing.tariff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reajuste sobre a tarifa fictícia T1, com os valores recalculados à mão:
 * T1 RES mín 10 m³ / 30,00, faixas 20→4,00, 50→6,00, aberta→9,00;
 * COM mín 10 / 50,00, faixas 20→7,00, 50→10,00, aberta→14,00.
 */
class ReadjustmentCalculatorTest {

    private static final LocalDate FROM = LocalDate.of(2027, 1, 1);

    @Test
    @DisplayName("índice puro de 5%: todo preço × 1,05 (30,00→31,5000; 4,00→4,2000; 14,00→14,7000)")
    void indexOnlyAppliesToEveryPrice() {
        TariffVersion adjusted = ReadjustmentCalculator.apply(t1(), id(200), FROM, null,
                ReadjustFormula.INDEX_ONLY, d("5"), null);

        CategoryRule res = adjusted.ruleFor(RES, null);
        assertThat(res.minCharge()).isEqualByComparingTo("31.5000");
        assertThat(res.blocks()).extracting(b -> b.pricePerM3().toPlainString())
                .containsExactly("4.2000", "6.3000", "9.4500");
        CategoryRule com = adjusted.ruleFor(COM, null);
        assertThat(com.minCharge()).isEqualByComparingTo("52.5000");
        assertThat(com.blocks().get(2).pricePerM3()).isEqualByComparingTo("14.7000");
        // a origem não muda: records imutáveis e o cálculo não edita a versão vigente
        assertThat(t1().ruleFor(RES, null).minCharge()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("fórmula paramétrica: IPCA 6,25% − fator X 1,25% = 5,00% aplicados (mesmo resultado do índice de 5%)")
    void parametricFormulaSubtractsFactorX() {
        assertThat(ReadjustFormula.INDEX_MINUS_FACTOR_X.appliedPct(d("6.25"), d("1.25")))
                .isEqualByComparingTo("5.0000");

        TariffVersion adjusted = ReadjustmentCalculator.apply(t1(), id(201), FROM, null,
                ReadjustFormula.INDEX_MINUS_FACTOR_X, d("6.25"), d("1.25"));

        assertThat(adjusted.ruleFor(RES, null).minCharge()).isEqualByComparingTo("31.5000");
    }

    @Test
    @DisplayName("preço com 4 casas: 4,4550 × 1,05 = 4,677750 → 4,6778 (HALF_UP a 4 casas)")
    void priceRoundingIsHalfUpToFourPlaces() {
        assertThat(ReadjustmentCalculator.adjustedPrice(d("4.4550"), d("5"))).isEqualByComparingTo("4.6778");
    }

    @Test
    @DisplayName("índice negativo (deflação) de −2%: 30,00 → 29,4000")
    void negativeIndexReducesPrices() {
        TariffVersion adjusted = ReadjustmentCalculator.apply(t1(), id(202), FROM, null,
                ReadjustFormula.INDEX_ONLY, d("-2"), null);

        assertThat(adjusted.ruleFor(RES, null).minCharge()).isEqualByComparingTo("29.4000");
    }

    @Test
    @DisplayName("percentuais da tarifa social e consumo mínimo NÃO são reajustados (não são preços)")
    void socialPercentsAndMinimumConsumptionAreNotPrices() {
        TariffVersion withSocial = t1WithSocial(social("50", band(0, 20, "50")));

        TariffVersion adjusted = ReadjustmentCalculator.apply(withSocial, id(203), FROM, null,
                ReadjustFormula.INDEX_ONLY, d("10"), null);

        assertThat(adjusted.socialRule().minimumChargeDiscountPct()).isEqualByComparingTo("50");
        assertThat(adjusted.socialRule().bands().get(0).discountPct()).isEqualByComparingTo("50");
        assertThat(adjusted.ruleFor(RES, null).minConsumption()).isEqualTo(10);
        assertThat(adjusted.ruleFor(RES, null).minCharge()).isEqualByComparingTo("33.0000");
    }

    @Test
    @DisplayName("incoerências recusadas: fator X com fórmula por índice; fórmula paramétrica sem fator X; índice ≤ −100%")
    void incoherentInputsAreRejected() {
        assertThatThrownBy(() -> ReadjustFormula.INDEX_ONLY.appliedPct(d("5"), d("1")))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("INDEX_ONLY");
        assertThatThrownBy(() -> ReadjustFormula.INDEX_MINUS_FACTOR_X.appliedPct(d("5"), null))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("fator X");
        assertThatThrownBy(() -> ReadjustFormula.INDEX_ONLY.appliedPct(d("-100"), null))
                .isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> ReadjustmentCalculator.apply(t1(), id(204), FROM, null,
                ReadjustFormula.INDEX_ONLY, null, null))
                .isInstanceOf(TariffConfigurationException.class);
    }

    @Test
    @DisplayName("a conta da versão reajustada em 10% para 27 m³ RES: 33,00 + 10×4,40 + 7×6,60 = 123,20")
    void adjustedVersionBillsWithTheEngine() {
        TariffVersion adjusted = ReadjustmentCalculator.apply(t1(), id(205), FROM, null,
                ReadjustFormula.INDEX_ONLY, d("10"), null);

        List<CategoryCharge> charges = TariffEngine.categoriesFor(adjusted, List.of(res(1)), 27);

        assertThat(charges.get(0).net()).isEqualByComparingTo("123.20");
    }
}
