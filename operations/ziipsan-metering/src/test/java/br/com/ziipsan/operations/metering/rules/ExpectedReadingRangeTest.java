package br.com.ziipsan.operations.metering.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Faixa de leitura esperada (relatório 02, 2.5 e casos R-*). As bandas e fatores usados aqui são
 * FICTÍCIOS (ver {@link Fixtures#rangePolicy()}). Cada linha foi recalculada à mão, sem olhar o
 * resultado do relatório, e depois conferida com ele: todos os R-* conferem.
 *
 * <p>Regra de arredondamento sob teste: cada ponta é arredondada HALF_UP para inteiro sobre fatores
 * exatos; depois, módulo 10^dígitos.
 */
class ExpectedReadingRangeTest {

    private final ExpectedRangePolicy policy = Fixtures.rangePolicy();

    @ParameterizedTest(name = "{0}: média {1}, anterior {2}, {3} dígitos -> [{4}, {5}]")
    @DisplayName("casos R-* do relatório 02, recalculados à mão")
    @CsvSource({
            // R-01: m <= 10: [LA, LA + m + 10] = [1000, 1000 + 8 + 10]
            "R-01, 8,    1000, 5, 1000, 1018",
            // R-02: 0,4 * 15 = 6,0 ; 1,6 * 15 = 24,0
            "R-02, 15,   1000, 5, 1006, 1024",
            // R-03: 0,5 * 25 = 12,5 -> 13 ; 1,5 * 25 = 37,5 -> 38 (HALF_UP)
            "R-03, 25,   1000, 5, 1013, 1038",
            // R-04: 0,6 * 60 = 36 ; 1,4 * 60 = 84
            "R-04, 60,   1000, 5, 1036, 1084",
            // R-05: 0,7 * 150 = 105 ; 1,3 * 150 = 195
            "R-05, 150,  1000, 5, 1105, 1195",
            // R-06: bruto [99990+13, 99990+38] = [100003, 100028]; módulo 100000 -> [3, 28]
            "R-06, 25,  99990, 5, 3,    28",
            // R-07: média 100 ainda é a faixa 0,6-1,4: [1060, 1140]
            "R-07a, 100, 1000, 5, 1060, 1140",
            // R-07: média 101 é a faixa 0,7-1,3: 70,7 -> 71 ; 131,3 -> 131
            "R-07b, 101, 1000, 5, 1071, 1131",
            // R-08: 0,7 * 105 = 73,5 -> 74 ; 1,3 * 105 = 136,5 -> 137 (o legado, com double, dava 73)
            "R-08, 105,  1000, 5, 1074, 1137",
    })
    void reportCases(String id, int average, int previous, int digits, int low, int high) {
        ExpectedReadingRange range = ReadingValidator.expectedRange(average, previous, digits, policy);
        assertThat(range.low()).as(id + " início").isEqualTo(low);
        assertThat(range.high()).as(id + " fim").isEqualTo(high);
    }

    @ParameterizedTest(name = "média {0} -> [{1}, {2}]")
    @DisplayName("limites entre faixas (cada corte pertence à faixa de baixo)")
    @CsvSource({
            // média 0: faixa 1, [LA, LA + 0 + 10]
            "0,   1000, 1010",
            // média 10: ainda faixa 1: [LA, LA + 10 + 10]
            "10,  1000, 1020",
            // média 11: faixa 2: 0,4 * 11 = 4,4 -> 4 ; 1,6 * 11 = 17,6 -> 18
            "11,  1004, 1018",
            // média 20: 8 ; 32
            "20,  1008, 1032",
            // média 21: faixa 3: 10,5 -> 11 ; 31,5 -> 32
            "21,  1011, 1032",
            // média 45: 22,5 -> 23 ; 67,5 -> 68
            "45,  1023, 1068",
            // média 46: faixa 4: 27,6 -> 28 ; 64,4 -> 64
            "46,  1028, 1064",
    })
    void bandBoundaries(int average, int low, int high) {
        ExpectedReadingRange range = ReadingValidator.expectedRange(average, 1000, 5, policy);
        assertThat(range.low()).isEqualTo(low);
        assertThat(range.high()).isEqualTo(high);
    }

    @Test
    @DisplayName("armadilha 1 do relatório: com BigDecimal(double) a média 105 daria 73, e com texto exato dá 74")
    void exactFactorsAvoidTheBinaryDoubleTrap() {
        BigDecimal legacy = new BigDecimal(0.7).multiply(BigDecimal.valueOf(105)).setScale(0, RoundingMode.HALF_UP);
        assertThat(legacy.intValueExact()).as("o legado, com double").isEqualTo(73);

        assertThat(ReadingValidator.expectedRange(105, 1000, 5, policy).low()).as("exato").isEqualTo(1074);
    }

    @Test
    @DisplayName("divergência do relatório: quando só o fim passa da virada a faixa atravessa o mostrador")
    void rangeThatStraddlesTheDialWrapsAndContainsBothSides() {
        // LA 99990, média 8: [99990, 99990 + 8 + 10 = 100008 -> 8]. O teste do legado
        // ("menor que o início OU maior que o fim") acusaria TODA leitura como fora da faixa.
        ExpectedReadingRange range = ReadingValidator.expectedRange(8, 99990, 5, policy);
        assertThat(range.low()).isEqualTo(99990);
        assertThat(range.high()).isEqualTo(8);
        assertThat(range.wraps()).isTrue();
        assertThat(range.contains(99990)).isTrue();
        assertThat(range.contains(99999)).isTrue();
        assertThat(range.contains(0)).isTrue();
        assertThat(range.contains(8)).isTrue();
        assertThat(range.contains(9)).isFalse();
        assertThat(range.contains(50000)).isFalse();
        assertThat(range.contains(99989)).isFalse();
    }

    @Test
    @DisplayName("mostrador de 3 dígitos: 995 + 6 = 1001 -> 1 e 995 + 24 = 1019 -> 19")
    void threeDigitDial() {
        ExpectedReadingRange range = ReadingValidator.expectedRange(15, 995, 3, policy);
        assertThat(range.low()).isEqualTo(1);
        assertThat(range.high()).isEqualTo(19);
        assertThat(range.wraps()).isFalse();
    }

    @Test
    @DisplayName("invariante: para toda média e leitura anterior, a leitura esperada (anterior + média) está na faixa")
    void expectedReadingIsAlwaysInsideTheRange() {
        for (int average = 0; average <= 400; average++) {
            for (int previous : new int[]{0, 1, 500, 99_000, 99_990, 99_999}) {
                ExpectedReadingRange range = ReadingValidator.expectedRange(average, previous, 5, policy);
                int expected = (int) ((previous + (long) average) % 100_000);
                assertThat(range.contains(expected))
                        .as("média %d, anterior %d, faixa %s", average, previous, range)
                        .isTrue();
                assertThat(range.low()).isBetween(0, 99_999);
                assertThat(range.high()).isBetween(0, 99_999);
                if (!range.wraps()) {
                    assertThat(range.low()).isLessThanOrEqualTo(range.high());
                }
            }
        }
    }

    @Test
    @DisplayName("entradas inválidas são recusadas, não corrigidas")
    void invalidInputs() {
        assertThatThrownBy(() -> ReadingValidator.expectedRange(-1, 1000, 5, policy))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> ReadingValidator.expectedRange(10, 100_000, 5, policy))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> ReadingValidator.expectedRange(10, 1000, 0, policy))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("a política exige faixas crescentes e a última sem teto")
    void policyValidation() {
        ExpectedRangePolicy.Band open = new ExpectedRangePolicy.Band(null, Fixtures.bd("0.7"), 0, Fixtures.bd("1.3"), 0);
        ExpectedRangePolicy.Band ten = new ExpectedRangePolicy.Band(10, Fixtures.bd("0"), 0, Fixtures.bd("1"), 10);
        ExpectedRangePolicy.Band twenty = new ExpectedRangePolicy.Band(20, Fixtures.bd("0.4"), 0, Fixtures.bd("1.6"), 0);
        assertThatThrownBy(() -> new ExpectedRangePolicy(List.of())).isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new ExpectedRangePolicy(List.of(ten, twenty)))
                .as("última faixa com teto").isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new ExpectedRangePolicy(List.of(twenty, ten, open)))
                .as("tetos decrescentes").isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new ExpectedRangePolicy(List.of(open, ten)))
                .as("faixa aberta no meio").isInstanceOf(MeteringInputException.class);
    }
}
