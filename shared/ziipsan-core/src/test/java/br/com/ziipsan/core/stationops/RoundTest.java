package br.com.ziipsan.core.stationops;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ronda: checklist, conclusao, cobertura, alerta e sync offline. */
class RoundTest {

    private RoundRoute route() { return TestSupport.route(TestSupport.etaRapida().id()); }

    @Test
    void completionRequiresAllRequiredChecklistItems() {
        RoundExecution e = new RoundExecution(UUID.randomUUID(), TestSupport.TENANT, route(),
                TestSupport.T0.plusSeconds(3600), "dev-1");
        e.start("operador-1", TestSupport.T0);
        e.answer(new RoundExecution.ItemAnswer("NIVEL", RoundExecution.ItemResult.OK, null));
        assertThatThrownBy(() -> e.complete(TestSupport.T0.plusSeconds(600)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLORO");
        e.answer(new RoundExecution.ItemAnswer("CLORO", RoundExecution.ItemResult.OK, null));
        e.complete(TestSupport.T0.plusSeconds(900));
        assertThat(e.isCompleted()).isTrue();
    }

    @Test
    void notOkRequiresNoteAndUnknownItemIsRefused() {
        RoundExecution e = new RoundExecution(UUID.randomUUID(), TestSupport.TENANT, route(),
                TestSupport.T0.plusSeconds(3600), "dev-2");
        e.start("operador-1", TestSupport.T0);
        assertThatThrownBy(() -> e.answer(new RoundExecution.ItemAnswer(
                "NIVEL", RoundExecution.ItemResult.NOT_OK, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> e.answer(new RoundExecution.ItemAnswer(
                "INEXISTENTE", RoundExecution.ItemResult.OK, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void coverageCountsCompletedSlotsAndNamesMissedOnes() {
        StationUnit unit = TestSupport.etaRapida();
        RoundRoute route = TestSupport.route(unit.id());
        Instant start = TestSupport.T0; // 06:00Z
        // Periodo de 4 h, intervalo de 60 min -> 4 slots; rondas concluidas
        // nos slots 1, 2 e 4 (conclusao dentro do slot).
        List<RoundExecution> executions = List.of(
                TestSupport.completedExecution(route, "k1", start.plusSeconds(30 * 60)),
                TestSupport.completedExecution(route, "k2", start.plusSeconds(90 * 60)),
                TestSupport.completedExecution(route, "k4", start.plusSeconds(200 * 60)));
        RoundCoverage.Assessment a = RoundCoverage.assess(TestSupport.TENANT, unit.id(),
                start, start.plusSeconds(4 * 3600), 60, executions, start.plusSeconds(4 * 3600));
        assertThat(a.expected()).isEqualTo(4);
        assertThat(a.coveredCount()).isEqualTo(3);
        assertThat(a.coveragePercent()).isEqualTo(75.0);
        assertThat(a.missedSlots()).hasSize(1);
        assertThat(a.missedSlots().get(0).start()).isEqualTo(start.plusSeconds(2 * 3600));
    }

    @Test
    void missedSlotsDispatchOneAlertEach() {
        StationUnit unit = TestSupport.etaRapida();
        RoundCoverage.Assessment a = RoundCoverage.assess(TestSupport.TENANT, unit.id(),
                TestSupport.T0, TestSupport.T0.plusSeconds(2 * 3600), 60,
                List.of(), TestSupport.T0.plusSeconds(2 * 3600));
        List<String> alerts = new ArrayList<>();
        int sent = RoundCoverage.dispatchMissedAlerts(a,
                (tenant, unitId, slotStart, reason) -> alerts.add(reason));
        assertThat(sent).isEqualTo(2);
        assertThat(alerts).hasSize(2);
        assertThat(a.coveragePercent()).isEqualTo(0.0);
    }

    @Test
    void doubleDispatchEmitsEachMissedAlertOnce() {
        // Correcao ED-87/D3: despachar a mesma apuracao (ou uma
        // reapuração identica do mesmo periodo) nao reemite alerta.
        StationUnit unit = TestSupport.etaRapida();
        RoundCoverage.Assessment a = RoundCoverage.assess(TestSupport.TENANT, unit.id(),
                TestSupport.T0, TestSupport.T0.plusSeconds(2 * 3600), 60,
                List.of(), TestSupport.T0.plusSeconds(2 * 3600));
        List<String> alerts = new ArrayList<>();
        StationOpsPorts.RoundAlertPort port =
                (tenant, unitId, slotStart, reason) -> alerts.add(reason);
        assertThat(RoundCoverage.dispatchMissedAlerts(a, port)).isEqualTo(2);
        assertThat(RoundCoverage.dispatchMissedAlerts(a, port)).isEqualTo(0);
        assertThat(alerts).hasSize(2);
        // Reapuracao do MESMO periodo (outro objeto Assessment, mesmas
        // chaves de slot): continua sem reemitir.
        RoundCoverage.Assessment again = RoundCoverage.assess(TestSupport.TENANT, unit.id(),
                TestSupport.T0, TestSupport.T0.plusSeconds(2 * 3600), 60,
                List.of(), TestSupport.T0.plusSeconds(2 * 3600));
        assertThat(RoundCoverage.dispatchMissedAlerts(again, port)).isEqualTo(0);
        assertThat(alerts).hasSize(2);
    }

    @Test
    void offlineExecutionSyncsOnceAndResyncIsDuplicate() {
        Map<String, RoundExecution> byKey = new HashMap<>();
        RoundSyncService sync = new RoundSyncService(new RoundSyncService.RoundStorePort() {
            @Override public Optional<RoundExecution> findByClientKey(UUID t, String key) {
                return Optional.ofNullable(byKey.get(key));
            }
            @Override public void save(RoundExecution e) { byKey.put(e.clientExecutionKey(), e); }
        });
        // A execucao nasce, responde e conclui INTEIRA no aparelho (sem rede):
        // nenhum servico e chamado ate a sincronizacao.
        RoundExecution e = TestSupport.completedExecution(route(), "device-42-round-7", TestSupport.T0);
        RoundSyncService.SyncResult first = sync.sync(TestSupport.TENANT, e);
        assertThat(first.outcome()).isEqualTo(RoundSyncService.SyncOutcome.ACCEPTED);
        RoundSyncService.SyncResult again = sync.sync(TestSupport.TENANT, e);
        assertThat(again.outcome()).isEqualTo(RoundSyncService.SyncOutcome.DUPLICATE);
        assertThat(again.executionId()).isEqualTo(first.executionId());
        assertThat(byKey).hasSize(1);
    }

    @Test
    void unfinishedOfflineDraftDoesNotSync() {
        RoundSyncService sync = new RoundSyncService(new RoundSyncService.RoundStorePort() {
            @Override public Optional<RoundExecution> findByClientKey(UUID t, String key) {
                return Optional.empty();
            }
            @Override public void save(RoundExecution e) { }
        });
        RoundExecution draft = new RoundExecution(UUID.randomUUID(), TestSupport.TENANT, route(),
                TestSupport.T0.plusSeconds(3600), "device-42-round-8");
        assertThatThrownBy(() -> sync.sync(TestSupport.TENANT, draft))
                .isInstanceOf(IllegalStateException.class);
    }
}
