package br.com.ziipsan.intelligence.gis.drain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static br.com.ziipsan.intelligence.gis.drain.GisDrainMaintenance.DueStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plano de limpeza (NR 12/ANA): sem intervalo do tenant, sem data —
 * e indicadores OPE-32 com denominador zero = desconhecido.
 */
class GisDrainMaintenanceTest {

    @Test
    void semIntervaloNaoHaVencimento() {
        var line = new GisDrainMaintenance.CleaningPlanLine("INLET", null, null, false);
        assertThat(GisDrainMaintenance.nextDue(line,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(GisDrainMaintenance.dueStatus(line,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31))).isEqualTo(DueStatus.NOT_SCHEDULED);
        assertThat(GisDrainMaintenance.planFromIntervalParameter(Map.of())).isEmpty();
        assertThat(GisDrainMaintenance.planFromIntervalParameter(null)).isEmpty();
    }

    @Test
    void calendarioComIntervaloDoTenant() {
        var line = new GisDrainMaintenance.CleaningPlanLine("INLET", 6, null, false);
        // Ultima limpeza 10/01/2026 + 6 meses = 10/07/2026.
        assertThat(GisDrainMaintenance.nextDue(line,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1)))
                .contains(LocalDate.of(2026, 7, 10));
        assertThat(GisDrainMaintenance.dueStatus(line,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 9))).isEqualTo(DueStatus.SCHEDULED);
        assertThat(GisDrainMaintenance.dueStatus(line,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 10))).isEqualTo(DueStatus.DUE);
        assertThat(GisDrainMaintenance.dueStatus(line,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 11))).isEqualTo(DueStatus.OVERDUE);
        // Sem ultima limpeza: base do plano + intervalo.
        assertThat(GisDrainMaintenance.nextDue(line, null, LocalDate.of(2026, 1, 1)))
                .contains(LocalDate.of(2026, 7, 1));
    }

    @Test
    void parametroComClasseForaDaListaEAcusado() {
        assertThatThrownBy(() -> GisDrainMaintenance.planFromIntervalParameter(
                Map.of("WATER_MAIN", 6)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(GisDrainMaintenance.planFromIntervalParameter(
                Map.of("INLET", 6, "STORM_PIPE", 12))).hasSize(2);
    }

    @Test
    void criterioAntecipaLimpeza() {
        var line = new GisDrainMaintenance.CleaningPlanLine("STORM_PIPE", null, 50.0, true);
        assertThat(GisDrainMaintenance.isDueByCriteria(line, 50.0, "GOOD")).isTrue();
        assertThat(GisDrainMaintenance.isDueByCriteria(line, 10.0, "CRITICAL")).isTrue();
        assertThat(GisDrainMaintenance.isDueByCriteria(line, 10.0, "GOOD")).isFalse();
        var semCriterio = new GisDrainMaintenance.CleaningPlanLine("INLET", null, null, false);
        assertThat(GisDrainMaintenance.isDueByCriteria(semCriterio, 99.0, "CRITICAL"))
                .isFalse();
    }

    @Test
    void indicadoresOpe32ComCasoConhecido() {
        var ind = GisDrainMaintenance.indicators(
                2, List.of(40.0, 20.0),
                10, 200, 50,
                8, 6,
                100, 25,
                List.of(10.0, 30.0));
        assertThat(ind.floodOccurrences()).isEqualTo(2);
        assertThat(ind.avgRainMmPerFloodPeriod()).isEqualTo(30.0);
        assertThat(ind.inspectedNetworkPct()).isEqualTo(25.0);
        assertThat(ind.cleaningCompletionPct()).isEqualTo(75.0);
        assertThat(ind.poorOrCriticalPct()).isEqualTo(25.0);
        assertThat(ind.avgObstructionPct()).isEqualTo(20.0);
    }

    @Test
    void indicadoresSemBaseSaoDesconhecidos() {
        var ind = GisDrainMaintenance.indicators(
                0, List.of(), 0, 0, 0, 0, 0, 0, 0, List.of());
        assertThat(ind.avgRainMmPerFloodPeriod()).isNull();
        assertThat(ind.inspectedNetworkPct()).isNull();
        assertThat(ind.cleaningCompletionPct()).isNull();
        assertThat(ind.poorOrCriticalPct()).isNull();
        assertThat(ind.avgObstructionPct()).isNull();
    }
}
