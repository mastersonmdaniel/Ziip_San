package br.com.ziipsan.reporting.forecast;

import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.ziipsan.reporting.forecast.SyntheticForecastBase.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LossCurveTest {

    private static List<BracketExposure> exposures() {
        return List.of(
                new BracketExposure("ATE_30", bd("2000.00")),
                new BracketExposure("D31_60", bd("1000.00")),
                new BracketExposure("D61_90", bd("500.00")),
                new BracketExposure("ACIMA_90", bd("500.00")));
    }

    @Test
    void provisaoExpectedLossRecalculadaAMao() {
        LossCurve loss = LossCurve.fromCurve(SyntheticForecastBase.curve(), exposures());

        // 2.000x0,10 + 1.000x0,30 + 500x0,60 + 500x0,80 = 200 + 300 + 300 + 400
        assertThat(loss.totalExposure()).isEqualByComparingTo("4000.00");
        assertThat(loss.provisionAmount()).isEqualByComparingTo("1200.00");
        assertThat(loss.segments()).hasSize(4);
        assertThat(loss.segments().get(0).expectedLossRate()).isEqualByComparingTo("0.100000");
        assertThat(loss.segments().get(3).expectedLossRate()).isEqualByComparingTo("0.800000");
    }

    @Test
    void jsonDosSegmentosNoFormatoDaEd32() {
        LossCurve loss = LossCurve.fromCurve(SyntheticForecastBase.curve(), exposures());
        String json = loss.segmentsJson();

        assertThat(json).contains("\"bracket\":\"ATE_30\"");
        assertThat(json).contains("\"exposure\":\"2000.00\"");
        assertThat(json).contains("\"expectedLossRate\":\"0.100000\"");
        assertThat(json).contains("\"bracket\":\"ACIMA_90\"");
    }

    @Test
    void exposicaoEmFaixaForaDaCurvaERecusada() {
        assertThatThrownBy(() -> LossCurve.fromCurve(SyntheticForecastBase.curve(),
                List.of(new BracketExposure("D181_360", bd("100.00")))))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("CURVE_BRACKET_MISSING");
    }

    @Test
    void estoqueVazioExplicitoGeraProvisaoZero() {
        LossCurve loss = LossCurve.fromCurve(SyntheticForecastBase.curve(), List.of());
        assertThat(loss.provisionAmount()).isEqualByComparingTo("0.00");
        assertThat(loss.segments()).isEmpty();
    }
}
