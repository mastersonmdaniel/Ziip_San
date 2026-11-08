package br.com.ziipsan.intelligence.gis.hydraulic;

import br.com.ziipsan.intelligence.gis.hydraulic.solver.NativeSolverRunner;
import br.com.ziipsan.intelligence.gis.hydraulic.solver.SolverPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * COD-34: teste em rede de resultado conhecido executando a biblioteca
 * REAL. Rede analítica dos fixtures: reservatório carga 60 m, tubo
 * L=1000 m / D=300 mm / C=140, junção cota 10 m, demanda 20 L/s.
 * Valor esperado pela fórmula de Hazen-Williams do manual do EPANET 2.2
 * (hf = 10,67 · L · Q^1,852 / (C^1,852 · d^4,87), SI):
 * hf ≈ 0,2841 m → carga na junção ≈ 59,7159 m → pressão ≈ 49,7159 m;
 * vazão no tubo = demanda = 20 L/s (continuidade); velocidade ≈ 0,283 m/s.
 * Sem binário configurado o teste é pulado (assumption) — no CI do dono
 * os binários são construídos pelo script da entrega e o teste roda.
 */
class NativeSolverKnownResultTest {

    private NativeSolverRunner runner() {
        return NativeSolverRunner.fromEnvironment();
    }

    @Test
    void epanetExecutaEResultadoBateComOAnalitico() {
        var runner = runner();
        assumeTrue(runner.epanetAvailable(), "runepanet nao configurado nesta maquina");
        String inp = EpanetInpWriter.write(HydraulicFixtures.analyticWater());
        SolverPort.SolverResult r = runner.runEpanet(inp);
        assertTrue(r.solverExecuted());
        assertNotNull(r.solverVersion());
        var j1 = r.nodes().get("J1");
        assertNotNull(j1, "relatorio sem resultado do no J1: " + r.reportExcerpt());
        assertEquals(49.7159, j1.pressureM(), 0.05, "pressao analitica esperada ~49,7159 m");
        assertEquals(59.7159, j1.headM(), 0.05);
        var p1 = r.links().get("P1");
        assertNotNull(p1);
        assertEquals(20.0, Math.abs(p1.flowLps()), 0.05);
        assertEquals(0.283, Math.abs(p1.velocityMps()), 0.02);
    }

    @Test
    void epanetSemBibliotecaFalhaDeclaradamente() {
        var sem = new NativeSolverRunner(null, null);
        var ex = org.junit.jupiter.api.Assertions.assertThrows(SolverPort.SolverUnavailableException.class,
                () -> sem.runEpanet("[TITLE]\nx\n[END]\n"));
        assertTrue(ex.getMessage().contains("nenhum resultado produzido"), ex.getMessage());
    }

    @Test
    void swmmExecutaRedeDeEsgotoSemErro() {
        var runner = runner();
        assumeTrue(runner.swmmAvailable(), "runswmm nao configurado nesta maquina");
        String inp = SwmmInpWriter.write(HydraulicFixtures.sampleSewer());
        SolverPort.SolverResult r = runner.runSwmm(inp);
        assertTrue(r.solverExecuted());
        assertNotNull(r.solverVersion());
    }

    @Test
    void binariosExistemQuandoConfigurados() {
        String e = System.getProperty("ziipsan.epanet.bin");
        if (e != null) assertTrue(Files.isExecutable(Path.of(e)), e);
        String s = System.getProperty("ziipsan.swmm.bin");
        if (s != null) assertTrue(Files.isExecutable(Path.of(s)), s);
    }
}
