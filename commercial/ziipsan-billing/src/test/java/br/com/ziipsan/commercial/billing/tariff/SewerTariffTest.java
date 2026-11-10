package br.com.ziipsan.commercial.billing.tariff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import br.com.ziipsan.commercial.billingrules.tariff.SewerCharge;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos E-* do relatório 02 (seção 10.3) e a armadilha de {@code BigDecimal(double)} (seção 7, item 1)
 * no cálculo do volume de esgoto. Tarifa FICTÍCIA T1 (ver {@code FictitiousTariffs}).
 */
class SewerTariffTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 5);
    private static final LocalDate TO = LocalDate.of(2026, 10, 4);

    private static SewerParameters params(String sewerPct, String collectionPct, SewerFactorPolicy policy) {
        return new SewerParameters(d(sewerPct), d(collectionPct), null, policy, false);
    }

    private static SewerCharge sewer(int economies, int baseVolume, SewerParameters p) {
        return TariffEngine.calculateSewer(t1Schedule(), FROM, TO, List.of(res(economies)), baseVolume, p);
    }

    @Test
    @DisplayName("E-01: água 27 m³, pcesgoto 80%, pccoleta 100%: volume 27; 112,00 × 0,80 = 89,60; água + esgoto = 201,60")
    void e01() {
        SewerCharge s = sewer(1, 27, params("80", "100", SewerFactorPolicy.EXACT));
        BigDecimal water = TariffEngine.calculate(t1Schedule(), FROM, TO, List.of(res(1)), 27).net();

        assertThat(s.volume()).isEqualTo(27);
        assertThat(s.net()).isEqualByComparingTo("89.60");
        assertThat(water.add(s.net())).isEqualByComparingTo("201.60");
    }

    @Test
    @DisplayName("E-02: pcesgoto 100%, pccoleta 80%: volume = 27 × 0,80 = 21,6 → 22; valor = 30 + 10×4 + 2×6 = 82,00")
    void e02() {
        SewerCharge s = sewer(1, 27, params("100", "80", SewerFactorPolicy.EXACT));

        assertThat(s.volume()).isEqualTo(22);
        assertThat(s.net()).isEqualByComparingTo("82.00");
    }

    @Test
    @DisplayName("E-03: consumo 8, pcesgoto 80%: a tarifa mínima de esgoto é 30,00 × 0,80 = 24,00")
    void e03() {
        SewerCharge s = sewer(1, 8, params("80", "100", SewerFactorPolicy.EXACT));

        assertThat(s.net()).isEqualByComparingTo("24.00");
    }

    @Test
    @DisplayName("E-04: pcesgoto 87,5% sobre 112,00; política do legado (fator em 2 casas): 0,88 → 98,56; política exata: 0,875 → 98,00")
    void e04() {
        SewerCharge legacy = sewer(1, 27, params("87.5", "100", SewerFactorPolicy.ROUNDED_TO_2_DECIMALS));
        SewerCharge exact = sewer(1, 27, params("87.5", "100", SewerFactorPolicy.EXACT));

        assertThat(legacy.factor()).isEqualByComparingTo("0.88");
        assertThat(legacy.net()).isEqualByComparingTo("98.56");
        assertThat(exact.factor()).isEqualByComparingTo("0.875");
        assertThat(exact.net()).isEqualByComparingTo("98.00");
        // a diferença é a armadilha 2 do relatório: 0,56 sobre 98,00 = 0,57% a mais que o percentual contratado
        assertThat(legacy.net().subtract(exact.net())).isEqualByComparingTo("0.56");
    }

    @ParameterizedTest(name = "pcesgoto {0} → fator {1} (arredondado a 2 casas)")
    @CsvSource({"87.5, 0.88", "33.33, 0.33", "80, 0.80", "100, 1.00", "12.345, 0.12", "0.4, 0.00"})
    @DisplayName("fator em 2 casas HALF_UP")
    void roundedFactor(String pct, String expected) {
        SewerCharge s = sewer(1, 10, params(pct, "100", SewerFactorPolicy.ROUNDED_TO_2_DECIMALS));

        assertThat(s.factor()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("E-05: consumo 12, pcesgoto 80%, alternativo 50% até 15 m³/economia: usa o MENOR (50%): tarifa(12) = 38,00 → 19,00")
    void e05() {
        SewerParameters withAlt = new SewerParameters(d("80"), d("100"),
                new SewerParameters.AlternativeSewerPct(d("50"), 15), SewerFactorPolicy.EXACT, false);

        SewerCharge s = sewer(1, 12, withAlt);

        assertThat(s.effectivePct()).isEqualByComparingTo("50");
        assertThat(s.net()).isEqualByComparingTo("19.00");
        // sem alternativo seria 38,00 × 0,80 = 30,40
        assertThat(sewer(1, 12, params("80", "100", SewerFactorPolicy.EXACT)).net()).isEqualByComparingTo("30.40");
    }

    @Test
    @DisplayName("percentual alternativo: acima do limite de volume por economia vale o normal; alternativo maior que o normal não vale")
    void alternativeBoundaries() {
        SewerParameters withAlt = new SewerParameters(d("80"), d("100"),
                new SewerParameters.AlternativeSewerPct(d("50"), 15), SewerFactorPolicy.EXACT, false);
        // 16 m³ > 15: percentual normal. tarifa(16) = 30 + 6×4 = 54,00 × 0,80 = 43,20
        assertThat(sewer(1, 16, withAlt).net()).isEqualByComparingTo("43.20");
        // exatamente 15 m³: ainda alternativo. tarifa(15) = 30 + 5×4 = 50,00 × 0,50 = 25,00
        assertThat(sewer(1, 15, withAlt).net()).isEqualByComparingTo("25.00");
        // alternativo (90%) maior que o normal (80%): nunca aumenta a cobrança
        SewerParameters higher = new SewerParameters(d("80"), d("100"),
                new SewerParameters.AlternativeSewerPct(d("90"), 15), SewerFactorPolicy.EXACT, false);
        assertThat(sewer(1, 12, higher).effectivePct()).isEqualByComparingTo("80");
        // volume por economia: 2 economias, volume 30 → 15 por economia → alternativo vale
        assertThat(sewer(2, 30, withAlt).effectivePct()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("E-06: esgoto sem água (poço), volume 14, pccoleta 100%, pcesgoto 100%: tarifa(14) = 30 + 4×4 = 46,00")
    void e06() {
        assertThat(sewer(1, 14, params("100", "100", SewerFactorPolicy.EXACT)).net()).isEqualByComparingTo("46.00");
    }

    @Test
    @DisplayName("esgoto com duas vigências: o fator de esgoto vale sobre cada fatia e a soma fecha")
    void sewerWithTwoVersions() {
        // T-08 com esgoto 80% exato: água 117,60 → esgoto: A 56,00 × 0,80 = 44,80; B 61,60 × 0,80 = 49,28; total 94,08
        TariffVersion a = new TariffVersion(id(100), T1_FROM, LocalDate.of(2026, 9, 19), List.of(t1Res(), t1Com()), null);
        TariffVersion b = new TariffVersion(id(200), LocalDate.of(2026, 9, 20), null,
                List.of(res(10, "33.00", block(20, "4.40"), block(50, "6.60"), block(null, "9.90"))), null);

        SewerCharge s = TariffEngine.calculateSewer(scheduleOf(a, b), FROM, TO, List.of(res(1)), 27,
                params("80", "100", SewerFactorPolicy.EXACT));

        assertThat(s.charge().slices().get(0).net()).isEqualByComparingTo("44.80");
        assertThat(s.charge().slices().get(1).net()).isEqualByComparingTo("49.28");
        assertThat(s.net()).isEqualByComparingTo("94.08");
    }

    // ------------------------------------------------------------------------------------------
    // Armadilha 1: BigDecimal(double)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("armadilha 1: 25 m³ com coleta de 70% dá 17,5 → 18; new BigDecimal(0.7) daria 17 (0,6999...×25 = 17,4999...)")
    void doublePitfallInSewerVolume() {
        // Com o decimal exato 0,7: 25 × 70 / 100 = 17,5 → HALF_UP → 18.
        assertThat(TariffEngine.sewerVolume(25, d("70"))).isEqualTo(18);

        // O que o legado faria: BigDecimal construído de double. 0,7 em ponto flutuante é
        // 0,6999999999999999555910790149937..., e 25 × isso = 17,49999999999999889 → HALF_UP → 17.
        @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
        BigDecimal viaDouble = new BigDecimal(0.7).multiply(BigDecimal.valueOf(25)).setScale(0, RoundingMode.HALF_UP);
        assertThat(viaDouble).isEqualByComparingTo("17");
        assertThat(TariffEngine.sewerVolume(25, d("70"))).isNotEqualTo(viaDouble.intValue());
    }

    @ParameterizedTest(name = "{0} m³ × {1}% = {2}")
    @CsvSource({"27, 100, 27", "27, 80, 22", "25, 70, 18", "5, 50, 3", "0, 80, 0", "10, 50, 5", "1, 50, 1", "3, 33.33, 1"})
    @DisplayName("volume de esgoto: base × pccoleta/100, arredondado ao m³ com HALF_UP")
    void sewerVolume(int base, String pct, int expected) {
        // 27×0,80 = 21,6 → 22; 5×0,50 = 2,5 → 3; 1×0,50 = 0,5 → 1; 3 × 0,3333 = 0,9999 → 1
        assertThat(TariffEngine.sewerVolume(base, d(pct))).isEqualTo(expected);
    }

    @Test
    @DisplayName("parâmetros de esgoto não têm padrão: percentual ausente ou fora de [0, 100] é recusado")
    void parametersHaveNoDefaults() {
        assertThatThrownBy(() -> new SewerParameters(null, d("100"), null, SewerFactorPolicy.EXACT, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SewerParameters(d("80"), null, null, SewerFactorPolicy.EXACT, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SewerParameters(d("120"), d("100"), null, SewerFactorPolicy.EXACT, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SewerParameters(d("80"), d("100"), null, null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
