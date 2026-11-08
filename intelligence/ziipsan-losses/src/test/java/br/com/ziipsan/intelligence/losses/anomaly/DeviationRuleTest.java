package br.com.ziipsan.intelligence.losses.anomaly;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serie sintetica de anomalia conhecida (OPE-36): segundas-feiras as
 * 03:00 com base estavel conhecida; a anomalia plantada e o salto da
 * ultima segunda. Mediana/MAD/sigma/z esperados sao calculados a mao
 * nos comentarios e fixados nas assercoes — nenhuma afrouxada.
 */
class DeviationRuleTest {

    private static final LocalDateTime FIRST_MONDAY = LocalDateTime.of(2026, 1, 5, 3, 0); // segunda

    private static AnomalySeries weeklySeries(String ref, AnomalySeries.SeriesKind kind,
                                              double... mondayValues) {
        List<AnomalySeries.SeriesPoint> points = new ArrayList<>();
        for (int i = 0; i < mondayValues.length; i++)
            points.add(new AnomalySeries.SeriesPoint(FIRST_MONDAY.plusWeeks(i), mondayValues[i]));
        return new AnomalySeries(ref, kind, "L/h", points);
    }

    private static AnomalyParameters.AnomalyConfig onlyDeviation() {
        return AnomalyParametersTest.config("SAME_HOUR_WEEKDAY_DEVIATION",
                AnomalyParametersTest.FULL_SENSITIVITY);
    }

    @Test
    void knownSpikeFiresWithHandComputedStatistics() {
        // base [98,100,102,100]: mediana 100; desvios [2,0,2,0] -> MAD = 1; sigma = 1,4826
        // alvo 110: distancia 10 -> z = 10/1,4826 = 6,7449 >= 3 -> dispara
        // confianca: cobertura min(1, 4/(2*4)) = 0,5; forca 0,5+0,5*min(1,(z-3)/3) = 1,0 -> 0,5
        AnomalySeries series = weeklySeries("DMC-NORTE", AnomalySeries.SeriesKind.NIGHT_FLOW_DMC,
                98, 100, 102, 100, 110);
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(series, onlyDeviation());
        assertEquals(1, alerts.size());
        AnomalyAlert alert = alerts.get(0);
        assertEquals(AnomalyRule.SAME_HOUR_WEEKDAY_DEVIATION, alert.rule());
        assertEquals(110.0, alert.observedValue());
        assertEquals(100.0, alert.baselineMedian());
        assertEquals(1.4826, alert.baselineSigma(), 1e-9);
        assertEquals(10.0 / 1.4826, alert.deviationSigma(), 1e-9);
        assertEquals(0.5, alert.confidence(), 1e-9);
        assertEquals(AnomalyAlert.Status.PROPOSED, alert.status());
        assertEquals(5, alert.evidence().size(), "evidencia = base (4) + ponto anomalo");
        assertTrue(alert.explanation().contains("mediana"), alert.explanation());
    }

    @Test
    void fullCoverageDoublesConfidence() {
        // base de 8 segundas [98,100,102,100,98,100,102,100]: mediana 100, MAD 1, sigma 1,4826
        // alvo 110 -> mesmo z; cobertura min(1, 8/8) = 1 -> confianca 1,0
        AnomalySeries series = weeklySeries("DMC-NORTE", AnomalySeries.SeriesKind.NIGHT_FLOW_DMC,
                98, 100, 102, 100, 98, 100, 102, 100, 110);
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(series, onlyDeviation());
        assertEquals(1, alerts.size());
        assertEquals(1.0, alerts.get(0).confidence(), 1e-9);
    }

    @Test
    void normalValueDoesNotFire() {
        // alvo 101: distancia 1 -> z = 0,6745 < 3
        AnomalySeries series = weeklySeries("DMC-NORTE", AnomalySeries.SeriesKind.NIGHT_FLOW_DMC,
                98, 100, 102, 100, 101);
        assertTrue(AnomalyDetector.evaluate(series, onlyDeviation()).isEmpty());
    }

    @Test
    void insufficientBaselineNeverFires() {
        // so 3 amostras de base (< minBaselineSamples=4): nem o salto de 10x dispara
        AnomalySeries series = weeklySeries("DMC-NORTE", AnomalySeries.SeriesKind.NIGHT_FLOW_DMC,
                100, 100, 100, 1000);
        assertTrue(AnomalyDetector.evaluate(series, onlyDeviation()).isEmpty());
    }

