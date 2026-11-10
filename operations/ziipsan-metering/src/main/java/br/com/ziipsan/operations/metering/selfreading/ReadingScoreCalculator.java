package br.com.ziipsan.operations.metering.selfreading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * O SCORE da autoleitura (II-7, E3): funcao DETERMINISTICA e DECLARADA,
 * sem relogio, sem aleatoriedade, sem estado. A mesma entrada produz
 * sempre o mesmo score.
 *
 * <p>Formula (componentes em [0, 1]; pesos da configuracao, soma 1):
 * <pre>
 *   total = 100 * (wOcr*ocr + wCoherence*coherence + wPhoto*photo + wGeo*geo)
 * </pre>
 * Total arredondado a 2 casas, HALF_UP. Componentes:
 * <ul>
 *   <li><b>ocr</b> = confianca devolvida pela porta OCR;</li>
 *   <li><b>coherence</b> = coerencia do consumo derivado
 *       (leitura OCR - leitura anterior) com a media aritmetica exacta
 *       do historico: {@code 1 - min(1, |consumo - media| / media)},
 *       divisao em escala 10 HALF_UP (a convencao da II-2). Casos
 *       declarados sem evidencia: sem historico, sem leitura anterior
 *       ou media zero com consumo nao-zero =&gt; 0; media zero com
 *       consumo zero =&gt; 1. Se o consumidor declarou uma leitura e ela
 *       DIVERGE da leitura OCR, coherence = 0 (declarado: a divergencia
 *       anula a coerencia, nao a reduz por palpite);</li>
 *   <li><b>photo</b> = qualidade declarada da foto:
 *       {@code min(1, min(largura, altura) / 1000)} -- foto com o lado
 *       menor &gt;= 1000 px vale 1; sem foto =&gt; 0;</li>
 *   <li><b>geo</b> = prova geografica: sem prova =&gt; 0; precisao acima
 *       da maxima =&gt; 0 (prova nao confiavel nao pontua); distancia
 *       dentro do maximo =&gt; 1; alem do maximo, decai linearmente:
 *       {@code max(0, 1 - (distancia - maximo) / maximo)} -- ao dobro
 *       do maximo vale 0.</li>
 * </ul>
 */
public final class ReadingScoreCalculator {

    private static final BigDecimal PHOTO_FULL_SIDE_PX = BigDecimal.valueOf(1000);

    private ReadingScoreCalculator() {
    }

    /**
     * @param ocrConfidence     confianca da extraccao OCR, em [0, 1]
     * @param ocrReading        leitura extraida pelo OCR (&gt;= 0)
     * @param declaredReading   leitura declarada pelo consumidor; null = nao declarou
     * @param previousReading   leitura anterior da unidade; null = primeira leitura
     * @param history           consumos historicos (&gt;= 0), do mais antigo ao mais recente
     * @param photo             artefacto da foto; null = sem foto
     * @param geo               resultado da prova geografica (do {@link GeoProofEvaluator})
     * @param config            pesos e limiares vigentes
     */
    public static ReadingScore score(BigDecimal ocrConfidence, int ocrReading,
                                     Integer declaredReading, Integer previousReading,
                                     List<Integer> history, PhotoArtifact photo,
                                     GeoProofResult geo, SelfReadingScoreConfig config) {
        if (ocrConfidence == null || ocrConfidence.signum() < 0
                || ocrConfidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confianca OCR fora de [0, 1]: " + ocrConfidence);
        }
        if (config == null || geo == null || history == null) {
            throw new IllegalArgumentException("config, geo e history sao obrigatorios");
        }
        BigDecimal ocr = ocrConfidence;
        BigDecimal coherence = coherence(ocrReading, declaredReading, previousReading, history);
        BigDecimal photoComponent = photoComponent(photo);
        BigDecimal geoComponent = geoComponent(geo, config);
        BigDecimal total = config.weightOcr().multiply(ocr)
                .add(config.weightCoherence().multiply(coherence))
                .add(config.weightPhoto().multiply(photoComponent))
                .add(config.weightGeo().multiply(geoComponent))
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        return new ReadingScore(total, ocr, coherence, photoComponent, geoComponent);
    }

    /** Componente de coerencia, conforme a formula declarada na classe. */
    public static BigDecimal coherence(int ocrReading, Integer declaredReading,
                                       Integer previousReading, List<Integer> history) {
        if (declaredReading != null && declaredReading.intValue() != ocrReading) {
            return BigDecimal.ZERO;
        }
        if (previousReading == null || history == null || history.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long consumption = (long) ocrReading - previousReading.longValue();
        if (consumption < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int value : history) {
            if (value < 0) {
                throw new IllegalArgumentException("historico so aceita consumos >= 0");
            }
            sum = sum.add(BigDecimal.valueOf(value));
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(history.size()), 10, RoundingMode.HALF_UP);
        BigDecimal cons = BigDecimal.valueOf(consumption);
        if (mean.signum() == 0) {
            return cons.signum() == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        BigDecimal ratio = cons.subtract(mean).abs().divide(mean, 10, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.ONE) >= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.ONE.subtract(ratio);
    }

    /** Componente da foto: min(1, lado menor / 1000); sem foto = 0. */
    public static BigDecimal photoComponent(PhotoArtifact photo) {
        if (photo == null) {
            return BigDecimal.ZERO;
        }
        int minSide = Math.min(photo.widthPx(), photo.heightPx());
        BigDecimal ratio = BigDecimal.valueOf(minSide)
                .divide(PHOTO_FULL_SIDE_PX, 10, RoundingMode.HALF_UP);
        return ratio.compareTo(BigDecimal.ONE) >= 0 ? BigDecimal.ONE : ratio;
    }

    /** Componente geografico, conforme a formula declarada na classe. */
    public static BigDecimal geoComponent(GeoProofResult geo, SelfReadingScoreConfig config) {
        if (geo == null || !geo.present()) {
            return BigDecimal.ZERO;
        }
        if (!geo.accuracySufficient()) {
            return BigDecimal.ZERO;
        }
        if (geo.withinMaxDistance()) {
            return BigDecimal.ONE;
        }
        double distance = geo.distanceMetres();
        double max = config.maxDistanceMetres();
        double decayed = 1.0 - (distance - max) / max;
        if (decayed <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(decayed);
    }
}
