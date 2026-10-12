package br.com.sigsan.intelligence.losses.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Valores esperados calculados à mão. */
class PressureStatisticsTest {

    private static List<BigDecimal> of(String... v) {
        List<BigDecimal> l = new ArrayList<>();
        for (String s : v) l.add(s == null ? null : new BigDecimal(s));
        return l;
    }

    @Test
    @DisplayName("10, 20, 30: média 20, variância 200/3, desvio √(200/3)")
    void basicSeries() {
        PressureStatistics s = PressureStatistics.of(of("10", "20", "30"));

        assertThat(s.count()).isEqualTo(3);
        assertThat(s.min()).isEqualByComparingTo("10");
        assertThat(s.max()).isEqualByComparingTo("30");
        assertThat(s.mean()).isEqualByComparingTo("20.0000");
        // (100 + 0 + 100) / 3 = 66,6667; √66,6667 = 8,1650
        assertThat(s.variance()).isEqualByComparingTo("66.6667");
        assertThat(s.standardDeviation()).isEqualByComparingTo("8.1650");
    }

    @Test
    @DisplayName("regressão: série só com pressão negativa devolve o máximo certo (antes: 4,9E-324)")
    void allNegativeSeries() {
        PressureStatistics s = PressureStatistics.of(of("-5", "-2", "-9"));

        assertThat(s.min()).isEqualByComparingTo("-9");
        assertThat(s.max()).as("o máximo de [-5, -2, -9]").isEqualByComparingTo("-2");
        assertThat(s.mean()).isEqualByComparingTo("-5.3333");
    }

    @Test
    @DisplayName("uma só leitura: variância e desvio zero")
    void singleReading() {
        PressureStatistics s = PressureStatistics.of(of("35.5"));

        assertThat(s.min()).isEqualByComparingTo("35.5");
        assertThat(s.max()).isEqualByComparingTo("35.5");
        assertThat(s.mean()).isEqualByComparingTo("35.5");
        assertThat(s.variance()).isEqualByComparingTo("0");
        assertThat(s.standardDeviation()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("leituras nulas são descartadas")
    void nullReadingsAreDropped() {
        PressureStatistics s = PressureStatistics.of(of("10", null, "30"));
        assertThat(s.count()).isEqualTo(2);
        assertThat(s.mean()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("série vazia ou só de nulos é erro")
    void emptySeriesIsAnError() {
        assertThatThrownBy(() -> PressureStatistics.of(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PressureStatistics.of(of((String) null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("média e variância não acumulam erro de arredondamento intermediário")
    void noIntermediateRounding() {
        // 1, 2: média 1,5; variância (0,25 + 0,25) / 2 = 0,25; desvio 0,5
        PressureStatistics s = PressureStatistics.of(of("1", "2"));
        assertThat(s.mean()).isEqualByComparingTo("1.5");
        assertThat(s.variance()).isEqualByComparingTo("0.25");
        assertThat(s.standardDeviation()).isEqualByComparingTo("0.5");
    }
}