    @Test
    void zeroMadBaselineFiresOnAnyDifference() {
        // base perfeitamente estavel [100,100,100,100]: MAD 0 -> sigma 0;
        // 105 difere da mediana -> dispara com desvio reportado como ausente
        AnomalySeries series = weeklySeries("LIG-42", AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION,
                100, 100, 100, 100, 105);
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(series, onlyDeviation());
        assertEquals(1, alerts.size());
        assertEquals(0.0, alerts.get(0).baselineSigma());
        assertNull(alerts.get(0).deviationSigma(), "sigma zero: o desvio nao se disfarca em numero");
        assertEquals(0.5, alerts.get(0).confidence(), 1e-9);
        // e o valor igual a mediana, com a mesma base, nao dispara
        AnomalySeries flat = weeklySeries("LIG-42", AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION,
                100, 100, 100, 100, 100);
        assertTrue(AnomalyDetector.evaluate(flat, onlyDeviation()).isEmpty());
    }

    @Test
    void targetAtHalfHourUsesSameHourBaseline() {
        // correcao R4 da revisao cruzada (sonda S6 do revisor): o slot
        // e a HORA do dia — alvo as 03:30 conversa com a base das
        // segundas as 03:00 ([98,100,102,100], mediana 100, sigma
        // 1,4826); o salto para 110 dispara como no caso das 03:00
        List<AnomalySeries.SeriesPoint> points = new ArrayList<>();
        double[] base = {98, 100, 102, 100};
        for (int i = 0; i < base.length; i++)
            points.add(new AnomalySeries.SeriesPoint(FIRST_MONDAY.plusWeeks(i), base[i]));
        points.add(new AnomalySeries.SeriesPoint(FIRST_MONDAY.plusWeeks(4).plusMinutes(30), 110));
        AnomalySeries series = new AnomalySeries("DMC-NORTE",
                AnomalySeries.SeriesKind.NIGHT_FLOW_DMC, "L/h", points);
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(series, onlyDeviation());
        assertEquals(1, alerts.size());
        assertEquals(110.0, alerts.get(0).observedValue());
        assertEquals(100.0, alerts.get(0).baselineMedian());
        assertEquals(FIRST_MONDAY.plusWeeks(4).plusMinutes(30), alerts.get(0).detectedAt());
    }

    @Test
    void baselineBeyond200SamplesIsNotTruncated() {
        // sem teto nem paginacao na base: 250 segundas de historico
        // (acima de qualquer pagina de 200) entram TODAS na base do
        // alvo — a evidencia carrega base (250) + ponto anomalo (1) e
        // a cobertura cheia leva a confianca a 1,0
        double[] pattern = {98, 100, 102, 100};
        List<AnomalySeries.SeriesPoint> points = new ArrayList<>();
        for (int i = 0; i < 250; i++)
            points.add(new AnomalySeries.SeriesPoint(
                    FIRST_MONDAY.plusWeeks(i), pattern[i % pattern.length]));
        points.add(new AnomalySeries.SeriesPoint(FIRST_MONDAY.plusWeeks(250), 110));
        AnomalySeries series = new AnomalySeries("DMC-NORTE",
                AnomalySeries.SeriesKind.NIGHT_FLOW_DMC, "L/h", points);
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(series, onlyDeviation());
        assertEquals(1, alerts.size(), "so o salto plantado dispara");
        assertEquals(251, alerts.get(0).evidence().size(),
                "evidencia = base completa (250) + ponto anomalo, sem truncamento");
        assertEquals(1.0, alerts.get(0).confidence(), 1e-9);
    }

    @Test
    void differentWeekdayIsNotBaseline() {
        // terças com valores altos NAO entram na base das segundas:
        // base das segundas segue [98,100,102,100]; a segunda normal (101) nao dispara
        List<AnomalySeries.SeriesPoint> points = new ArrayList<>();
        double[] mondays = {98, 100, 102, 100, 101};
        for (int i = 0; i < mondays.length; i++) {
            points.add(new AnomalySeries.SeriesPoint(FIRST_MONDAY.plusWeeks(i), mondays[i]));
            points.add(new AnomalySeries.SeriesPoint(FIRST_MONDAY.plusWeeks(i).plusDays(1), 900));
        }
        AnomalySeries series = new AnomalySeries("DMC-NORTE",
                AnomalySeries.SeriesKind.NIGHT_FLOW_DMC, "L/h", points);
        assertTrue(AnomalyDetector.evaluate(series, onlyDeviation()).isEmpty(),
                "a base e so do mesmo dia da semana e da mesma hora");
    }
}
