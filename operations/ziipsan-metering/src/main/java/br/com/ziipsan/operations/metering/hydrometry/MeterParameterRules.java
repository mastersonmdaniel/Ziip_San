package br.com.ziipsan.operations.metering.hydrometry;

/** Travas dos parâmetros da ED-14. "Só reduz": o intervalo de verificação nunca passa de 7 anos. */
public final class MeterParameterRules {
    private MeterParameterRules() {}
    public static final int DEFAULT_VERIFICATION_INTERVAL_YEARS = 7;

    public static int validateVerificationIntervalYears(int years) {
        if (years < 1 || years > DEFAULT_VERIFICATION_INTERVAL_YEARS) throw new IllegalArgumentException(
            "meter.verification_interval_years deve estar entre 1 e " + DEFAULT_VERIFICATION_INTERVAL_YEARS
            + " (padrão 7, só reduz): " + years);
        return years;
    }
    public static int validateSamplePlanPct(int pct) {
        if (pct < 0 || pct > 100) throw new IllegalArgumentException("meter.sample_plan_pct deve estar entre 0 e 100: " + pct);
        return pct;
    }
    public static long validateReplaceVolume(String accuracyClassKey, java.util.Map<String, Long> byClass) {
        if (byClass == null) throw new IllegalArgumentException("meter.replace_by_volume_m3_by_class sem valor vigente");
        Long v = byClass.get(accuracyClassKey);
        if (v == null) throw new IllegalArgumentException(
            "meter.replace_by_volume_m3_by_class sem limiar para a classe " + accuracyClassKey);
        if (v <= 0) throw new IllegalArgumentException("Limiar de troca por volume deve ser positivo: " + v);
        return v;
    }
}
