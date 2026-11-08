package br.com.ziipsan.intelligence.losses.anomaly;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Motor (ED-94): regras desligadas nao disparam; regra inaplicavel a serie nao dispara. */
class AnomalyDetectorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 0, 0);

    private static AnomalySeries hourlyOf(AnomalySeries.SeriesKind kind, String unit, double v, int n) {
        List<AnomalySeries.SeriesPoint> points = new ArrayList<>();
        for (int i = 0; i < n; i++) points.add(new AnomalySeries.SeriesPoint(T0.plusHours(i), v));
        return new AnomalySeries("REF-1", kind, unit, points);
    }

    @Test
    void disabledRuleDoesNotFire() {
        // 30h de consumo continuo, mas CONTINUOUS_CONSUMPTION fora de anomaly.rules_enabled
        AnomalySeries series = hourlyOf(AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION, "L", 5, 30);
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(series,
                AnomalyParametersTest.config("ZERO_CONSUMPTION", AnomalyParametersTest.FULL_SENSITIVITY));
        assertTrue(alerts.isEmpty());
    }

    @Test
    void pressureRuleDoesNotApplyToConsumptionSeries() {
        // queda brusca de "consumo" nao e queda de pressao: regra ligada, serie errada
        List<AnomalySeries.SeriesPoint> points = List.of(
                new AnomalySeries.SeriesPoint(T0, 50), new AnomalySeries.SeriesPoint(T0.plusHours(1), 10));
        AnomalySeries series = new AnomalySeries("LIG-1",
                AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION, "L", points);
        assertTrue(AnomalyDetector.evaluate(series,
                AnomalyParametersTest.config("ALL", AnomalyParametersTest.FULL_SENSITIVITY)).isEmpty());
    }

    @Test
    void runRulesDoNotApplyToPressureSeries() {
        AnomalySeries series = hourlyOf(AnomalySeries.SeriesKind.PRESSURE_POINT, "mca", 5, 80);
        assertTrue(AnomalyDetector.evaluate(series,
                AnomalyParametersTest.config("ALL", AnomalyParametersTest.FULL_SENSITIVITY)).isEmpty());
    }

    @Test
    void everyAlertIsSelfContained() {
        AnomalySeries series = hourlyOf(AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION, "L", 5, 30);
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(series,
                AnomalyParametersTest.config("ALL", AnomalyParametersTest.FULL_SENSITIVITY));
        assertEquals(1, alerts.size());
        AnomalyAlert alert = alerts.get(0);
        assertTrue(alert.isSelfContained(), "alerta mostra serie (evidencia), regra e confianca");
        assertTrue(alert.parameterSnapshot().contains("anomaly")
                || alert.parameterSnapshot().contains("deviationK"));
        assertTrue(alert.explanation().contains("24"), "a explicacao nomeia o limiar usado");
    }

    @Test
    void emptySeriesProducesNoAlerts() {
        AnomalySeries series = new AnomalySeries("LIG-1",
                AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION, "L", List.of());
        assertTrue(AnomalyDetector.evaluate(series,
                AnomalyParametersTest.config("ALL", AnomalyParametersTest.FULL_SENSITIVITY)).isEmpty());
    }
}
