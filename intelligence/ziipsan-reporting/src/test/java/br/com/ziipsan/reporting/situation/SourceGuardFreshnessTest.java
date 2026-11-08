package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-93: guarda de fonte (OPE-22) e politica de latencia (ADR-0012). */
class SourceGuardFreshnessTest {

    @Test
    void primaryFreeQueryAndSupersetAreRefusedByName() {
        for (SituationSourceGuard.FeedSource forbidden : List.of(
                SituationSourceGuard.FeedSource.PRIMARY_FREE_QUERY,
                SituationSourceGuard.FeedSource.SUPERSET)) {
            IndicatorValidationException ex = assertThrows(IndicatorValidationException.class,
                    () -> SituationSourceGuard.requireSummaryTable(forbidden));
            assertEquals("OPE22_CONSOLE_SOURCE_FORBIDDEN", ex.code());
        }
    }

    @Test
    void consoleSnapshotRefusesForbiddenSourcesToo() {
        SituationConsoleService console = new SituationConsoleService(new InMemorySituationStateStore());
        assertThrows(IndicatorValidationException.class, () -> console.snapshot(SituationFixtures.TENANT,
                SituationSourceGuard.FeedSource.SUPERSET, SituationFixtures.POLICY_3MIN, SituationFixtures.NOW));
    }

    @Test
    void latencyBudgetHasNoFactoryDefault() {
        IndicatorValidationException ex = assertThrows(IndicatorValidationException.class,
                () -> new SituationLatencyPolicy(null));
        assertEquals("SITUATION_LATENCY_BUDGET_REQUIRED", ex.code());
        assertTrue(ex.getMessage().contains("[SEM FONTE]"));
    }

    @Test
    void freshnessSeparatesFreshStaleAndNoData() {
        SituationLatencyPolicy policy = SituationFixtures.POLICY_3MIN;
        assertEquals(SituationLatencyPolicy.Freshness.FRESH,
                policy.freshness(SituationFixtures.NOW.minusSeconds(30), SituationFixtures.NOW));
        assertEquals(SituationLatencyPolicy.Freshness.STALE,
                policy.freshness(SituationFixtures.NOW.minus(Duration.ofMinutes(30)), SituationFixtures.NOW));
        assertEquals(SituationLatencyPolicy.Freshness.NO_DATA,
                policy.freshness(null, SituationFixtures.NOW));
    }

    @Test
    void mixedFreshAndStaleSetFreshnessFollowsTheOldestWorstCase() {
        // D2 (revisao ED-93-REV-1): conjunto misto — item novo (30s) +
        // item velho (45min) sob o mesmo teto de 3min. O frescor do
        // conjunto e o do item MAIS ANTIGO (pior caso, min): STALE.
        // Com o criterio antigo (max/mais novo) este conjunto saia FRESH
        // e o item velho sumia atras do novo. O controle (so o item
        // novo) continua FRESH — o criterio nao e "sempre STALE".
        SituationStateRow fresh = new SituationStateRow(java.util.UUID.randomUUID(),
                SituationFixtures.TENANT, SituationItemKind.INCIDENT, "OC-NEW", "RUPTURE",
                "OPEN", true, null, null, SituationFixtures.NOW.minusSeconds(30),
                SituationFixtures.NOW, java.util.Map.of());
        SituationStateRow old = new SituationStateRow(java.util.UUID.randomUUID(),
                SituationFixtures.TENANT, SituationItemKind.INCIDENT, "OC-OLD", "RUPTURE",
                "OPEN", true, null, null, SituationFixtures.NOW.minus(java.time.Duration.ofMinutes(45)),
                SituationFixtures.NOW, java.util.Map.of());

        InMemorySituationStateStore mixed = new InMemorySituationStateStore();
        mixed.replaceTenantSnapshot(SituationFixtures.TENANT, List.of(fresh, old));
        SituationSnapshot mixedSnapshot = new SituationConsoleService(mixed)
                .snapshot(SituationFixtures.TENANT, SituationSourceGuard.FeedSource.NATIVE_SUMMARY_TABLE,
                        SituationFixtures.POLICY_3MIN, SituationFixtures.NOW);
        assertEquals(SituationLatencyPolicy.Freshness.STALE,
                mixedSnapshot.freshnessByKind().get(SituationItemKind.INCIDENT));

        InMemorySituationStateStore freshOnly = new InMemorySituationStateStore();
        freshOnly.replaceTenantSnapshot(SituationFixtures.TENANT, List.of(fresh));
        SituationSnapshot freshSnapshot = new SituationConsoleService(freshOnly)
                .snapshot(SituationFixtures.TENANT, SituationSourceGuard.FeedSource.NATIVE_SUMMARY_TABLE,
                        SituationFixtures.POLICY_3MIN, SituationFixtures.NOW);
        assertEquals(SituationLatencyPolicy.Freshness.FRESH,
                freshSnapshot.freshnessByKind().get(SituationItemKind.INCIDENT));
    }

    @Test
    void emptyConsoleReportsNoDataPerKind() {
        SituationSnapshot snapshot = new SituationConsoleService(new InMemorySituationStateStore())
                .snapshot(SituationFixtures.TENANT, SituationSourceGuard.FeedSource.NATIVE_SUMMARY_TABLE,
                        SituationFixtures.POLICY_3MIN, SituationFixtures.NOW);
        assertTrue(snapshot.freshnessByKind().values().stream()
                .allMatch(f -> f == SituationLatencyPolicy.Freshness.NO_DATA));
        assertEquals(6, snapshot.freshnessByKind().size());
    }

    @Test
    void storeRefusesRowsOfAnotherTenant() {
        InMemorySituationStateStore store = new InMemorySituationStateStore();
        SituationStateRow foreign = new SituationStateRow(java.util.UUID.randomUUID(),
                SituationFixtures.OTHER_TENANT, SituationItemKind.CREW, "EQ-9", "Equipe EQ-9",
                "POSITIONED", false, null, null, SituationFixtures.NOW, SituationFixtures.NOW,
                java.util.Map.of());
        IndicatorValidationException ex = assertThrows(IndicatorValidationException.class,
                () -> store.replaceTenantSnapshot(SituationFixtures.TENANT, List.of(foreign)));
        assertEquals("SITUATION_ROW_TENANT_MISMATCH", ex.code());
    }
}
