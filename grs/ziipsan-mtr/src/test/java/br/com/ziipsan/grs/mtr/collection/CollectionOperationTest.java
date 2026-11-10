package br.com.ziipsan.grs.mtr.collection;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E13, teste 7 (coleta).
import br.com.ziipsan.core.fieldos.Tracking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionOperationTest {

    private final CollectionOperation.RoutePlanningPort reverseRoutes =
            (sectorId, stops) -> stops.reversed();

    private CollectionOperation.CollectionPlan plan() {
        return CollectionOperation.plan(new CollectionOperation.Sector("SETOR-1", "Centro"),
                CollectionOperation.FlowKind.SELECTIVE, List.of("P1", "P2", "P3"),
                Map.of("SETOR-1", 3), reverseRoutes);
    }

    @Test
    @DisplayName("sector sem frequência em waste.collection_frequency_by_sector recusa o plano")
    void sectorWithoutFrequencyIsRefused() {
        assertThatThrownBy(() -> CollectionOperation.plan(new CollectionOperation.Sector("SETOR-9", "Longe"),
                CollectionOperation.FlowKind.REGULAR, List.of("P1"), Map.of("SETOR-1", 3), reverseRoutes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("waste.collection_frequency_by_sector");
        assertThat(CollectionOperation.KEY_FREQUENCY_BY_SECTOR).isEqualTo("waste.collection_frequency_by_sector");
        assertThat(CollectionOperation.KEY_WEIGHING_REQUIRED).isEqualTo("waste.weighing_required");
    }

    @Test
    @DisplayName("plano consome o roteiro da porta (ED-65) e guarda a ordem devolvida")
    void planConsumesRoutePort() {
        var plan = plan();
        assertThat(plan.frequencyDaysPerWeek()).isEqualTo(3);
        assertThat(plan.orderedStopIds()).containsExactly("P3", "P2", "P1");
        assertThat(plan.flow()).isEqualTo(CollectionOperation.FlowKind.SELECTIVE);
    }

    @Test
    @DisplayName("com waste.weighing_required activo, execução sem ticket não fecha; inactivo, fecha")
    void weighingGate() {
        var execution = new CollectionOperation.Execution(plan());
        assertThatThrownBy(() -> execution.close(true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("waste.weighing_required");
        execution.addTicket(new CollectionOperation.WeighingTicket("TB-001",
                Instant.parse("2026-10-05T12:00:00Z"), new BigDecimal("1250.5")));
        var summary = execution.close(true);
        assertThat(summary.ticketCount()).isEqualTo(1);
        assertThat(summary.totalWeighedKg()).isEqualByComparingTo("1250.5");

        var noWeighing = new CollectionOperation.Execution(plan());
        assertThat(noWeighing.close(false).totalWeighedKg()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("GPS da execução passa pelo portão da ED-20: sem aviso ao empregado, o ponto é recusado")
    void gpsPointsPassEd20Gate() {
        var execution = new CollectionOperation.Execution(plan());
        CollectionOperation.GpsTrackPort gpsPort = Tracking::ingest;
        var shift = new Tracking.Shift(Instant.parse("2026-10-05T08:00:00Z"),
                Instant.parse("2026-10-05T17:00:00Z"));
        var point = new Tracking.TrackPoint(UUID.randomUUID(), null,
                Instant.parse("2026-10-05T10:00:00Z"), -27.0, -48.6);
        assertThatThrownBy(() -> execution.addTrackPoint(gpsPort, point, shift, true, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("aviso");
        execution.addTrackPoint(gpsPort, point, shift, true, true);
        assertThat(execution.close(false).trackPointCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("indicadores: só aritmética declarada sobre a operação; fórmulas ANA ficam [SEM FONTE]")
    void indicatorsAreDeclaredArithmeticOnly() {
        var a = new CollectionOperation.ExecutionSummary("SETOR-1",
                CollectionOperation.FlowKind.REGULAR, 1, new BigDecimal("1000"), 5);
        var b = new CollectionOperation.ExecutionSummary("SETOR-1",
                CollectionOperation.FlowKind.REGULAR, 0, new BigDecimal("0"), 3);
        Map<String, Object> indicators = CollectionOperation.operationalIndicators(List.of(a, b));
        assertThat(indicators.get("executions")).isEqualTo(2);
        assertThat(indicators.get("weighedKgPerExecution")).isEqualTo("500.000");
        assertThat(indicators.get("weighingCoverage")).isEqualTo("0.5000");
        assertThat(String.valueOf(indicators.get("anaIndicators"))).contains("[SEM FONTE]").contains("P-ED104-07");
    }

    @Test
    @DisplayName("cooperativas e grandes geradores são registos declarados da operação")
    void partnersAndLargeGeneratorsAreDeclared() {
        var coop = new CollectionOperation.Partner(UUID.randomUUID(),
                CollectionOperation.PartnerKind.COOPERATIVE, "Cooperativa de Catadores do Vale");
        assertThat(coop.kind()).isEqualTo(CollectionOperation.PartnerKind.COOPERATIVE);
        var large = new CollectionOperation.LargeGenerator(UUID.randomUUID(), "Hospital Regional",
                CollectionOperation.FlowKind.RSS);
        assertThat(large.flow()).isEqualTo(CollectionOperation.FlowKind.RSS);
        assertThatThrownBy(() -> new CollectionOperation.WeighingTicket(" ", Instant.now(), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
