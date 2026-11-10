package br.com.ziipsan.operations.metering.critique;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estimacao VEE (II-2, E4): quando a leitura e invalida/ausente, o consumo
 * estimado sai do historico por metodo declarado. Deterministica: mesmo
 * historico + mesmo metodo = mesmo valor, sem relogio nem aleatoriedade.
 *
 * <p>Historico vazio = recusa tipificada ({@link VeeEstimationException}):
 * estimar sem base seria inventar consumo.
 */
public final class VeeEstimator {

    /** Recusa tipificada da estimacao. */
    public static final class VeeEstimationException extends IllegalStateException {
        public VeeEstimationException(String message) {
            super(message);
        }
    }

    /**
     * @return consumo estimado (inteiro &gt;= 0)
     * @throws VeeEstimationException historico vazio
     */
    public int estimate(List<Integer> history, EstimationMethod method) {
        if (history == null || history.isEmpty()) {
            throw new VeeEstimationException("estimacao VEE sem historico: nenhum consumo para estimar");
        }
        return switch (method) {
            case HISTORICAL_MEAN -> CritiqueEngine.historicalMean(history)
                    .setScale(0, RoundingMode.HALF_UP).intValueExact();
            case HISTORICAL_MEDIAN -> median(history);
            case LAST_VALID -> history.get(history.size() - 1);
        };
    }

    private int median(List<Integer> history) {
        List<Integer> sorted = new ArrayList<>(history);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        BigDecimal middle = BigDecimal.valueOf(sorted.get(n / 2 - 1))
                .add(BigDecimal.valueOf(sorted.get(n / 2)))
                .divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
        return middle.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
