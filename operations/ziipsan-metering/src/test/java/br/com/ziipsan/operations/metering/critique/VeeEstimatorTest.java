package br.com.ziipsan.operations.metering.critique;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** §4.3 da estimativa: determinismo e valor exacto calculado a mao. */
class VeeEstimatorTest {

    private final VeeEstimator estimator = new VeeEstimator();

    @Test
    void mediaExactaCalculadaAMao() {
        // (10 + 12 + 14) / 3 = 12 exacto
        assertEquals(12, estimator.estimate(List.of(10, 12, 14), EstimationMethod.HISTORICAL_MEAN));
    }

    @Test
    void mediaComArredondamentoHalfUp() {
        // (10 + 11) / 2 = 10.5 -> HALF_UP = 11
        assertEquals(11, estimator.estimate(List.of(10, 11), EstimationMethod.HISTORICAL_MEAN));
    }

    @Test
    void medianaImparEPar() {
        assertEquals(12, estimator.estimate(List.of(14, 10, 12), EstimationMethod.HISTORICAL_MEDIAN));
        // par: (12 + 14) / 2 = 13
        assertEquals(13, estimator.estimate(List.of(10, 12, 14, 20), EstimationMethod.HISTORICAL_MEDIAN));
    }

    @Test
    void ultimoValidoEMaisRecente() {
        assertEquals(14, estimator.estimate(List.of(10, 12, 14), EstimationMethod.LAST_VALID));
    }

    @Test
    void determinismo() {
        List<Integer> history = List.of(7, 9, 11, 13);
        for (EstimationMethod method : EstimationMethod.values()) {
            assertEquals(estimator.estimate(history, method), estimator.estimate(history, method),
                    "metodo " + method);
        }
    }

    @Test
    void historicoVazioRecusaTipificada() {
        assertThrows(VeeEstimator.VeeEstimationException.class,
                () -> estimator.estimate(List.of(), EstimationMethod.HISTORICAL_MEAN));
    }
}
