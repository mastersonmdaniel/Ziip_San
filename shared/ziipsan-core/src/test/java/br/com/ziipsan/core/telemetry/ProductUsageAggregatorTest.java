package br.com.ziipsan.core.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4.8 / PLT-42: a agregação é por tenant e por papel — adoção, tempo até
 * o primeiro ciclo, abandono de assistentes e tarefas mais repetidas.
 */
class ProductUsageAggregatorTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant T = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    @DisplayName("P4.8: agrega por (tenant, papel): adoção, primeiro ciclo, abandono e tarefas repetidas")
    void aggregatesByTenantAndRole() {
        List<TelemetryEvent> events = List.of(
                TelemetryEvent.featureUsed(TENANT_A, "OPERATOR", "billing.cycle", T),
                TelemetryEvent.featureUsed(TENANT_A, "OPERATOR", "billing.cycle", T),
                TelemetryEvent.featureUsed(TENANT_A, "OPERATOR", "meter.reading", T),
                TelemetryEvent.featureUsed(TENANT_A, "MANAGER", "billing.cycle", T),
                TelemetryEvent.firstCycleCompleted(TENANT_A, "OPERATOR", 3_600_000L, T),
                TelemetryEvent.firstCycleCompleted(TENANT_A, "OPERATOR", 7_200_000L, T),
                TelemetryEvent.wizard(TENANT_A, "OPERATOR", TelemetryEvent.Kind.WIZARD_STARTED, "onboarding", T),
                TelemetryEvent.wizard(TENANT_A, "OPERATOR", TelemetryEvent.Kind.WIZARD_STARTED, "onboarding", T),
                TelemetryEvent.wizard(TENANT_A, "OPERATOR", TelemetryEvent.Kind.WIZARD_COMPLETED, "onboarding", T),
                TelemetryEvent.wizard(TENANT_A, "OPERATOR", TelemetryEvent.Kind.WIZARD_ABANDONED, "onboarding", T),
                TelemetryEvent.taskExecuted(TENANT_A, "OPERATOR", "bill.issue", T),
                TelemetryEvent.taskExecuted(TENANT_A, "OPERATOR", "bill.issue", T),
                TelemetryEvent.taskExecuted(TENANT_A, "OPERATOR", "bill.issue", T),
                TelemetryEvent.taskExecuted(TENANT_A, "OPERATOR", "reading.import", T),
                TelemetryEvent.featureUsed(TENANT_B, "OPERATOR", "billing.cycle", T));

        List<ProductUsageAggregator.TenantUsageSummary> summaries =
                ProductUsageAggregator.summaries(events);
        assertThat(summaries).hasSize(3);

        ProductUsageAggregator.TenantUsageSummary aOp = summaries.stream()
                .filter(s -> s.tenantId().equals(TENANT_A) && s.role().equals("OPERATOR"))
                .findFirst().orElseThrow();
        assertThat(aOp.features().get(0).featureKey()).isEqualTo("billing.cycle");
        assertThat(aOp.features().get(0).events()).isEqualTo(2);
        assertThat(aOp.avgFirstCycleMillis()).isEqualTo(5_400_000.0);
        assertThat(aOp.wizards()).hasSize(1);
        assertThat(aOp.wizards().get(0).started()).isEqualTo(2);
        assertThat(aOp.wizards().get(0).abandoned()).isEqualTo(1);
        assertThat(aOp.wizards().get(0).abandonmentRate()).isEqualTo(0.5);
        assertThat(aOp.topTasks().get(0).taskKey()).isEqualTo("bill.issue");
        assertThat(aOp.topTasks().get(0).executions()).isEqualTo(3);
    }

    @Test
    @DisplayName("P4.8: priorização do changelog usa o agregado global, sem tenant nem papel na saída")
    void changelogPrioritiesAreGlobal() {
        List<TelemetryEvent> events = List.of(
                TelemetryEvent.featureUsed(TENANT_A, "OPERATOR", "billing.cycle", T),
                TelemetryEvent.featureUsed(TENANT_B, "MANAGER", "billing.cycle", T),
                TelemetryEvent.featureUsed(TENANT_B, "MANAGER", "gis.map", T));
        List<ProductUsageAggregator.ChangelogPriority> priorities =
                ProductUsageAggregator.changelogPriorities(events);
        assertThat(priorities.get(0).key()).isEqualTo("billing.cycle");
        assertThat(priorities.get(0).events()).isEqualTo(2);
        String json = ProductUsageAggregator.toJson(ProductUsageAggregator.summaries(events));
        assertThat(json).contains("billing.cycle").doesNotContain("userId").doesNotContain("session");
    }
}
