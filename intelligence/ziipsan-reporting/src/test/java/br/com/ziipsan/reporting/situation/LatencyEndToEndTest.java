package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.core.incident.NetworkIncident;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ED-93: teste de latencia de ponta a ponta NO NUCLEO (aceite; RNF
 * ADR-0012): fato na porta -> projecao -> tabela de estado -> leitura
 * do console, cronometrado com relogio real. O teto de 3 minutos e
 * massa sintetica declarada das fixtures — o produto nao tem teto de
 * fabrica ([SEM FONTE]). A latencia ate o navegador (rede/banco/HTTP)
 * e da integracao: pendencia P-ED93-03, nao afirmada aqui.
 */
class LatencyEndToEndTest {

    @Test
    void factToConsolePathCompletesWithinTheConfiguredBudget() {
        NetworkIncident fresh = SituationFixtures.incident("OC-LAT", NetworkIncident.Type.INTERRUPTION,
                SituationFixtures.NOW_LOCAL.minusSeconds(5));
        SituationProjector projector = SituationFixtures.projector(
                t -> List.of(fresh), t -> List.of(), t -> List.of(),
                t -> List.of(), t -> List.of(), t -> List.of());
        InMemorySituationStateStore store = new InMemorySituationStateStore();
        SituationConsoleService console = new SituationConsoleService(store);

        SituationLatencyProbe.Measurement measurement = SituationLatencyProbe.measureEndToEnd(
                projector, store, console, SituationFixtures.TENANT,
                SituationFixtures.POLICY_3MIN, SituationFixtures.NOW);

        assertEquals(1, measurement.projectedRows());
        assertTrue(measurement.withinBudget(),
                "caminho fato->console estourou o teto configurado: " + measurement.elapsed());
        assertEquals(SituationLatencyPolicy.Freshness.FRESH, measurement.oldestFreshness());
    }

    @Test
    void oldFactIsReportedStaleNotFresh() {
        NetworkIncident old = SituationFixtures.incident("OC-OLD", NetworkIncident.Type.RUPTURE,
                SituationFixtures.NOW_LOCAL.minusMinutes(45));
        SituationProjector projector = SituationFixtures.projector(
                t -> List.of(old), t -> List.of(), t -> List.of(),
                t -> List.of(), t -> List.of(), t -> List.of());
        InMemorySituationStateStore store = new InMemorySituationStateStore();
        SituationConsoleService console = new SituationConsoleService(store);

        SituationLatencyProbe.Measurement measurement = SituationLatencyProbe.measureEndToEnd(
                projector, store, console, SituationFixtures.TENANT,
                SituationFixtures.POLICY_3MIN, SituationFixtures.NOW);

        assertEquals(SituationLatencyPolicy.Freshness.STALE, measurement.oldestFreshness());
        SituationSnapshot snapshot = console.snapshot(SituationFixtures.TENANT,
                SituationSourceGuard.FeedSource.NATIVE_SUMMARY_TABLE,
                SituationFixtures.POLICY_3MIN, SituationFixtures.NOW);
        assertEquals(SituationLatencyPolicy.Freshness.STALE,
                snapshot.freshnessByKind().get(SituationItemKind.INCIDENT));
    }

    @Test
    void pipelineScalesWithinBudgetForSyntheticLoad() {
        // Massa sintetica declarada: 600 linhas projetadas e lidas no caminho.
        List<SituationFeeds.CrewPosition> crews = new java.util.ArrayList<>();
        for (int i = 0; i < 600; i++) {
            crews.add(new SituationFeeds.CrewPosition("EQ-" + i, null, -27.0, -48.7,
                    SituationFixtures.NOW.minusSeconds(20)));
        }
        SituationProjector projector = SituationFixtures.projector(
                t -> List.of(), t -> List.of(), t -> crews, t -> List.of(), t -> List.of(), t -> List.of());
        InMemorySituationStateStore store = new InMemorySituationStateStore();
        SituationConsoleService console = new SituationConsoleService(store);

        SituationLatencyProbe.Measurement measurement = SituationLatencyProbe.measureEndToEnd(
                projector, store, console, SituationFixtures.TENANT,
                new SituationLatencyPolicy(Duration.ofMinutes(3)), SituationFixtures.NOW);

        assertEquals(600, measurement.projectedRows());
        assertTrue(measurement.withinBudget());
        assertFalse(measurement.elapsed().isNegative());
    }
}
