package br.com.ziipsan.reporting.forecast;

import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.ziipsan.reporting.forecast.SyntheticForecastBase.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LagDistributionTest {

    @Test
    void estimaFracoesDaBaseSintetica() {
        LagDistribution lag = SyntheticForecastBase.lagDistribution();

        assertThat(lag.fractionAt(0)).isEqualByComparingTo("0.400000");
        assertThat(lag.fractionAt(1)).isEqualByComparingTo("0.200000");
        assertThat(lag.fractionAt(2)).isEqualByComparingTo("0.100000");
        assertThat(lag.fractionAt(3)).isEqualByComparingTo("0.050000");
        assertThat(lag.totalFraction()).isEqualByComparingTo("0.750000");
        assertThat(lag.neverCollectedFraction()).isEqualByComparingTo("0.250000");
        // Lag sem observacao: zero nesta distribuicao (declarado no javadoc).
        assertThat(lag.fractionAt(9)).isEqualByComparingTo("0.000000");
    }

    @Test
    void mediaPonderadaComDuasCoortesNoMesmoLag() {
        // lag 0: coortes de 10.000 (recebido 5.000) e 30.000 (recebido 10.000)
        // fracao = 15.000 / 40.000 = 0,375
        LagDistribution lag = LagDistribution.estimate(List.of(
                new LagObservation(0, bd("10000.00"), bd("5000.00")),
                new LagObservation(0, bd("30000.00"), bd("10000.00"))));
        assertThat(lag.fractionAt(0)).isEqualByComparingTo("0.375000");
    }

    @Test
    void ajustePorDeslocamentoEscalaAsFracoes() {
        LagDistribution adjusted = SyntheticForecastBase.lagDistribution().adjusted(bd("0.05"));
        // novo total 0,80; fator 0,80/0,75 = 1,066667 (escala 6)
        // 0,40x = 0,426667; 0,20x = 0,213333; 0,10x = 0,106667; 0,05x = 0,053333
        assertThat(adjusted.fractionAt(0)).isEqualByComparingTo("0.426667");
        assertThat(adjusted.fractionAt(1)).isEqualByComparingTo("0.213333");
        assertThat(adjusted.fractionAt(2)).isEqualByComparingTo("0.106667");
        assertThat(adjusted.fractionAt(3)).isEqualByComparingTo("0.053333");
        assertThat(adjusted.totalFraction()).isEqualByComparingTo("0.800000");
    }

    @Test
    void fracoesAcimaDeUmSaoRecusadas() {
        // O mesmo recebimento contado em dois lags: 0,80 + 0,80 > 1.
        assertThatThrownBy(() -> LagDistribution.estimate(List.of(
                new LagObservation(0, bd("10000.00"), bd("8000.00")),
                new LagObservation(1, bd("10000.00"), bd("8000.00")))))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("LAG_FRACTIONS_EXCEED_ONE");
    }
}
