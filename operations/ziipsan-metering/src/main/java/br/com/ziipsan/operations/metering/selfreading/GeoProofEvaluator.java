package br.com.ziipsan.operations.metering.selfreading;

/**
 * Avaliador da prova geografica (II-7, E4): distancia haversine entre a
 * posicao capturada na autoleitura e as coordenadas da ligacao, com
 * filtro por limiares CONFIGURAVEIS (distancia maxima e precisao maxima
 * -- valores operacionais sao [A DEFINIR], pendencia P-II7-LIMIARES;
 * ver {@link SelfReadingScoreConfig#proofDefaults()}).
 *
 * <p>Regras declaradas:
 * <ul>
 *   <li>distancia &lt;= maxima =&gt; dentro do filtro (fronteira
 *       INCLUSIVA: igual ao limiar esta dentro); distancia &gt; maxima
 *       =&gt; fora -- o chamador (servico de intake) sinaliza e manda
 *       para revisao humana, nunca aceita em silencio;</li>
 *   <li>precisao &lt;= maxima =&gt; suficiente (inclusiva);</li>
 *   <li>sem prova ou sem coordenadas da ligacao =&gt;
 *       {@link GeoProofResult#absent()}: distancia null, sem veredito
 *       inventado;</li>
 *   <li>haversine com o raio medio da Terra de 6.371.000 m -- a mesma
 *       convencao da ED-15 ({@code PositionDeviationRule}).</li>
 * </ul>
 *
 * <p>Difere da ED-15 num ponto de desenho, declarado: la o desvio de
 * posicao do leiturista gera alerta SEM bloqueio; aqui a prova e do
 * proprio consumidor e a distancia alem do limiar e porta de aceite --
 * por isso o resultado alimenta um gate no intake, nao so um alerta.
 */
public final class GeoProofEvaluator {

    private static final double EARTH_RADIUS_METRES = 6_371_000.0;

    private GeoProofEvaluator() {
    }

    /**
     * @param proof              prova capturada; null = sem prova
     * @param connectionPosition coordenadas da ligacao; null = ligacao sem coordenadas
     * @param config             limiares vigentes (maxima distancia/precisao)
     */
    public static GeoProofResult evaluate(GeoProof proof, GeoPoint connectionPosition,
                                          SelfReadingScoreConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("configuracao de limiares e obrigatoria");
        }
        if (proof == null || connectionPosition == null) {
            return GeoProofResult.absent();
        }
        double distance = distanceMetres(proof.position(), connectionPosition);
        boolean within = distance <= config.maxDistanceMetres();
        boolean accuracyOk = proof.accuracyMetres() <= config.maxAccuracyMetres();
        return new GeoProofResult(distance, within, accuracyOk, true);
    }

    /** Distancia haversine em metros entre dois pontos. */
    public static double distanceMetres(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METRES * Math.asin(Math.sqrt(h));
    }
}
