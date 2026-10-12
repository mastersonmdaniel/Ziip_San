package br.com.sigsan.platform.tenant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consulta ao cadastro global de tenants (public.tenant_catalog): UUID → schema e situação.
 *
 * <p>É a fonte do schema do tenant da requisição. O UUID vem do token; o schema nunca vem do cliente.
 * Cache curto (60 s) em memória: suspender um tenant leva no máximo esse tempo para valer.
 */
@Component
public class TenantCatalog {

    /** Situação do tenant no catálogo. Só ACTIVE atende requisições. */
    public record Entry(UUID id, String schema, String status) {
        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    private static final Duration TTL = Duration.ofSeconds(60);

    private record Cached(Optional<Entry> entry, Instant expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public TenantCatalog(DataSource dataSource) {
        this(new JdbcTemplate(dataSource), Clock.systemUTC());
    }

    TenantCatalog(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<Entry> find(UUID tenantId) {
        Instant now = clock.instant();
        Cached cached = cache.get(tenantId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.entry();
        }
        Optional<Entry> entry = jdbc.query(
                "SELECT id, schema_name, status FROM public.tenant_catalog WHERE id = ?",
                (rs, i) -> new Entry(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                tenantId).stream().findFirst();
        cache.put(tenantId, new Cached(entry, now.plus(TTL)));
        return entry;
    }
}
