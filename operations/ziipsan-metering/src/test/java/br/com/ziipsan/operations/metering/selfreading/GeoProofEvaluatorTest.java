package br.com.ziipsan.operations.metering.selfreading;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.3 da estimativa: distancia com haversine calculado
 * INDEPENDENTEMENTE (forma fechada sobre o raio declarado de
 * 6.371.000 m) e filtro alem do limiar sinalizado.
 */
class GeoProofEvaluatorTest {

    private final SelfReadingScoreConfig config = SelfReadingScoreConfig.proofDefaults();
    private final Instant now = Instant.parse("2026-11-10T12:00:00Z");

    // Forma fechada independente: no equador, 1 grau = R * pi / 180.
    private static final double ONE_DEGREE_METRES = 111194.92664455873;

    private GeoProof proof(double lat, double lon, double accuracy) {
        return new GeoProof(new GeoPoint(lat, lon), accuracy, now);
    }

    @Test
    void mesmoPontoDistanciaZeroEDentro() {
        GeoProofResult r = GeoProofEvaluator.evaluate(
                proof(-26.99, -48.63, 10), new GeoPoint(-26.99, -48.63), config);
        assertTrue(r.present());
        assertEquals(0.0, r.distanceMetres(), 0.000001);
        assertTrue(r.withinMaxDistance());
        assertTrue(r.accuracySufficient());
    }

    @Test
    void umGrauNoEquadorBateComFormaFechada() {
        double byLon = GeoProofEvaluator.distanceMetres(new GeoPoint(0, 0), new GeoPoint(0, 1));
        double byLat = GeoProofEvaluator.distanceMetres(new GeoPoint(0, 0), new GeoPoint(1, 0));
        assertEquals(ONE_DEGREE_METRES, byLon, 0.5);
        assertEquals(ONE_DEGREE_METRES, byLat, 0.5);
    }

    @Test
    void fronteiraDoFiltroInclusiva() {
        // Distancia EXACTAMENTE igual ao maximo esta DENTRO. O caso e o
        // antipodal (0,0)-(0,180): h = 1 exactamente e a distancia e
        // 2*R*asin(1) = 20015086.79602057 em double -- representavel ao
        // ulp, ao contrario de 1 grau (forma fechada 111194.92664455873
        // vs haversine em double 111194.92664455874, 1 ulp acima, o que
        // tornaria a fronteira uma questao de arredondamento, nao da regra).
        SelfReadingScoreConfig exact = new SelfReadingScoreConfig(
                new java.math.BigDecimal("0.40"), new java.math.BigDecimal("0.30"),
                new java.math.BigDecimal("0.15"), new java.math.BigDecimal("0.15"),
                new java.math.BigDecimal("70"), 20015086.79602057, 50.0);
        GeoProofResult r = GeoProofEvaluator.evaluate(proof(0, 0, 10), new GeoPoint(0, 180), exact);
        assertEquals(20015086.79602057, r.distanceMetres(), 0.0);
        assertTrue(r.withinMaxDistance());
        // precisao exactamente no maximo tambem e suficiente (inclusiva)
        GeoProofResult acc = GeoProofEvaluator.evaluate(
                proof(0, 0, 50.0), new GeoPoint(0, 0), config);
        assertTrue(acc.accuracySufficient());
    }

    @Test
    void alemDoLimiarFicaForaDoFiltro() {
        // 0.002 graus de longitude no equador = 222.389... m > 100 m de maximo
        GeoProofResult r = GeoProofEvaluator.evaluate(
                proof(0, 0, 10), new GeoPoint(0, 0.002), config);
        assertTrue(r.present());
        assertEquals(222.38985328911746, r.distanceMetres(), 0.5);
        assertFalse(r.withinMaxDistance());
    }

    @Test
    void precisaoAlemDoMaximoInsuficiente() {
        GeoProofResult r = GeoProofEvaluator.evaluate(
                proof(0, 0, 80), new GeoPoint(0, 0), config);
        assertFalse(r.accuracySufficient());
        assertTrue(r.withinMaxDistance());
    }

    @Test
    void semProvaOuSemLigacaoResultadoAusente() {
        GeoProofResult a = GeoProofEvaluator.evaluate(null, new GeoPoint(0, 0), config);
        GeoProofResult b = GeoProofEvaluator.evaluate(proof(0, 0, 10), null, config);
        assertFalse(a.present());
        assertFalse(b.present());
        assertNull(a.distanceMetres());
        assertFalse(a.withinMaxDistance());
    }

    @Test
    void coordenadasInvalidasRecusadas() {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, 181));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new GeoProof(new GeoPoint(0, 0), -1, now));
    }

    @Test
    void determinismoDaDistancia() {
        double a = GeoProofEvaluator.distanceMetres(new GeoPoint(-26.99, -48.63),
                new GeoPoint(-26.98, -48.62));
        double b = GeoProofEvaluator.distanceMetres(new GeoPoint(-26.99, -48.63),
                new GeoPoint(-26.98, -48.62));
        assertEquals(a, b, 0.0);
    }
}
