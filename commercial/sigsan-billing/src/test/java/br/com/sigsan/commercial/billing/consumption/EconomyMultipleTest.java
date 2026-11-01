package br.com.sigsan.commercial.billing.consumption;

import br.com.sigsan.commercial.billing.tariff.CategoryRule;
import br.com.sigsan.commercial.billing.tariff.TariffGroup;
import br.com.sigsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.sigsan.commercial.billing.tariff.TariffEngine;
import br.com.sigsan.commercial.billing.tariff.TariffVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static br.com.sigsan.commercial.billing.fixture.FictitiousTariffs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos M-* do relatório 02 (seção 10.2): consumo ajustado ao múltiplo de economias antes da tarifa
 * (tarifa FICTÍCIA T1, mínimo 10 m³ por economia nas duas categorias).
 */
class EconomyMultipleTest {

    static Stream<Arguments> cases() {
        return Stream.of(
                // RES, 2 eco, 35: mín 20; excesso 15; 15 DIV 2 = 7; ajustado = 7×2 + 10×2 = 34; resto 1 → 34
                Arguments.of("M-01", List.of(res(2)), 35, 34),
                // RES1 + COM1, 25: mín 20; excesso 5; 5 DIV 2 = 2; ajustado (2+10) + (2+10) = 24; resto 1 → 24
                Arguments.of("M-02", List.of(res(1), com(1)), 25, 24),
                // RES, 3 eco, 28: mín 30; excesso −2 ≤ 0; resto = 28 MOD 3 = 1 → 27
                Arguments.of("M-03", List.of(res(3)), 28, 27),
                // 2 eco, 15 (mín 20, excesso −5): resto = 15 MOD 2 = 1 → 14
                Arguments.of("M-04", List.of(res(2)), 15, 14),
                // já é múltiplo: 2 eco, 36: mín 20, excesso 16, 16 DIV 2 = 8, ajustado 8×2 + 20 = 36 → 36
                Arguments.of("M-05", List.of(res(2)), 36, 36),
                // 3 eco, 100: mín 30; excesso 70; 70 DIV 3 = 23; ajustado 23×3 + 30 = 99; resto 1 → 99
                Arguments.of("M-06", List.of(res(3)), 100, 99),
                // 1 economia: nunca ajusta
                Arguments.of("M-07", List.of(res(1)), 27, 27)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("M-01..M-07: consumo faturado = consumo − resto")
    void adjust(String id, List<TariffGroup> groups, int consumption, int expected) {
        assertThat(EconomyMultiple.adjust(consumption, t1(), groups)).isEqualTo(expected);
    }

    @Test
    @DisplayName("propriedade (10.16): com mais de uma economia o consumo faturado é múltiplo das economias e perde menos que uma economia")
    void billedConsumptionIsAMultipleOfEconomies() {
        for (int economies = 2; economies <= 6; economies++) {
            for (int consumption = 0; consumption <= 300; consumption++) {
                int billed = EconomyMultiple.adjust(consumption, t1(), List.of(res(economies)));
                assertThat(billed % economies).as("%d eco, %d m³", economies, consumption).isZero();
                assertThat(consumption - billed).isBetween(0, economies - 1);
            }
        }
    }

    @Test
    @DisplayName("armadilha 3: a divisão inteira perde m³ de forma sistemática; o motor de tarifa não cobra o resto")
    void integerDivisionLosesCubicMeters() {
        // 35 m³ em 2 economias: sem o ajuste, a tarifa só enxerga 7 m³ por economia (15 DIV 2) e o 35º m³ some
        // em silêncio. O ajuste torna isso explícito: 34 faturados, 1 perdido.
        List<TariffGroup> groups = List.of(res(2));
        assertThat(TariffEngine.categoriesFor(t1(), groups, 35).get(0).consumption()).isEqualTo(34);
        assertThat(TariffEngine.categoriesFor(t1(), groups, 34).get(0).net())
                .isEqualByComparingTo(TariffEngine.categoriesFor(t1(), groups, 35).get(0).net());
    }

    @Test
    @DisplayName("fator de economias incompatível com o ajuste falha em vez de inventar consumo")
    void economyFactorMismatchFails() {
        // mín da ligação com fator 2 e 3 economias = 10×2 = 20; excesso 15; 15 DIV 3 = 5; ajustado = 5×3 + 10×3 = 45;
        // resto = 35 − 45 = −10: fora de [0, 3)
        CategoryRule withFactor = CategoryRule.forCategory(RES, 10, d("30.00"), d("2"), List.of(block(null, "4.00")));
        TariffVersion v = new TariffVersion(id(150), T1_FROM, null, List.of(withFactor), null);

        assertThatThrownBy(() -> EconomyMultiple.adjust(35, v, List.of(res(3))))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("fator de economias");
    }
}
