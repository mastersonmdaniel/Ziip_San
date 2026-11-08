package br.com.ziipsan.intelligence.gis.hydraulic;

import br.com.ziipsan.intelligence.gis.hydraulic.solver.SolverPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationScenariosTwinTest {

    private static SolverPort.SolverResult fakeResult(double pressure) {
        return new SolverPort.SolverResult(SolverPort.SolverKind.EPANET, true, "2.2.0",
                Map.of("J1", new SolverPort.NodeValue(20, 59.7, pressure)),
                Map.of("P1", new SolverPort.LinkValue(20, 0.28, 0.3)), Map.of(), List.of(), null, "");
    }

    @Test
    void calibracaoSemTelemetriaEhRecusada() {
        assertThrows(IllegalArgumentException.class, () -> HydraulicCalibration.calibrate(
                dm -> fakeResult(49.7), List.of(), HydraulicCalibration.CalibrationPolicy.declaredDefault()));
    }

    @Test
    void calibracaoEncontraMultiplicadorQueZeraErro() {
        // solver "verdadeiro" com demanda efetiva 1,2x: pressão cai 2 m por +0,1 de multiplicador a partir de 51,7 em 1,0
        java.util.function.DoubleFunction<SolverPort.SolverResult> rerun =
                dm -> fakeResult(51.7 - (dm - 1.0) * 20.0);
        var obs = List.of(new HydraulicCalibration.Observation("J1", HydraulicCalibration.SeriesKind.PRESSURE_M, 49.7));
        var out = HydraulicCalibration.calibrate(rerun, obs, HydraulicCalibration.CalibrationPolicy.declaredDefault());
        assertEquals(1.10, out.demandMultiplier(), 0.051);
        assertTrue(out.rmse() < 0.2, "rmse=" + out.rmse());
        assertEquals(1, out.pairedPoints());
    }

    @Test
    void portaoN7BarraSemConfiancaEPublicaComTudo() {
        var ok = new HydraulicPublication.PublishedResult(fakeResult(49.7), "EPANET", "2.2.0",
                List.of("premissa declarada"), HydraulicPublication.Confidence.MEDIUM);
        assertTrue(HydraulicPublication.gateProblems(ok).isEmpty());
        HydraulicPublication.publish(ok);
        var sem = new HydraulicPublication.PublishedResult(fakeResult(49.7), "EPANET", "2.2.0",
                List.of("premissa"), null);
        assertTrue(HydraulicPublication.gateProblems(sem).stream().anyMatch(p -> p.contains("confianca")));
        assertThrows(IllegalStateException.class, () -> HydraulicPublication.publish(sem));
    }

    @Test
    void cenariosTransformamSemTocarNaBase() {
        var base = HydraulicFixtures.sampleWater();
        var falta = HydraulicScenarios.apply(base, HydraulicScenarios.ScenarioKind.POWER_OUTAGE, List.of(), null);
        assertTrue(falta.snapshot().controls().stream().anyMatch(c -> c.contains("LINK B1 CLOSED")), falta.snapshot().controls().toString());
        assertTrue(base.controls().isEmpty());
        var romp = HydraulicScenarios.apply(base, HydraulicScenarios.ScenarioKind.PIPE_BREAK, List.of("P1"), null);
        String inp = EpanetInpWriter.write(romp.snapshot());
        assertTrue(inp.contains("P1\tR1\tJ1\t1000\t300\t140\t0\tClosed"), inp);
        var manobra = HydraulicScenarios.apply(base, HydraulicScenarios.ScenarioKind.MANEUVER_PLAN, List.of("V1", "P2"), null);
        assertEquals(2, manobra.snapshot().controls().size());
        assertThrows(IllegalArgumentException.class, () ->
                HydraulicScenarios.apply(base, HydraulicScenarios.ScenarioKind.OFF_PEAK_PUMPING, List.of(), null));
        var foraPonta = HydraulicScenarios.apply(base, HydraulicScenarios.ScenarioKind.OFF_PEAK_PUMPING, List.of(), "00:00-05:00");
        assertTrue(foraPonta.premises().get(0).contains("00:00-05:00"));
    }

    @Test
    void gemeoExportaDemandaPorZonaENoEConsumoAgregado() {
        var e = DigitalTwinExport.build(HydraulicFixtures.sampleWater());
        assertEquals(0, new java.math.BigDecimal("77760").compareTo(e.zoneBilledVolumeM3()));
        assertEquals(0, new java.math.BigDecimal("30").compareTo(e.zoneDemandLps().stripTrailingZeros()));
        assertEquals(0, new java.math.BigDecimal("20").compareTo(e.demandByNode().get("J1").demandLps()));
        String json = DigitalTwinExport.toJson(e);
        assertTrue(json.contains("\"zoneCode\":\"Z1\"") && json.contains("\"demandByNode\""), json);
        assertTrue(DigitalTwinExport.toCsv(e).contains("J1;20;51840"), DigitalTwinExport.toCsv(e));
    }
}
