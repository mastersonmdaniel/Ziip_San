package br.com.sigsan.commercial.billing.consumption;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Faixa de leitura esperada a partir da média de consumo (relatório 02, seção 2.5). Serve de
 * veículo para a armadilha de {@code BigDecimal(double)}: os fatores entram como texto.
 *
 * <pre>
 *   m ≤ 10:        [LA, LA + m + 10]
 *   11 ≤ m ≤ 20:   [LA + 0,4·m, LA + 1,6·m]
 *   21 ≤ m ≤ 45:   [LA + 0,5·m, LA + 1,5·m]
 *   46 ≤ m ≤ 100:  [LA + 0,6·m, LA + 1,4·m]
 *   m &gt; 100:      [LA + 0,7·m, LA + 1,3·m]
 * </pre>
 * Cada extremo é arredondado ao inteiro com HALF_UP; se passa do maior valor do mostrador
 * ({@code 10^dígitos − 1}), subtrai-se {@code 10^dígitos} (virada do hidrômetro).
 *
 * <p>Nota: a comparação de uma leitura com a faixa, e o tratamento de faixa que cruza a virada
 * ({@code low > high}), pertencem ao cálculo de consumo da leitura (módulo de medição).
 */
public final class ExpectedReadingRange {

    public record Range(int low, int high) {
    }

    private ExpectedReadingRange() {
    }

    public static Range of(int average, int previousReading, int digits) {
        if (average < 0 || previousReading < 0 || digits < 1) {
            throw new IllegalArgumentException("Média, leitura anterior e dígitos devem ser positivos");
        }
        int low;
        int high;
        if (average <= 10) {
            low = previousReading;
            high = previousReading + average + 10;
        } else if (average <= 20) {
            low = previousReading + scaled(average, "0.4");
            high = previousReading + scaled(average, "1.6");
        } else if (average <= 45) {
            low = previousReading + scaled(average, "0.5");
            high = previousReading + scaled(average, "1.5");
        } else if (average <= 100) {
            low = previousReading + scaled(average, "0.6");
            high = previousReading + scaled(average, "1.4");
        } else {
            low = previousReading + scaled(average, "0.7");
            high = previousReading + scaled(average, "1.3");
        }
        long modulus = BigDecimal.TEN.pow(digits).longValueExact();
        return new Range(wrap(low, modulus), wrap(high, modulus));
    }

    private static int scaled(int average, String factor) {
        return new BigDecimal(factor).multiply(BigDecimal.valueOf(average))
                .setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static int wrap(int reading, long modulus) {
        return reading > modulus - 1 ? (int) (reading - modulus) : reading;
    }
}
