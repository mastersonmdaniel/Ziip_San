package br.com.ziipsan.operations.metering.selfreading;

/**
 * Chaves de parametro PROPOSTAS para a autoleitura com OCR (II-7), no
 * padrao do catalogo do tenant. O registo no catalogo global e a fiacao
 * ao servico de parametros do tenant sao da integracao (P-II7-FIACAO).
 * NENHUM valor operacional esta fixado aqui: limiares de score e de
 * distancia sao [A DEFINIR] ate fonte/decisao do tenant (P-II7-LIMIARES)
 * -- os valores em {@link SelfReadingScoreConfig#proofDefaults()} sao
 * construcao de prova declarada, nao omissoes operacionais.
 */
public final class SelfReadingParameters {

    /** Limiar de score (0..100) para aceite automatico para a critica. */
    public static final String SCORE_ACCEPT_THRESHOLD_KEY = "meter.selfreading_score_accept_threshold";
    /** Distancia maxima a ligacao, em metros, para a prova geografica passar no filtro. */
    public static final String GEO_MAX_DISTANCE_METRES_KEY = "meter.selfreading_geo_max_distance_m";
    /** Precisao maxima aceitavel do GPS, em metros, para a prova contar no score. */
    public static final String GEO_MAX_ACCURACY_METRES_KEY = "meter.selfreading_geo_max_accuracy_m";
    /** Peso da confianca OCR no score (fraccao; os 4 pesos somam 1). */
    public static final String WEIGHT_OCR_KEY = "meter.selfreading_weight_ocr";
    /** Peso da coerencia com o historico no score. */
    public static final String WEIGHT_COHERENCE_KEY = "meter.selfreading_weight_coherence";
    /** Peso da qualidade declarada da foto no score. */
    public static final String WEIGHT_PHOTO_KEY = "meter.selfreading_weight_photo";
    /** Peso da prova geografica no score. */
    public static final String WEIGHT_GEO_KEY = "meter.selfreading_weight_geo";

    private SelfReadingParameters() {
    }
}
