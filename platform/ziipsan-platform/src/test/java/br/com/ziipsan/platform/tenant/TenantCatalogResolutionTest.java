package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * P4.6, item 4: findResolution devolve cluster e modo (colunas da core V7),
 * com o mesmo cache de 60 s do find. JdbcTemplate falso que conta as idas:
 * a prova contra o banco real da resolucao esta no catalogo real dos ITs.
 */
class TenantCatalogResolutionTest {

    /** JdbcTemplate que devolve sempre a mesma lista e conta as consultas. */
    static final class CountingJdbc extends JdbcTemplate {
        private final List<TenantResolution> result;
        int queries;

        CountingJdbc(List<TenantResolution> result) {
            this.result = result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queries++;
            return (List<T>) result;
        }
    }

    @Test
    @DisplayName("findResolution: devolve schema, cluster e modo; segunda chamada vem do cache")
    void resolutionReturnsClusterAndModeAndCaches() {
        UUID tenantId = UUID.randomUUID();
        var expected = new TenantResolution(tenantId, "tenant_shared", "default",
                IsolationMode.SHARED, "ACTIVE");
        var jdbc = new CountingJdbc(List.of(expected));
        var catalog = new TenantCatalog(jdbc,
                Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC));

        assertThat(catalog.findResolution(tenantId)).contains(expected);
        assertThat(catalog.findResolution(tenantId)).contains(expected);
        assertThat(jdbc.queries).isEqualTo(1);
    }

    @Test
    @DisplayName("findResolution: tenant desconhecido devolve vazio")
    void unknownTenantIsEmpty() {
        var catalog = new TenantCatalog(new CountingJdbc(List.of()), Clock.systemUTC());
        assertThat(catalog.findResolution(UUID.randomUUID())).isEmpty();
    }
}
