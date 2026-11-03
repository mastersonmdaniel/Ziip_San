package br.com.ziipsan.platform.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.14: canário, quarentena e paralelismo limitado, sem banco — a migração é
 * injetada como função e a escrita no catálogo vai para um JdbcTemplate falso
 * (subclasse que só registra os argumentos; captor do Mockito sobre o varargs
 * de update() é frágil e foi evitado de propósito).
 */
class TenantMigrationRolloutTest {

    /** JdbcTemplate que não toca em banco: registra cada update(status, erro, data, id). */
    static class RecordingJdbcTemplate extends JdbcTemplate {
        final List<Object[]> updates = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updates.add(args);
            return 1;
        }
    }

    private static TenantMigrationRollout.Tenant tenant(String schema) {
        return new TenantMigrationRollout.Tenant(UUID.randomUUID(), schema);
    }

    private static TenantMigrationRollout.MigrationOutcome ok() {
        return new TenantMigrationRollout.MigrationOutcome(3, "2.11");
    }

    private static List<String> statuses(RecordingJdbcTemplate jdbc) {
        return jdbc.updates.stream().map(args -> (String) args[0]).toList();
    }

    @Test
    @DisplayName("todos concluem: canário primeiro, demais em paralelo, todos SUCCEEDED")
    void allSucceed() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        var rollout = new TenantMigrationRollout(jdbc,
                (schema, id) -> ok(), 2);
        var result = rollout.run(List.of(tenant("a"), tenant("b"), tenant("c")));

        assertThat(result.abortedAfterCanary()).isFalse();
        assertThat(result.succeeded()).hasSize(3);
        assertThat(result.quarantined()).isEmpty();
        assertThat(result.results().get(0).canary()).isTrue();
        assertThat(statuses(jdbc)).containsExactly("SUCCEEDED", "SUCCEEDED", "SUCCEEDED");
    }

    @Test
    @DisplayName("canário falha: onda abortada, demais nem tentados")
    void canaryFailureAbortsWave() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        var attempted = new ConcurrentHashMap<String, Boolean>();
        var rollout = new TenantMigrationRollout(jdbc,
                (schema, id) -> {
                    attempted.put(schema, true);
                    if (schema.equals("a")) {
                        throw new RuntimeException("migration quebrada no canário");
                    }
                    return ok();
                },
                2);
        var result = rollout.run(List.of(tenant("a"), tenant("b"), tenant("c")));

        assertThat(result.abortedAfterCanary()).isTrue();
        assertThat(attempted.keySet()).containsExactly("a");
        assertThat(result.quarantined()).hasSize(1);
        assertThat(result.quarantined().get(0).error()).contains("migration quebrada");
        // Só o canário teve situação gravada; os demais não têm linha de tentativa.
        assertThat(statuses(jdbc)).containsExactly("QUARANTINED");
        assertThat(jdbc.updates.get(0)[1]).isEqualTo("migration quebrada no canário");
    }

    @Test
    @DisplayName("não-canário falha: vai para quarentena sem parar os demais")
    void nonCanaryFailureQuarantinesOnlyThatTenant() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        var rollout = new TenantMigrationRollout(jdbc,
                (schema, id) -> {
                    if (schema.equals("b")) {
                        throw new RuntimeException("checksum divergente em b");
                    }
                    return new TenantMigrationRollout.MigrationOutcome(1, "2.11");
                },
                2);
        var result = rollout.run(List.of(tenant("a"), tenant("b"), tenant("c")));

        assertThat(result.abortedAfterCanary()).isFalse();
        assertThat(result.succeeded()).hasSize(2);
        assertThat(result.quarantined()).hasSize(1);
        assertThat(result.quarantined().get(0).tenant().schemaName()).isEqualTo("b");
        assertThat(result.quarantined().get(0).error()).contains("checksum divergente");
        assertThat(statuses(jdbc)).containsExactlyInAnyOrder("SUCCEEDED", "QUARANTINED", "SUCCEEDED");
    }

    @Test
    @DisplayName("tenants no mesmo schema migram uma vez só, com o mesmo resultado para todos")
    void sharedSchemaMigratesOnce() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        var calls = new AtomicInteger();
        var rollout = new TenantMigrationRollout(jdbc,
                (schema, id) -> {
                    calls.incrementAndGet();
                    return new TenantMigrationRollout.MigrationOutcome(2, "2.11");
                },
                2);
        var result = rollout.run(List.of(tenant("a"), tenant("shared"), tenant("shared")));

        // Canário "a" + grupo "shared" uma vez = 2 chamadas, 3 resultados.
        assertThat(calls.get()).isEqualTo(2);
        assertThat(result.succeeded()).hasSize(3);
        assertThat(result.results()).extracting(r -> r.tenant().schemaName())
                .containsExactly("a", "shared", "shared");
        assertThat(statuses(jdbc)).containsExactly("SUCCEEDED", "SUCCEEDED", "SUCCEEDED");
    }

    @Test
    @DisplayName("paralelismo limitado: nunca mais que N migrações simultâneas")
    void parallelismIsBounded() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        var current = new AtomicInteger();
        var max = new AtomicInteger();
        BiFunction<String, UUID, TenantMigrationRollout.MigrationOutcome> migrate = (schema, id) -> {
            int now = current.incrementAndGet();
            max.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                current.decrementAndGet();
            }
            return new TenantMigrationRollout.MigrationOutcome(0, "2.11");
        };
        var rollout = new TenantMigrationRollout(jdbc, migrate, 2);
        var result = rollout.run(List.of(tenant("a"), tenant("b"), tenant("c"),
                tenant("d"), tenant("e"), tenant("f")));

        assertThat(result.succeeded()).hasSize(6);
        assertThat(max.get()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("lista vazia: nenhum update, nenhum aborto")
    void emptyTenantListIsNoOp() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        var result = new TenantMigrationRollout(jdbc,
                (s, id) -> new TenantMigrationRollout.MigrationOutcome(0, "0"), 2)
                .run(List.of());
        assertThat(result.results()).isEmpty();
        assertThat(result.abortedAfterCanary()).isFalse();
        assertThat(jdbc.updates).isEmpty();
    }
}
