package br.com.ziipsan.core.tenant;

import br.com.ziipsan.core.migration.CoreMigrationRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.21: shard no catálogo + teste de escala com 300 schemas.
 *
 * <p>O QUE É MEDIDO DE VERDADE AQUI (e o que não é — ver relatório da frente):
 * <ul>
 *   <li>300 schemas vazios criados + 300 linhas no catálogo, em 3 clusters de 100:
 *       tempo de criação, tamanho do catálogo, tempo de VACUUM e latência de
 *       lookup por (cluster_id, schema_name) — tudo medido e impresso;</li>
 *   <li>o UNIQUE revisto: mesmo schema_name em clusters diferentes aceito, repetido
 *       no mesmo cluster recusado;</li>
 *   <li>ziipsan_app: SELECT no catálogo OK, INSERT recusado (só SELECT, core V4/V7 — V4 da P0.17, reafirmada pela V7 da P0.21);</li>
 *   <li>pg_dump por schema: tempo real de dump de um schema, via binário pg_dump
 *       do host (o CI-linux roda sobre PGDG, onde ele existe);</li>
 *   <li>NÃO medido aqui: migração completa de tenant × 300 (custo alto demais para
 *       a suíte; medido 1× via provisionamento nos ITs de app) e pg_upgrade
 *       (exige segundo cluster de versão maior — pendência registrada).</li>
 * </ul>
 *
 * <p>Usa o PostgreSQL descartável de integração (mesmas variáveis do
 * TenantRlsIsolationTest). Schemas e linhas com prefixo itest_shard_ + id da
 * execução, removidos no fim.
 */
class ShardCatalogScaleTest {

    private static final String RUN_ID = Long.toHexString(System.nanoTime() & 0xFFFFFFL);
    private static final String PREFIX = "itest_shard_" + RUN_ID + "_";
    private static final int CLUSTERS = 3;
    private static final int PER_CLUSTER = 100;
    private static final int TOTAL = CLUSTERS * PER_CLUSTER;

