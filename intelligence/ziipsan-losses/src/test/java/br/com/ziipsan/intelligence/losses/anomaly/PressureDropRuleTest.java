package br.com.ziipsan.intelligence.losses.anomaly;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serie sintetica de pressao de anomalia conhecida (OPE-36):
 * limiar de teste pressureDropPercent=25 (valor DE TESTE).
 */
class PressureDropRuleTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 0, 0);

    private static AnomalySeries pressure(double... values) {
        java.util.List<AnomalySeries.SeriesPoint> points = new java.util.ArrayList<>();
        for (int i = 0; i < values.length; i++)
            points.add(new AnomalySeries.SeriesPoint(T0.plusHours(i), values[i]));
        return new AnomalySeries("PONTO-P7", AnomalySeries.SeriesKind.PRESSURE_POINT, "mca", points);
    }

    private static AnomalyParameters.AnomalyConfig dropOnly() {
        return AnomalyParametersTest.config("SUDDEN_PRESSURE_DROP",
                AnomalyParametersTest.FULL_SENSITIVITY);
    }

    @Test
    void knownDropFiresWithHandComputedConfidence() {
        // 50 -> 30: queda de 40% >= 25% -> dispara na segunda amostra do par
        // confianca: 0,5 + 0,5 * (0,40-0,25)/0,25 = 0,8
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(pressure(50, 50, 30), dropOnly());
        assertEquals(1, alerts.size());
        AnomalyAlert alert = alerts.get(0);
        assertEquals(AnomalyRule.SUDDEN_PRESSURE_DROP, alert.rule());
        assertEquals(T0.plusHours(2), alert.detectedAt());
        assertEquals(30.0, alert.observedValue());
        assertEquals(0.8, alert.confidence(), 1e-9);
        assertEquals(2, alert.evidence().size(), "evidencia e o par de amostras");
    }

    @Test
    void dropExactlyAtThresholdFires() {
        // 40 -> 30: queda de exatamente 25%
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(pressure(40, 30), dropOnly());
        assertEquals(1, alerts.size());
        assertEquals(0.5, alerts.get(0).confidence(), 1e-9);
    }

    @Test
    void smallDropDoesNotFire() {
        // 50 -> 45: queda de 10% < 25%
        assertTrue(AnomalyDetector.evaluate(pressure(50, 45), dropOnly()).isEmpty());
    }

    @Test
    void riseDoesNotFire() {
        assertTrue(AnomalyDetector.evaluate(pressure(30, 50), dropOnly()).isEmpty());
    }

    @Test
    void spacedDropIsNotSuddenAndDoesNotFire() {
        // correcao R2 da revisao cruzada (sonda S5 do revisor): a mesma
        // queda de 40% (50 -> 30) com amostras a 7 DIAS de distancia
        // nao e "subita" — sem adjacencia temporal (passo de 1h), sem
        // alerta; idem com buraco de 2h entre as amostras
        AnomalySeries weekApart = new AnomalySeries("PONTO-P7",
                AnomalySeries.SeriesKind.PRESSURE_POINT, "mca",
                List.of(new AnomalySeries.SeriesPoint(T0, 50),
                        new AnomalySeries.SeriesPoint(T0.plusDays(7), 30)));
        assertTrue(AnomalyDetector.evaluate(weekApart, dropOnly()).isEmpty(),
                "queda entre amostras a 7 dias nao e queda subita");
        AnomalySeries twoHourGap = new AnomalySeries("PONTO-P7",
                AnomalySeries.SeriesKind.PRESSURE_POINT, "mca",
                List.of(new AnomalySeries.SeriesPoint(T0, 50),
                        new AnomalySeries.SeriesPoint(T0.plusHours(2), 30)));
        assertTrue(AnomalyDetector.evaluate(twoHourGap, dropOnly()).isEmpty(),
                "queda com buraco de 2h nao e queda subita");
    }

    @Test
    void dropFromZeroIsNotDefinedAndDoesNotFire() {
        // anterior zero nao define queda relativa: par ignorado, sem divisao por zero disfarcada
        assertTrue(AnomalyDetector.evaluate(pressure(0, 0, 10), dropOnly()).isEmpty());
    }
}
