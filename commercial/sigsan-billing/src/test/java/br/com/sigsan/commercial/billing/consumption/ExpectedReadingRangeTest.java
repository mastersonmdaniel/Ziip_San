package br.com.sigsan.commercial.billing.consumption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos R-* do relatório 02 (seção 10.5) e a armadilha "média 105 dá 1073 e não 1074" (seção 7,
 * item 1; caso R-08). Valores recalculados à mão: cada extremo = leitura anterior + fator × média,
 * HALF_UP ao inteiro.
 */
class ExpectedReadingRangeTest {

    @ParameterizedTest(name = "média {0}, leitura anterior {1}, {2} dígitos → [{3}, {4}]")
    @CsvSource({
            // R-01: m ≤ 10 → [LA, LA + m + 10]
            "8, 1000, 5, 1000, 1018",
            "10, 1000, 5, 1000, 1020",
            // R-02: m = 15 → 0,4×15 = 6; 1,6×15 = 24
            "15, 1000, 5, 1006, 1024",
            // fronteiras da faixa 11..20: 11 → 4,4→4 e 17,6→18; 20 → 8 e 32
            "11, 1000, 5, 1004, 1018",
            "20, 1000, 5, 1008, 1032",
            // R-03: m = 25 → 12,5→13 e 37,5→38 (HALF_UP)
            "25, 1000, 5, 1013, 1038",
            // 21 → 10,5→11 e 31,5→32; 45 → 22,5→23 e 67,5→68
            "21, 1000, 5, 1011, 1032",
            "45, 1000, 5, 1023, 1068",
            // R-04: m = 60 → 36 e 84; 46 → 27,6→28 e 64,4→64
            "60, 1000, 5, 1036, 1084",
            "46, 1000, 5, 1028, 1064",
            // R-07: m = 100 usa 0,6/1,4 → 60 e 140; m = 101 usa 0,7/1,3 → 70,7→71 e 131,3→131
            "100, 1000, 5, 1060, 1140",
            "101, 1000, 5, 1071, 1131",
            // R-05: m = 150 → 105 e 195
            "150, 1000, 5, 1105, 1195",
            // R-06: m = 25, LA = 99990, 5 dígitos: bruto [100003, 100028] → virada (−100000) → [3, 28]
            "25, 99990, 5, 3, 28",
            // R-08: m = 105 → 0,7×105 = 73,5 → 74 e 1,3×105 = 136,5 → 137
            "105, 1000, 5, 1074, 1137"
    })
    @DisplayName("R-01..R-08: faixa de leitura esperada")
    void range(int average, int previous, int digits, int low, int high) {
        ExpectedReadingRange.Range r = ExpectedReadingRange.of(average, previous, digits);

        assertThat(r.low()).isEqualTo(low);
        assertThat(r.high()).isEqualTo(high);
    }

    @Test
    @DisplayName("armadilha 1 / R-08: com BigDecimal(0.7) a média 105 daria 73 (1073); com decimal exato dá 74 (1074)")
    void doublePitfall() {
        // 0,7 em ponto flutuante vale 0,6999999999999999555...; × 105 = 73,49999999999999534...; HALF_UP → 73
        BigDecimal viaDouble = new BigDecimal(0.7).multiply(BigDecimal.valueOf(105)).setScale(0, RoundingMode.HALF_UP);
        assertThat(viaDouble).isEqualByComparingTo("73");
        assertThat(1000 + viaDouble.intValue()).as("o que o legado calcula").isEqualTo(1073);

        // com o decimal exato "0.7": 73,5 → 74
        assertThat(ExpectedReadingRange.of(105, 1000, 5).low()).as("o correto").isEqualTo(1074);
    }

    @Test
    @DisplayName("propriedade (10.16): antes da virada, faixa inicial ≤ faixa final para toda média")
    void lowNeverExceedsHigh() {
        for (int average = 0; average <= 2000; average++) {
            ExpectedReadingRange.Range r = ExpectedReadingRange.of(average, 0, 9);
            assertThat(r.low()).as("média %d", average).isLessThanOrEqualTo(r.high());
        }
    }

    @Test
    @DisplayName("entradas inválidas")
    void invalidInputs() {
        assertThatThrownBy(() -> ExpectedReadingRange.of(-1, 0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExpectedReadingRange.of(10, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
