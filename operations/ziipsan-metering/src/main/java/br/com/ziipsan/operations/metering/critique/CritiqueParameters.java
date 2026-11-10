package br.com.ziipsan.operations.metering.critique;

/**
 * Chaves de parametro do tenant propostas pela II-2 para o catalogo global
 * de parametros (nos moldes de {@code MeteringParameters} da P2.5 e de
 * {@code LeakMeterParameters} da P3.28). O REGISTO destas chaves no
 * catalogo e a fiacao a {@code TenantParameterService} sao da integracao:
 * NENHUM valor operacional esta fixado aqui -- os limiares reais sao
 * {@code [A DEFINIR]} pelo tenant/regulador (pendencia P-II2-LIMIARES).
 * Ate la, configuracoes so existem como construcao de prova declarada
 * ({@link CritiqueConfig#proofDefaults()}, {@link LeakDetectionConfig#proofDefaults()}).
 */
public final class CritiqueParameters {

    /** Regras de critica do tenant (lista ordenada de {@code CritiqueRule}). */
    public static final String CRITIQUE_RULES_KEY = "meter.critique_rules";
    /** Metodo de estimacao VEE do tenant ({@code EstimationMethod}). */
    public static final String ESTIMATION_METHOD_KEY = "meter.vee_estimation_method";
    /** Janela nocturna da deteccao proactiva (horas, ex.: [2, 3]). */
    public static final String LEAK_NIGHT_HOURS_KEY = "meter.leak_proactive_night_hours";
    /** Caudal minimo nocturno da deteccao proactiva. */
    public static final String LEAK_MIN_NIGHT_FLOW_KEY = "meter.leak_proactive_min_night_flow";
    /** Noites consecutivas exigidas para o sinal proactivo. */
    public static final String LEAK_CONSECUTIVE_NIGHTS_KEY = "meter.leak_proactive_consecutive_nights";

    private CritiqueParameters() {
    }
}
