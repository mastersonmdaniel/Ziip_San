package br.com.ziipsan.reporting.forecast;

import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.ziipsan.reporting.forecast.SyntheticForecastBase.bd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionCurveTest {

    @Test
    void estimaTaxasPorFaixaDaBaseSintetica() {
        CollectionCurve curve = SyntheticForecastBase.curve();

        assertThat(curve.entryFor("ATE_30").recoveryRate()).isEqualByComparingTo("0.900000");
        assertThat(curve.entryFor("ATE_30").lossRate()).isEqualByComparingTo("0.100000");
        assertThat(curve.entryFor("D31_60").recoveryRate()).isEqualByComparingTo("0.700000");
        assertThat(curve.entryFor("D31_60").lossRate()).isEqualByComparingTo("0.300000");
        assertThat(curve.entryFor("D61_90").recoveryRate()).isEqualByComparingTo("0.400000");
        assertThat(curve.entryFor("D61_90").lossRate()).isEqualByComparingTo("0.600000");
        assertThat(curve.entryFor("ACIMA_90").recoveryRate()).isEqualByComparingTo("0.200000");
        assertThat(curve.entryFor("ACIMA_90").lossRate()).isEqualByComparingTo("0.800000");
    }

    @Test
    void totaisErecuperacaoGeral() {
        CollectionCurve curve = SyntheticForecastBase.curve();

        assertThat(curve.totalBilled()).isEqualByComparingTo("18000.00");
        assertThat(curve.totalCollected()).isEqualByComparingTo("13500.00");
        // 13.500 / 18.000 = 0,75
        assertThat(curve.overallRecoveryRate()).isEqualByComparingTo("0.750000");
        assertThat(curve.overallLossRate()).isEqualByComparingTo("0.250000");
    }

    @Test
    void recebidoMaiorQueFaturadoERecusado() {
        DelayBracket bracket = DelayBracket.standard().get(0);
        assertThatThrownBy(() -> new BracketObservation(bracket, bd("100.00"), bd("100.01")))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("COLLECTED_EXCEEDS_BILLED");
    }

    @Test
    void faixaRepetidaOuSobrepostaERecusada() {
        List<DelayBracket> b = DelayBracket.standard();
        assertThatThrownBy(() -> CollectionCurve.estimate(List.of(
                new BracketObservation(b.get(0), bd("100.00"), bd("50.00")),
                new BracketObservation(b.get(0), bd("100.00"), bd("50.00")))))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("BRACKET_DUPLICATED");

        assertThatThrownBy(() -> CollectionCurve.estimate(List.of(
                new BracketObservation(new DelayBracket("X", 0, 45), bd("100.00"), bd("50.00")),
                new BracketObservation(new DelayBracket("Y", 31, 60), bd("100.00"), bd("50.00")))))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("BRACKETS_OVERLAP");
    }

    @Test
    void faixaAusenteNaCurvaERecusadaNomeada() {
        CollectionCurve curve = SyntheticForecastBase.curve();
        assertThatThrownBy(() -> curve.entryFor("D181_360"))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("CURVE_BRACKET_MISSING");
    }

    @Test
    void recuperacaoAjustadaPeloDeslocamentoComTeto() {
        CollectionCurve curve = SyntheticForecastBase.curve();
        // 0,90 + 0,05 = 0,95; 0,90 + 0,20 estoura e e limitada a 1
        assertThat(curve.adjustedRecoveryRate("ATE_30", bd("0.05"))).isEqualByComparingTo("0.950000");
        assertThat(curve.adjustedRecoveryRate("ATE_30", bd("0.20"))).isEqualByComparingTo("1.000000");
        assertThat(curve.adjustedRecoveryRate("ACIMA_90", bd("-0.05"))).isEqualByComparingTo("0.150000");
    }
}
