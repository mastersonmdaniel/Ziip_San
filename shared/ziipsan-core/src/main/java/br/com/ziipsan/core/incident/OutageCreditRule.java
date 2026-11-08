package br.com.ziipsan.core.incident;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Credito na fatura por interrupcao prolongada (RAS-29): regra
 * OPCIONAL, DESLIGADA por padrao ({@code outage.credit_enabled}),
 * ligada pelo perfil da agencia que a exigir (ex.: AGERGS REN
 * 37/2017, citada no aceite). A formula NAO tem valor de fabrica:
 * limiar, percentual por hora excedente e teto sao DADOS do
 * tenant/perfil; sem regra completa, nao ha credito — nunca um
 * percentual presumido.
 */
public record OutageCreditRule(boolean enabled, Integer thresholdHoursOrNull,
                               BigDecimal percentPerHourBeyondOrNull, BigDecimal capPercentOrNull) {

    /** Percentual de credito sobre a fatura da unidade afetada, ou vazio se desligado/incompleto. */
    public Optional<BigDecimal> creditPercent(NetworkIncident incident, java.time.LocalDateTime now) {
        if (!enabled || thresholdHoursOrNull == null || percentPerHourBeyondOrNull == null
                || capPercentOrNull == null) return Optional.empty();
        long hours = incident.durationAt(now).toHours();
        if (hours <= thresholdHoursOrNull) return Optional.empty();
        BigDecimal pct = percentPerHourBeyondOrNull.multiply(BigDecimal.valueOf(hours - thresholdHoursOrNull));
        return Optional.of(pct.min(capPercentOrNull));
    }

    public static OutageCreditRule disabled() { return new OutageCreditRule(false, null, null, null); }
}
