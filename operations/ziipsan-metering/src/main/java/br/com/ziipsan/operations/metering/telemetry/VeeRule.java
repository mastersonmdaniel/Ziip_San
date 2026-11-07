package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A parte pura da VEE — validação e estimativa (ED-17): o que se confere numa
 * leitura AMR antes de ela virar leitura de faturamento, e como se estima a
 * leitura que o dispositivo não entregou. A edição e a promoção vivem no
 * {@code TelemetryVeeService}.
 *
 * <h2>Validação</h2>
 * Recusa (e lista <b>todos</b> os motivos) quando: a leitura é negativa; o
 * instante é futuro; a leitura excede a capacidade do mostrador
 * ({@code 10^dígitos − 1}, o mesmo limite da validação do retorno da P2.5);
 * ou a leitura é menor que a anterior sem virada de mostrador que a explique.
 * Leitura ausente NÃO é violação: é o caso da estimativa.
 *
 * <h2>Estimativa</h2>
 * {@code anterior + média diária × dias}, o efeito "anterior mais média" que
 * a cascata de consumo já conhece ({@code PREVIOUS_PLUS_AVERAGE}), com a média
 * diária vinda de quem chama (o histórico do ponto) — sem média, não há
 * estimativa: erro explícito, nunca um valor presumido.
 */
public final class VeeRule {

    private VeeRule() {
    }

    public enum Violation {
        DIAL_NEGATIVE,
        READ_AT_IN_FUTURE,
        DIAL_EXCEEDS_CAPACITY,
        DIAL_BELOW_PREVIOUS
    }

    public static List<Violation> validate(AmrReading reading, Integer previousDial,
                                           int dialDigits, Instant now) {
        TelemetryInput.notNull(reading, "reading");
        TelemetryInput.notNull(now, "now");
        TelemetryInput.require(dialDigits >= 1 && dialDigits <= 9,
                MeteringErrorCode.INVALID_INPUT, "dialDigits fora de 1..9");

        List<Violation> violations = new ArrayList<>();
        if (reading.readAt().toInstant().isAfter(now)) {
            violations.add(Violation.READ_AT_IN_FUTURE);
        }
        Integer dial = reading.dialReading();
        if (dial != null) {
            if (dial < 0) {
                violations.add(Violation.DIAL_NEGATIVE);
            }
            if (dial > dialMaximum(dialDigits)) {
                violations.add(Violation.DIAL_EXCEEDS_CAPACITY);
            }
            if (previousDial != null && dial < previousDial && !isRollover(previousDial, dial, dialDigits)) {
                violations.add(Violation.DIAL_BELOW_PREVIOUS);
            }
        }
        return violations;
    }

    /** O maior valor do mostrador com {@code digits} dígitos: {@code 10^digits − 1}. */
    public static int dialMaximum(int digits) {
        int max = 1;
        for (int i = 0; i < digits; i++) {
            max *= 10;
        }
        return max - 1;
    }

    /** Virada de mostrador: a anterior está na metade alta e a nova na metade baixa do mostrador. */
    private static boolean isRollover(int previousDial, int dial, int dialDigits) {
        int capacity = dialMaximum(dialDigits) + 1;
        return previousDial >= capacity / 2 && dial < capacity / 2;
    }

    /**
     * A leitura estimada: {@code anterior + média diária × dias}, arredondada
     * HALF_UP para o mostrador (inteiro), como na faixa esperada da P2.5.
     */
    public static int estimateDial(int previousDial, BigDecimal averageDailyConsumption, long days) {
        TelemetryInput.require(previousDial >= 0, MeteringErrorCode.INVALID_INPUT,
                "previousDial negativo");
        TelemetryInput.require(averageDailyConsumption != null
                        && averageDailyConsumption.signum() > 0, MeteringErrorCode.INVALID_INPUT,
                "sem média diária positiva não há estimativa");
        TelemetryInput.require(days >= 0, MeteringErrorCode.INVALID_INPUT, "days negativo");
        return BigDecimal.valueOf(previousDial)
                .add(averageDailyConsumption.multiply(BigDecimal.valueOf(days)))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
