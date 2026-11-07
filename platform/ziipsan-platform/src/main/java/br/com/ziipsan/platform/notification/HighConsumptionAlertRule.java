package br.com.ziipsan.platform.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Crítica de consumo anormal do fechamento da leitura (P3.5 sobre a média da
 * P2.5): dispara o alerta preventivo quando o consumo do ciclo fica ACIMA de
 * {@code alert.high_consumption_pct}% da média — estritamente acima: consumo
 * igual ao limite (média × (1 + pct/100)) NÃO dispara, porque não está acima.
 *
 * <p>Calculado à mão: média 10 m³ e pct 50 → limite 15 m³; consumo 16 dispara,
 * consumo 15 e 14 não disparam. Média ausente ou não positiva torna a crítica
 * impossível: erro explícito, nunca média presumida.
 */
public final class HighConsumptionAlertRule {

    private HighConsumptionAlertRule() {
    }

    /** O limite (exclusivo) a partir do qual o consumo é anormal: média × (1 + pct/100). */
    public static BigDecimal threshold(BigDecimal averageConsumption, BigDecimal highConsumptionPct) {
        requireAverageAndPct(averageConsumption, highConsumptionPct);
        BigDecimal factor = BigDecimal.ONE.add(highConsumptionPct.movePointLeft(2));
        return averageConsumption.multiply(factor);
    }

    /** Verdade se a crítica dispara: consumo ESTRITAMENTE acima do limite. */
    public static boolean fires(BigDecimal consumption, BigDecimal averageConsumption, BigDecimal highConsumptionPct) {
        if (consumption == null || consumption.signum() < 0) {
            throw new IllegalArgumentException("o consumo do ciclo é obrigatório e não pode ser negativo");
        }
        return consumption.compareTo(threshold(averageConsumption, highConsumptionPct)) > 0;
    }

    /** Excesso do consumo sobre a média, em percentual com 2 casas (para as variáveis do modelo do alerta). */
    public static BigDecimal excessPct(BigDecimal consumption, BigDecimal averageConsumption) {
        if (consumption == null || consumption.signum() < 0) {
            throw new IllegalArgumentException("o consumo do ciclo é obrigatório e não pode ser negativo");
        }
        if (averageConsumption == null || averageConsumption.signum() <= 0) {
            throw new IllegalArgumentException("a média da crítica é obrigatória e precisa ser positiva");
        }
        return consumption.subtract(averageConsumption)
                .divide(averageConsumption, 6, RoundingMode.HALF_UP)
                .movePointRight(2)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireAverageAndPct(BigDecimal averageConsumption, BigDecimal highConsumptionPct) {
        if (averageConsumption == null || averageConsumption.signum() <= 0) {
            throw new IllegalArgumentException("a média da crítica é obrigatória e precisa ser positiva");
        }
        if (highConsumptionPct == null || highConsumptionPct.signum() < 0) {
            throw new IllegalArgumentException("alert.high_consumption_pct é obrigatório e não pode ser negativo");
        }
    }
}
