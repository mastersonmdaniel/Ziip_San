package br.com.ziipsan.operations.metering.selfreading;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A configuracao do score e dos filtros da autoleitura (II-7): pesos dos
 * 4 componentes (somam exactamente 1), limiar de aceite, distancia
 * maxima e precisao maxima da prova geografica. Tudo CONFIGURAVEL --
 * nenhum valor aqui e facto operacional: os valores reais sao do
 * tenant/regulador [A DEFINIR] (P-II7-LIMIARES) e chegam por parametro
 * (chaves em {@link SelfReadingParameters}).
 *
 * @param weightOcr           peso da confianca OCR, em [0, 1]
 * @param weightCoherence    peso da coerencia com o historico, em [0, 1]
 * @param weightPhoto         peso da qualidade da foto, em [0, 1]
 * @param weightGeo           peso da prova geografica, em [0, 1]
 * @param acceptThreshold     limiar de aceite em [0, 100]; score &gt;= limiar aceita
 * @param maxDistanceMetres   distancia maxima a ligacao em metros (&gt; 0)
 * @param maxAccuracyMetres   precisao maxima do GPS em metros (&gt; 0)
 */
public record SelfReadingScoreConfig(BigDecimal weightOcr, BigDecimal weightCoherence,
                                     BigDecimal weightPhoto, BigDecimal weightGeo,
                                     BigDecimal acceptThreshold,
                                     double maxDistanceMetres, double maxAccuracyMetres) {

    public SelfReadingScoreConfig {
        Objects.requireNonNull(weightOcr, "weightOcr");
        Objects.requireNonNull(weightCoherence, "weightCoherence");
        Objects.requireNonNull(weightPhoto, "weightPhoto");
        Objects.requireNonNull(weightGeo, "weightGeo");
        Objects.requireNonNull(acceptThreshold, "acceptThreshold");
        for (BigDecimal w : new BigDecimal[]{weightOcr, weightCoherence, weightPhoto, weightGeo}) {
            if (w.signum() < 0 || w.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("peso fora de [0, 1]: " + w);
            }
        }
        BigDecimal sum = weightOcr.add(weightCoherence).add(weightPhoto).add(weightGeo);
        if (sum.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("os 4 pesos tem de somar exactamente 1; somam " + sum);
        }
        if (acceptThreshold.signum() < 0
                || acceptThreshold.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("limiar de aceite fora de [0, 100]: " + acceptThreshold);
        }
        if (!(maxDistanceMetres > 0) || !Double.isFinite(maxDistanceMetres)) {
            throw new IllegalArgumentException(
                    "distancia maxima precisa ser finita e > 0; recebido " + maxDistanceMetres);
        }
        if (!(maxAccuracyMetres > 0) || !Double.isFinite(maxAccuracyMetres)) {
            throw new IllegalArgumentException(
                    "precisao maxima precisa ser finita e > 0; recebido " + maxAccuracyMetres);
        }
    }

    /**
     * CONSTRUCAO DE PROVA -- nao sao omissoes operacionais nem factos:
     * pesos 0.40/0.30/0.15/0.15, limiar 70, distancia maxima 100 m,
     * precisao maxima 50 m. Servem para as provas deterministicas deste
     * pacote; em producao a configuracao vem dos parametros do tenant
     * (P-II7-LIMIARES: valores reais [A DEFINIR]).
     */
    public static SelfReadingScoreConfig proofDefaults() {
        return new SelfReadingScoreConfig(
                new BigDecimal("0.40"), new BigDecimal("0.30"),
                new BigDecimal("0.15"), new BigDecimal("0.15"),
                new BigDecimal("70"), 100.0, 50.0);
    }
}
