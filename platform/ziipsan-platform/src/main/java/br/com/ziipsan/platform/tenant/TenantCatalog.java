package br.com.ziipsan.platform.tenant;

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

    /**
     * P0.21: teto de schemas por cluster, registrado na ARQUITETURA-ALVO §3. É o
     * número provado pelo teste de escala (300 schemas); acima dele o tenant vai
     * para outro cluster. A APLICAÇÃO do teto (recusar provisionamento acima dele)
     * é da P1.14, junto do rollout — aqui o teto é só o número registrado, e o
     * teste de escala o usa como asserção, não como número solto no teste.
     */
    public static final int MAX_SCHEMAS_PER_CLUSTER = 300;

    /** Situação do tenant no catálogo. Só ACTIVE atende requisições. */
    public record Entry(UUID id, String schema, String status) {
        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    private static final Duration TTL = Duration.ofSeconds(60);

    private record Cached(Optional<Entry> entry, Instant expiresAt) {
    }

    private record CachedResolution(Optional<TenantResolution> resolution, Instant expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CachedResolution> resolutionCache = new ConcurrentHashMap<>();

    @Autowired
    public TenantCatalog(DataSource dataSource) {
        this(new JdbcTemplate(dataSource), Clock.systemUTC());
    }

    TenantCatalog(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * P4.6, item 4 do aceite: resolucao completa do tenant — schema, CLUSTER e
     * MODO de isolamento — para o roteamento de pools por cluster e por papel
     * ({@link ClusterPoolRouter}). Le as colunas da core V7 (cluster_id,
     * isolation_mode) que o {@link #find(UUID)} nao le. Cache proprio, com o
     * mesmo TTL de 60 s: suspender um tenant ou muda-lo de cluster/modo leva
     * no maximo esse tempo para valer no roteamento.
     */
    public Optional<TenantResolution> findResolution(UUID tenantId) {
        Instant now = clock.instant();
        CachedResolution cached = resolutionCache.get(tenantId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.resolution();
        }
        Optional<TenantResolution> resolution = jdbc.query(
                "SELECT id, schema_name, cluster_id, isolation_mode, status"
                        + " FROM public.tenant_catalog WHERE id = ?",
                (rs, i) -> new TenantResolution(
                        rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        IsolationMode.parse(rs.getString(4)), rs.getString(5)),
                tenantId).stream().findFirst();
        resolutionCache.put(tenantId, new CachedResolution(resolution, now.plus(TTL)));
        return resolution;
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
