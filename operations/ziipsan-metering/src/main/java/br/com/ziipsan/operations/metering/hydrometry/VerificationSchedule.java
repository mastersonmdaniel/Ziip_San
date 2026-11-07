package br.com.ziipsan.operations.metering.hydrometry;

import java.time.LocalDate;
import java.util.List;

/**
 * Próxima verificação CALCULADA — nunca digitada (ED-14):
 * {@code última verificação + meter.verification_interval_years} (padrão 7, só reduz).
 * Sem verificação anterior, a base é a verificação inicial exigida na instalação/recebimento.
 */
public final class VerificationSchedule {
    private VerificationSchedule() {}

    public static LocalDate nextVerificationOn(LocalDate lastVerificationOn, int intervalYears) {
        if (lastVerificationOn == null) throw new IllegalArgumentException(
            "Sem verificação registrada não há próxima data calculável: registre a verificação inicial");
        return lastVerificationOn.plusYears(MeterParameterRules.validateVerificationIntervalYears(intervalYears));
    }

    public static boolean isDue(LocalDate nextOn, LocalDate reference) {
        return nextOn != null && !nextOn.isAfter(reference);
    }
    public static boolean isDueWithin(LocalDate nextOn, LocalDate reference, int horizonDays) {
        if (horizonDays < 0) throw new IllegalArgumentException("Horizonte do alerta não pode ser negativo");
        return nextOn != null && !nextOn.isAfter(reference.plusDays(horizonDays));
    }

    /** Hidrômetros vencidos ou a vencer no horizonte — entrada da geração de OS em lote. */
    public record DueMeter(java.util.UUID meterId, LocalDate nextVerificationOn) {}
    public static List<DueMeter> selectDue(List<DueMeter> candidates, LocalDate reference, int horizonDays) {
        return candidates.stream().filter(d -> isDueWithin(d.nextVerificationOn(), reference, horizonDays)).toList();
    }
}
