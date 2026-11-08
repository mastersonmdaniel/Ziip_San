package br.com.ziipsan.intelligence.losses.anomaly;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Series sinteticas horarias de anomalia conhecida (OPE-36) para as
 * regras de corrida: limiares do parametro de teste continuousHours=24
 * e zeroHours=72 (valores DE TESTE, nao de fabrica).
 */
class RunRulesTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 0, 0);

    private static AnomalySeries hourly(String ref, double... values) {
        List<AnomalySeries.SeriesPoint> points = new ArrayList<>();
        for (int i = 0; i < values.length; i++)
            points.add(new AnomalySeries.SeriesPoint(T0.plusHours(i), values[i]));
        return new AnomalySeries(ref, AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION, "L", points);
    }

    private static double[] repeat(double v, int n) {
        double[] a = new double[n];
        java.util.Arrays.fill(a, v);
        return a;
    }

    private static double[] concat(double[]... parts) {
        int n = 0;
        for (double[] p : parts) n += p.length;
        double[] a = new double[n];
        int i = 0;
        for (double[] p : parts) for (double v : p) a[i++] = v;
        return a;
    }

    private static AnomalyParameters.AnomalyConfig runsOnly() {
        return AnomalyParametersTest.config("CONTINUOUS_CONSUMPTION,ZERO_CONSUMPTION",
                AnomalyParametersTest.FULL_SENSITIVITY);
    }

    @Test
    void continuous30HoursFiresOnceAtRunEnd() {
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(hourly("LIG-1", repeat(5, 30)), runsOnly());
        assertEquals(1, alerts.size());
        AnomalyAlert alert = alerts.get(0);
        assertEquals(AnomalyRule.CONTINUOUS_CONSUMPTION, alert.rule());
        assertEquals(T0.plusHours(29), alert.detectedAt());
        assertEquals(30, alert.evidence().size());
        // confianca: 0,5 + 0,5 * (30-24)/24 = 0,625
        assertEquals(0.625, alert.confidence(), 1e-9);
    }

    @Test
    void continuousExactlyAtThresholdFiresWithHalfConfidence() {
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(hourly("LIG-1", repeat(5, 24)), runsOnly());
        assertEquals(1, alerts.size());
        assertEquals(0.5, alerts.get(0).confidence(), 1e-9);
    }

    @Test
    void continuous23HoursDoesNotFire() {
        assertTrue(AnomalyDetector.evaluate(hourly("LIG-1", repeat(5, 23)), runsOnly()).isEmpty());
    }

    @Test
    void gapBreaksTheRun() {
        // 12h com fluxo, UMA HORA FALTANDO na serie, 12h com fluxo:
        // nenhuma corrida contigua atinge 24h
        List<AnomalySeries.SeriesPoint> points = new ArrayList<>();
        for (int i = 0; i < 12; i++) points.add(new AnomalySeries.SeriesPoint(T0.plusHours(i), 5));
        for (int i = 13; i < 25; i++) points.add(new AnomalySeries.SeriesPoint(T0.plusHours(i), 5));
        AnomalySeries series = new AnomalySeries("LIG-1",
                AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION, "L", points);
        assertTrue(AnomalyDetector.evaluate(series, runsOnly()).isEmpty());
    }

    @Test
    void twoSeparatedRunsFireTwice() {
        double[] values = concat(repeat(5, 24), new double[]{0}, repeat(5, 24));
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(hourly("LIG-1", values), runsOnly());
        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().allMatch(a -> a.rule() == AnomalyRule.CONTINUOUS_CONSUMPTION));
        // fortalecimento (correcao da revisao cruzada): nao basta
        // CONTAR 2 — afirma-se QUAIS sao os 2 alertas: a primeira
        // corrida ocupa as horas 0..23 (detectada em T0+23h) e a
        // segunda as horas 25..48 (detectada em T0+48h), cada uma com
        // a corrida inteira (24 pontos) como evidencia e confianca de
        // limiar exato (24h = continuousHours -> 0,5)
        AnomalyAlert first = alerts.get(0);
        assertEquals(T0.plusHours(23), first.detectedAt());
        assertEquals(T0, first.windowStart());
        assertEquals(T0.plusHours(23), first.windowEnd());
        assertEquals(24, first.evidence().size());
        assertEquals(0.5, first.confidence(), 1e-9);
        AnomalyAlert second = alerts.get(1);
        assertEquals(T0.plusHours(48), second.detectedAt());
        assertEquals(T0.plusHours(25), second.windowStart());
        assertEquals(T0.plusHours(48), second.windowEnd());
        assertEquals(24, second.evidence().size());
        assertEquals(0.5, second.confidence(), 1e-9);
    }

    @Test
    void zero80HoursFiresOnce() {
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(hourly("LIG-2", repeat(0, 80)), runsOnly());
        assertEquals(1, alerts.size());
        assertEquals(AnomalyRule.ZERO_CONSUMPTION, alerts.get(0).rule());
        // confianca: 0,5 + 0,5 * (80-72)/72 = 0,5555...
        assertEquals(0.5 + 0.5 * (8.0 / 72.0), alerts.get(0).confidence(), 1e-9);
    }

    @Test
    void zero71HoursDoesNotFireAndZeroExactly72Fires() {
        assertTrue(AnomalyDetector.evaluate(hourly("LIG-2", repeat(0, 71)), runsOnly()).isEmpty());
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(hourly("LIG-2", repeat(0, 72)), runsOnly());
        assertEquals(1, alerts.size());
        assertEquals(0.5, alerts.get(0).confidence(), 1e-9);
    }
}
