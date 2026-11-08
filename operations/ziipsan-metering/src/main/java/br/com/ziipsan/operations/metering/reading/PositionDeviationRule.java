package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

/**
 * Desvio de posição da leitura (ED-15): compara a posição onde a leitura
 * foi capturada — vinda do app de campo da ED-18 — com a posição do
 * imóvel. Desvio acima do limiar gera <b>alerta, sem bloqueio</b>: a
 * leitura é aceita e marcada; GPS de aparelho tem erro próprio e travar
 * a leitura por posição puniria o leiturista pelo aparelho.
 *
 * <p>O limiar em metros é <b>argumento obrigatório</b>: o aceite da ED-15
 * não nomeia parâmetro do tenant para ele, então nenhum valor é presumido
 * nesta classe — quem chama lê a configuração vigente da sua fonte e a
 * informa. Sem posição da leitura ou do imóvel, não há desvio a medir:
 * o resultado é {@link Outcome#NO_POSITION}, também sem bloqueio e sem
 * alerta inventado.
 *
 * <p>Distância por haversine sobre coordenadas em graus decimais, com o
 * raio médio da Terra de 6.371.000 m.
 */
public final class PositionDeviationRule {

    private PositionDeviationRule() {
    }

    private static final double EARTH_RADIUS_METRES = 6_371_000.0;

    /** Uma posição geográfica (graus decimais). */
    public record GeoPoint(double latitude, double longitude) {
        public GeoPoint {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "coordenada não finita (NaN ou infinita) não é posição");
            }
            if (latitude < -90.0 || latitude > 90.0) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "latitude fora de -90..90: " + latitude);
            }
            if (longitude < -180.0 || longitude > 180.0) {
                throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                        "longitude fora de -180..180: " + longitude);
            }
        }
    }

    public enum Outcome {
        /** Desvio dentro do limiar: sem alerta. */
        WITHIN_THRESHOLD,
        /** Desvio acima do limiar: alerta gerado, leitura aceita. */
        ALERT_DEVIATION,
        /** Sem as duas posições: nada a medir, leitura aceita sem alerta. */
        NO_POSITION
    }

    /**
     * @param alertGenerated alerta de desvio gerado
     * @param blocked        sempre {@code false}: esta regra nunca bloqueia a leitura
     * @param distanceMetres distância medida; {@code null} quando não há as duas posições
     */
    public record Result(Outcome outcome, boolean alertGenerated, boolean blocked, Double distanceMetres) {
    }

    /**
     * @param readingPosition posição capturada pelo app (ED-18); nula se o app não capturou
     * @param propertyPosition posição cadastrada do imóvel; nula se o imóvel não tem posição
     * @param thresholdMetres limiar de desvio em metros (> 0), informado por quem chama
     */
    public static Result evaluate(GeoPoint readingPosition, GeoPoint propertyPosition,
                                  double thresholdMetres) {
        if (!(thresholdMetres > 0)) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "limiar de desvio de posição precisa ser > 0 metro; recebido " + thresholdMetres);
        }
        if (readingPosition == null || propertyPosition == null) {
            return new Result(Outcome.NO_POSITION, false, false, null);
        }
        double distance = distanceMetres(readingPosition, propertyPosition);
        boolean alert = distance > thresholdMetres;
        return new Result(alert ? Outcome.ALERT_DEVIATION : Outcome.WITHIN_THRESHOLD,
                alert, false, distance);
    }

    /** Distância haversine em metros entre dois pontos. */
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
