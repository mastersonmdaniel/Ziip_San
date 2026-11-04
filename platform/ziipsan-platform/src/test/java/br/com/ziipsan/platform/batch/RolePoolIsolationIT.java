package br.com.ziipsan.platform.batch;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ziipsan.platform.tenant.PoolRole;
import br.com.ziipsan.platform.tenant.RoleDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.2 sobre o Postgres real, SEM PgBouncer (a variante via PgBouncer em
 * modo transaction esta no {@code RolePoolPgBouncerIT}).
 *
 * <p>Os tres pools sao criados com teto 1: cada pool tem UMA conexao fisica,
 * entao toda leitura depois de uma troca de tenant reaproveita FORCADO a
 * mesma conexao — o cenario em que um contexto de sessao vazaria. Como o
 * contexto aqui e LOCAL a transacao (P0.28, via {@code JdbcBatchJobStore}
 * da P2.1), a troca nao vaza em nenhum pool.
 *
 * <p>Credenciais por ambiente, sem valor padrao versionado (mesmo padrao do
 * {@code BatchItDatabase}): as senhas de lote e ingestao vem de
 * {@code ZIIPSAN_IT_BATCH_PASSWORD} / {@code ZIIPSAN_IT_INGEST_PASSWORD}
 * (o roteiro de IT concede LOGIN as roles, como faz para o worker). Sem
 * elas, os testes falham rapido com o nome da variavel — nunca com banco
 * inventado. A senha da app vem de {@code ZIIPSAN_IT_DB_PASSWORD}, como os
 * demais ITs do modulo.
 */
class RolePoolIsolationIT {

    private static Map<PoolRole, HikariDataSource> pools;

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Variavel de ambiente obrigatoria para o IT ausente: " + name);
        }
        return value;
    }

    @BeforeAll
    static void ensureDatabaseAndPools() {
        BatchItDatabase.ensure();
        // Teto 1 em todos os pools: reaproveitamento forcado da conexao fisica.
        pools = Map.of(
                PoolRole.API, RoleDataSourceFactory.create(PoolRole.API, BatchItDatabase.url,
                        new RoleDataSourceFactory.RoleCredentials("ziipsan_app",
                                requireEnv("ZIIPSAN_IT_DB_PASSWORD")), 1),
                PoolRole.BATCH, RoleDataSourceFactory.create(PoolRole.BATCH, BatchItDatabase.url,
                        new RoleDataSourceFactory.RoleCredentials("ziipsan_batch",
                                requireEnv("ZIIPSAN_IT_BATCH_PASSWORD")), 1),
                PoolRole.INGEST, RoleDataSourceFactory.create(PoolRole.INGEST, BatchItDatabase.url,
                        new RoleDataSourceFactory.RoleCredentials("ziipsan_ingest",
                                requireEnv("ZIIPSAN_IT_INGEST_PASSWORD")), 1));
    }

    @AfterAll
    static void closePools() {
        if (pools != null) {
            pools.values().forEach(HikariDataSource::close);
        }
    }

    @Test
    @DisplayName("isolamento x3 pools: lote criado por A via pool de lote nao e visto por B em nenhum pool")
    void isolationHoldsOnAllThreePools() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String cycle = "2027-03-" + UUID.randomUUID().toString().substring(0, 8);

        // Criacao pelo pool de lote (o store da P2.1, sem duplicar a logica).
        BatchJobStore batchStore = new JdbcBatchJobStore(pools.get(PoolRole.BATCH));
        batchStore.createRun(tenantA, cycle, List.of("rota-a"),
                LocalDate.of(2027, 3, 1), "America/Sao_Paulo", null, null);

        // Leitura nos tres pools, com troca de tenant na mesma conexao fisica
        // (teto 1): A ve, B nao ve — em nenhum pool, depois de nenhuma troca.
        for (PoolRole role : PoolRole.values()) {
            BatchJobStore store = new JdbcBatchJobStore(pools.get(role));
            assertThat(store.findRun(tenantA, cycle))
                    .as("pool %s: tenant A ve o proprio lote", role)
                    .isPresent();
            assertThat(store.findRun(tenantB, cycle))
                    .as("pool %s: tenant B nao ve o lote de A", role)
                    .isEmpty();
            // Troca de volta: A continua vendo depois de B ter usado a conexao.
            assertThat(store.findRun(tenantA, cycle))
                    .as("pool %s: A continua vendo apos B usar a conexao", role)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("saturacao: pool de lote com a unica conexao ocupada nao impede o pool da API")
    void saturatedBatchPoolDoesNotStarveApiPool() throws Exception {
        HikariDataSource batch = pools.get(PoolRole.BATCH);
        HikariDataSource api = pools.get(PoolRole.API);
        // Ocupa a unica conexao do pool de lote (transacao aberta, sem commit).
        Connection held = batch.getConnection();
        held.setAutoCommit(false);
        try {
            assertThat(batch.getHikariPoolMXBean().getActiveConnections())
                    .as("pool de lote saturado: 1/1 ativa")
                    .isEqualTo(1);
            // O pool da API serve normalmente enquanto o de lote esta saturado.
            try (Connection apiConn = api.getConnection();
                 PreparedStatement ps = apiConn.prepareStatement(
                         "SELECT count(*) FROM public.tenant_catalog");
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(0);
            }
            // E o de ingestao tambem.
            try (Connection ingestConn = pools.get(PoolRole.INGEST).getConnection();
                 PreparedStatement ps = ingestConn.prepareStatement(
                         "SELECT count(*) FROM public.tenant_catalog");
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
            }
        } finally {
            held.rollback();
            held.setAutoCommit(true);
            held.close();
        }
    }

    @Test
    @DisplayName("P0.21 sobre os tres pools: catalogo, versao de schema por tenant e prepared repetida")
    void catalogAndTenantSchemaVersionReadableOnAllPools() throws Exception {
        for (PoolRole role : PoolRole.values()) {
            try (Connection conn = pools.get(role).getConnection()) {
                // Catalogo (P0.21): colunas de shard e de versao de schema existem.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns"
                                + " WHERE table_schema = 'public' AND table_name = 'tenant_catalog'"
                                + " AND column_name IN ('cluster_id', 'isolation_mode',"
                                + " 'tenant_schema_version')");
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).as("pool %s: colunas do catalogo", role).isEqualTo(3);
                }
                // A prepared statement repetida 20 vezes no mesmo pool (a regra
                // da P0.21: sem prepareThreshold=0; o padrao do pgJDBC vale).
                for (int i = 0; i < 20; i++) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT count(*) FROM public.tenant_catalog WHERE status = ?")) {
                        ps.setString(1, "ACTIVE");
                        try (ResultSet rs = ps.executeQuery()) {
                            assertThat(rs.next()).isTrue();
                            assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(0);
                        }
                    }
                }
                // Versao de schema por tenant (P0.21/P1.14): a coluna e legivel
                // no pool, mesmo com o catalogo vazio (nenhum tenant provisionado).
                int rows = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT tenant_schema_version FROM public.tenant_catalog");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows++;
                    }
                }
                assertThat(rows).as("pool %s: linhas lidas do catalogo", role).isGreaterThanOrEqualTo(0);
            }
        }
    }
}
