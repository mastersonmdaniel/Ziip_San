package br.com.ziipsan.intelligence.losses.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Estatística descritiva de uma série de pressão (metros de coluna d'água).
 *
 * <p>Variância e desvio-padrão <b>populacionais</b> (divisão por n), como o serviço já fazia:
 * a série é a população de leituras do período, não uma amostra de outra maior. A média é exata
 * até o fim, e só o resultado é arredondado em 4 casas (HALF_UP).
 *
 * <p>Corrige a versão anterior, que iniciava o máximo em {@code Double.MIN_VALUE}. Esse valor é o
 * <i>menor positivo</i> (4,9E-324), e não o mais negativo: numa série só com pressões negativas, ou
 * seja, justamente as que importam num evento de pressão negativa, o máximo saía como 4,9E-324.
 */
public record PressureStatistics(
        int count,
        BigDecimal min,
        BigDecimal max,
        BigDecimal mean,
        BigDecimal variance,
        BigDecimal standardDeviation) {

    private static final int SCALE = 4;

    /** Leituras nulas são descartadas. Série vazia é erro: não existe média de nada. */
    public static PressureStatistics of(Collection<BigDecimal> readings) {
        List<BigDecimal> values = readings.stream().filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Série de pressão sem nenhuma leitura válida");
        }

        BigDecimal min = values.get(0);
        BigDecimal max = values.get(0);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            if (v.compareTo(min) < 0) min = v;
            if (v.compareTo(max) > 0) max = v;
            sum = sum.add(v);
        }

        BigDecimal n = BigDecimal.valueOf(values.size());
        BigDecimal mean = sum.divide(n, MathContext.DECIMAL128);

        BigDecimal squares = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            BigDecimal d = v.subtract(mean);
            squares = squares.add(d.multiply(d));
        }
        BigDecimal variance = squares.divide(n, MathContext.DECIMAL128);
        BigDecimal std = variance.sqrt(MathContext.DECIMAL128);

        return new PressureStatistics(
                values.size(),
                min,
                max,
                mean.setScale(SCALE, RoundingMode.HALF_UP),
                variance.setScale(SCALE, RoundingMode.HALF_UP),
                std.setScale(SCALE, RoundingMode.HALF_UP));
    }
}
