package br.com.ziipsan.platform.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correção RG-Lote3 (08/11/2026): o carimbo {@code migration_attempted_at}
 * gravado no catálogo vem do {@link Clock} injetado no rollout — com
 * relógio fixo, o {@code Timestamp} da atualização é exatamente o instante
 * do relógio. Mesmo dublê do {@code TenantMigrationRolloutWaveTest}
 * (JdbcTemplate que só registra), sem banco.
 */
class TenantMigrationRolloutClockTest {

    static class RecordingJdbcTemplate extends JdbcTemplate {
        final List<Object[]> updates = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updates.add(args);
            return 1;
        }
    }

    @Test
    @DisplayName("migration_attempted_at vem do Clock injetado")
    void attemptedAtComesFromInjectedClock() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        Instant fixed = Instant.parse("2026-11-08T13:00:00Z");
        var rollout = new TenantMigrationRollout(jdbc,
                (schema, id) -> new TenantMigrationRollout.MigrationOutcome(1, "100"),
                2,
                Clock.fixed(fixed, ZoneOffset.UTC));

        var result = rollout.run(List.of(
                new TenantMigrationRollout.Tenant(UUID.randomUUID(), "tenant_a")));

        assertThat(result.succeeded()).hasSize(1);
        assertThat(jdbc.updates).hasSize(1);
        // recordStatuses grava (migration_status, migration_error,
        // migration_attempted_at, id) — o carimbo é o argumento 2.
        assertThat(jdbc.updates.get(0)[0]).isEqualTo("SUCCEEDED");
        assertThat(((Timestamp) jdbc.updates.get(0)[2]).toInstant()).isEqualTo(fixed);
    }
}
