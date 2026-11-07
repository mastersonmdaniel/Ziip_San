package br.com.ziipsan.operations.metering.hydrometry;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;

/**
 * Critério de troca (ED-14): por IDADE (intervalo de verificação vencido) OU por VOLUME
 * totalizado × classe ({@code meter.replace_by_volume_m3_by_class}), o que ocorrer primeiro.
 */
public final class ReplacementRule {
    private ReplacementRule() {}
    public enum Reason { NONE, AGE, VOLUME, AGE_AND_VOLUME }

    public static Reason evaluate(LocalDate lastVerificationOn, LocalDate reference, int intervalYears,
                                  long totalizedVolumeM3, int accuracyClass, Map<String, Long> replaceByVolumeByClass) {
        boolean byAge = lastVerificationOn != null
                && !VerificationSchedule.nextVerificationOn(lastVerificationOn, intervalYears).isAfter(reference);
        long threshold = MeterParameterRules.validateReplaceVolume(String.valueOf(accuracyClass), replaceByVolumeByClass);
        boolean byVolume = totalizedVolumeM3 >= threshold;
        if (byAge && byVolume) return Reason.AGE_AND_VOLUME;
        if (byAge) return Reason.AGE;
        if (byVolume) return Reason.VOLUME;
        return Reason.NONE;
    }

    public static int ageYears(LocalDate manufacturedOrInstalledOn, LocalDate reference) {
        if (manufacturedOrInstalledOn == null) throw new IllegalArgumentException("Data-base da idade é obrigatória");
        return Period.between(manufacturedOrInstalledOn, reference).getYears();
    }
}