    private static DataSource appDs;
    private static DataSource migratorDs;

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel " + name + " nao definida (ver scripts/ci-linux.sh)");
        }
        return value;
    }

    @BeforeAll
    static void connect() {
        String url = System.getenv().getOrDefault(
                "ZIIPSAN_IT_JDBC_URL", "jdbc:postgresql://host.docker.internal:55433/ziipsan");
        appDs = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                url, System.getenv().getOrDefault("ZIIPSAN_IT_DB_USER", "ziipsan_app"),
                requireEnv("ZIIPSAN_IT_DB_PASSWORD"));
        migratorDs = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                url, System.getenv().getOrDefault("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator"),
                requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD"));
        new CoreMigrationRunner(migratorDs).run(null);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        try (Connection conn = migratorDs.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM public.tenant_catalog WHERE schema_name LIKE '" + PREFIX + "%'");
            for (int i = 0; i < TOTAL; i++) {
                st.execute("DROP SCHEMA IF EXISTS " + PREFIX + i + " CASCADE");
            }
        }
    }

    private static String schemaName(int i) {
        return PREFIX + i;
    }

    private static String clusterOf(int i) {
        return "cluster-" + (i % CLUSTERS);
    }

    @Test
    @DisplayName("P0.21: 300 schemas, catálogo, vacuum, latência, UNIQUE por cluster, app só SELECT, pg_dump")
    void scale300() throws Exception {
        // --- colunas da V7 existem
        try (Connection conn = migratorDs.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT column_name FROM information_schema.columns "
                             + "WHERE table_schema = 'public' AND table_name = 'tenant_catalog' "
                             + "AND column_name IN ('cluster_id', 'isolation_mode', 'tenant_schema_version')")) {
            List<String> cols = new ArrayList<>();
            while (rs.next()) {
                cols.add(rs.getString(1));
            }
            assertThat(cols).containsExactlyInAnyOrder("cluster_id", "isolation_mode", "tenant_schema_version");
        }

        // --- 300 schemas vazios + 300 linhas no catálogo, com tempo
        long t0 = System.nanoTime();
        try (Connection conn = migratorDs.getConnection(); Statement st = conn.createStatement()) {
            for (int i = 0; i < TOTAL; i++) {
                st.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName(i));
            }
        }
        long schemaMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("P0.21 escala: " + TOTAL + " schemas criados em " + schemaMs + " ms");
        t0 = System.nanoTime();
        try (Connection conn = migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.tenant_catalog (id, schema_name, name, status, cluster_id, isolation_mode)"
                             + " VALUES (?, ?, ?, 'ACTIVE', ?, 'SCHEMA')")) {
            for (int i = 0; i < TOTAL; i++) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, schemaName(i));
                ps.setString(3, "Tenant sintético escala P0.21 " + i);
                ps.setString(4, clusterOf(i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        long catalogMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("P0.21 escala: " + TOTAL + " linhas no catálogo em " + catalogMs + " ms");

        // --- tamanho do catálogo + vacuum
        long catalogBytes;
        try (Connection conn = migratorDs.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT pg_total_relation_size('public.tenant_catalog')")) {
            rs.next();
            catalogBytes = rs.getLong(1);
        }
        System.out.println("P0.21 escala: tenant_catalog ocupa " + catalogBytes + " bytes com " + TOTAL + " linhas de teste");
        t0 = System.nanoTime();
        try (Connection conn = migratorDs.getConnection(); Statement st = conn.createStatement()) {
            st.execute("VACUUM ANALYZE public.tenant_catalog");
        }
        long vacuumMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("P0.21 escala: VACUUM ANALYZE do catálogo em " + vacuumMs + " ms");

        // --- latência de lookup por (cluster_id, schema_name), 200 repetições
        long lookups = 200;
        t0 = System.nanoTime();
        try (Connection conn = migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM public.tenant_catalog WHERE cluster_id = ? AND schema_name = ?")) {
            for (int i = 0; i < lookups; i++) {
                int row = i % TOTAL;
                ps.setString(1, clusterOf(row));
                ps.setString(2, schemaName(row));
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                }
            }
        }
        double meanLookupMs = (System.nanoTime() - t0) / 1_000_000.0 / lookups;
        System.out.println("P0.21 escala: lookup por (cluster_id, schema_name), média de "
                + String.format("%.3f", meanLookupMs) + " ms em " + lookups + " repetições");
        assertThat(meanLookupMs).isLessThan(500);

        // --- contagem por cluster respeita o teto registrado (300: espelha
        // TenantCatalog.MAX_SCHEMAS_PER_CLUSTER, de platform — core não depende de
        // platform, então o número aparece aqui como literal documentado, não solto).
        try (Connection conn = migratorDs.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT cluster_id, count(*) FROM public.tenant_catalog "
                             + "WHERE schema_name LIKE '" + PREFIX + "%' GROUP BY cluster_id")) {
            while (rs.next()) {
                assertThat(rs.getLong(2)).isLessThanOrEqualTo(300);
            }
        }

        // --- UNIQUE revisto: mesmo nome em outro cluster OK, no mesmo cluster recusado.
        // Transação explícita + rollback no fim: nada deste bloco fica no catálogo.
        try (Connection conn = migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.tenant_catalog (id, schema_name, name, status, cluster_id, isolation_mode)"
                             + " VALUES (?, ?, 'x', 'ACTIVE', ?, 'SCHEMA')")) {
            conn.setAutoCommit(false);
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "shared-name-" + RUN_ID);
            ps.setString(3, "cluster-outro");
            ps.executeUpdate();
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "shared-name-" + RUN_ID);
            ps.setString(3, "cluster-terceiro");
            ps.executeUpdate();
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "shared-name-" + RUN_ID);
            ps.setString(3, "cluster-outro");
            assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
            conn.rollback();
        }

        // --- app só SELECT no catálogo
        try (Connection conn = appDs.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM public.tenant_catalog")) {
            assertThat(rs.next()).isTrue();
        }
        try (Connection conn = appDs.getConnection(); Statement st = conn.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO public.tenant_catalog (id, schema_name, name, status) VALUES ('"
                            + UUID.randomUUID() + "', 'itest_app_write_" + RUN_ID + "', 'x', 'ACTIVE')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }

        // --- pg_dump por schema (binário do host; o CI-linux roda sobre PGDG)
        String jdbcUrl = System.getenv().getOrDefault(
                "ZIIPSAN_IT_JDBC_URL", "jdbc:postgresql://host.docker.internal:55433/ziipsan");
        String hostPort = jdbcUrl.replace("jdbc:postgresql://", "");
        String host = hostPort.substring(0, hostPort.indexOf(':'));
        String rest = hostPort.substring(hostPort.indexOf(':') + 1);
        String port = rest.substring(0, rest.indexOf('/'));
        String db = rest.substring(rest.indexOf('/') + 1);
        File dump = new File("/var/tmp", "p0r21-dump-" + RUN_ID + ".sql");
        t0 = System.nanoTime();
        ProcessBuilder pb = new ProcessBuilder("pg_dump", "-h", host, "-p", port, "-U",
                System.getenv().getOrDefault("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator"),
                "-n", schemaName(0), "-f", dump.getAbsolutePath(), db);
        pb.environment().put("PGPASSWORD", requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD"));
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes());
        int exit = proc.waitFor();
        long dumpMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("P0.21 escala: pg_dump de 1 schema (vazio) em " + dumpMs + " ms, saída "
                + dump.length() + " bytes");
        assertThat(exit).as("pg_dump: %s", out).isZero();
        assertThat(dump.length()).isGreaterThan(0);
        dump.delete();
    }
}
