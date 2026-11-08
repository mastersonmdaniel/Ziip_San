package br.com.ziipsan.platform.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.12: o rollout do job {@code migrate} em ONDAS — fronteira de onda
 * observável (a onda seguinte só começa com a anterior concluída e gravada)
 * e quarentena que não bloqueia os demais, inclusive os das ondas seguintes.
 * Mesmo dublê do {@code TenantMigrationRolloutTest} da P1.14 (JdbcTemplate que
 * só registra), sem banco.
 */
class TenantMigrationRolloutWaveTest {

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

    private static List<String> statuses(RecordingJdbcTemplate jdbc) {
        return jdbc.updates.stream().map(args -> (String) args[0]).toList();
    }

    @Test
    @DisplayName("tenant em falha numa onda não bloqueia os demais, nem os das ondas seguintes")
    void failingTenantInWaveDoesNotBlockOtherWaves() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        var attempted = ConcurrentHashMap.<String>newKeySet();
        var rollout = new TenantMigrationRollout(jdbc,
                (schema, id) -> {
                    attempted.add(schema);
                    if (schema.equals("b")) {
                        throw new RuntimeException("migration quebrada em b");
                    }
                    return new TenantMigrationRollout.MigrationOutcome(1, "100");
                },
                2);
        // Canário a; onda 1 = b, c; onda 2 = d, e. O b falha na onda 1.
        var result = rollout.run(List.of(
                tenant("a"), tenant("b"), tenant("c"), tenant("d"), tenant("e")));

        assertThat(result.abortedAfterCanary()).isFalse();
        assertThat(attempted).containsExactlyInAnyOrder("a", "b", "c", "d", "e");
        assertThat(result.succeeded()).hasSize(4);
        assertThat(result.quarantined()).hasSize(1);
        assertThat(result.quarantined().get(0).tenant().schemaName()).isEqualTo("b");
        assertThat(result.quarantined().get(0).error()).contains("migration quebrada em b");
        assertThat(statuses(jdbc)).containsExactlyInAnyOrder(
                "SUCCEEDED", "QUARANTINED", "SUCCEEDED", "SUCCEEDED", "SUCCEEDED");
    }

    @Test
    @DisplayName("fronteira de onda: tenant da onda 2 só começa depois de a onda 1 inteira terminar")
    void waveBarrierIsRespected() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        var finished = new AtomicInteger();
        var finishedAtStart = new ConcurrentHashMap<String, Integer>();
        var rollout = new TenantMigrationRollout(jdbc,
                (schema, id) -> {
                    finishedAtStart.put(schema, finished.get());
                    finished.incrementAndGet();
                    return new TenantMigrationRollout.MigrationOutcome(0, "100");
                },
                2);
        // Canário a; onda 1 = b, c; onda 2 = d, e; onda 3 = f.
        var result = rollout.run(List.of(
                tenant("a"), tenant("b"), tenant("c"), tenant("d"), tenant("e"), tenant("f")));

        assertThat(result.succeeded()).hasSize(6);
        // O canário começa com 0 concluídos. Dentro de uma onda os tenants
        // correm em paralelo, então a contagem exata entre eles varia (b e c
        // veem 1 ou 2); o invariante determinístico da fronteira é ENTRE
        // ondas: d e e (onda 2) só começam com canário + onda 1 inteira
        // concluídos (3 ou 4) — sem fronteira, d poderia começar com 2 — e
        // f (onda 3, sozinho) começa com exatamente 5.
        assertThat(finishedAtStart.get("a")).isEqualTo(0);
        assertThat(finishedAtStart.get("b")).isGreaterThan(0);
        assertThat(finishedAtStart.get("b")).isLessThanOrEqualTo(2);
        assertThat(finishedAtStart.get("c")).isGreaterThan(0);
        assertThat(finishedAtStart.get("c")).isLessThanOrEqualTo(2);
        assertThat(finishedAtStart.get("d")).isGreaterThan(2);
        assertThat(finishedAtStart.get("d")).isLessThanOrEqualTo(4);
        assertThat(finishedAtStart.get("e")).isGreaterThan(2);
        assertThat(finishedAtStart.get("e")).isLessThanOrEqualTo(4);
        assertThat(finishedAtStart.get("f")).isEqualTo(5);
    }

    @Test
    @DisplayName("situação é gravada por onda: ao fim da onda 1, canário e onda 1 já têm status")
    void statusesAreRecordedPerWave() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        var updatesAtStart = new ConcurrentHashMap<String, Integer>();
        var rollout = new TenantMigrationRollout(jdbc,
                (schema, id) -> {
                    updatesAtStart.put(schema, jdbc.updates.size());
                    return new TenantMigrationRollout.MigrationOutcome(0, "100");
                },
                2);
        var result = rollout.run(List.of(tenant("a"), tenant("b"), tenant("c"), tenant("d")));

        assertThat(result.succeeded()).hasSize(4);
        // d é da onda 2: quando ele começa, o catálogo já tinha o status do
        // canário (1) + os da onda 1 (2) = 3 updates gravados.
        assertThat(updatesAtStart.get("d")).isEqualTo(3);
        assertThat(statuses(jdbc)).containsExactly(
                "SUCCEEDED", "SUCCEEDED", "SUCCEEDED", "SUCCEEDED");
    }
}
