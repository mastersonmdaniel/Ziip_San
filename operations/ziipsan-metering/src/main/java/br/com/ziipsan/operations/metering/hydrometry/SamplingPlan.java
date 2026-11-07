package br.com.ziipsan.operations.metering.hydrometry;

/** Plano de amostragem de lote para ensaio (ED-14): ceil(lote × meter.sample_plan_pct%). */
public final class SamplingPlan {
    private SamplingPlan() {}
    public static int sampleSize(int lotSize, int samplePlanPct) {
        if (lotSize < 0) throw new IllegalArgumentException("Tamanho do lote não pode ser negativo");
        MeterParameterRules.validateSamplePlanPct(samplePlanPct);
        if (lotSize == 0 || samplePlanPct == 0) return 0;
        int size = (int) Math.ceil(lotSize * samplePlanPct / 100.0);
        return Math.max(1, Math.min(size, lotSize));
    }
}
