package br.com.ziipsan.operations.metering.selfreading;

/**
 * Resultado da avaliacao da prova geografica (II-7): a distancia medida
 * a ligacao e os dois vereditos do filtro. {@code distanceMetres} e
 * {@code null} quando nao ha as duas posicoes -- nada e inventado.
 *
 * @param distanceMetres      distancia haversine em metros; null = nao medida
 * @param withinMaxDistance   distancia &lt;= distancia maxima configurada
 *                            (fronteira INCLUSIVA, declarada: igual ao
 *                            limiar esta dentro); false quando nao medida
 * @param accuracySufficient  precisao &lt;= precisao maxima configurada
 *                            (fronteira inclusiva); false quando nao ha prova
 * @param present             true quando havia prova e posicao da ligacao
 */
public record GeoProofResult(Double distanceMetres, boolean withinMaxDistance,
                             boolean accuracySufficient, boolean present) {

    /** Sem prova ou sem posicao da ligacao: nada medido, nada presumido. */
    public static GeoProofResult absent() {
        return new GeoProofResult(null, false, false, false);
    }
}
