package br.com.ziipsan.platform.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.ziipsan.platform.tenant.PoolRole;
import br.com.ziipsan.platform.tenant.RoleDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.2: o isolamento entre tenants repetido nos tres pools ATRAVES do
 * PgBouncer em modo transaction — o ensaio que o plano pede.
 *
 * <p>Mesmo padrao do {@code PgBouncerPreparedIT} (P0.21): sobe um PgBouncer
 * real do host, em modo transaction com {@code max_prepared_statements=100}
 * e {@code server_reset_query=DISCARD ALL} (a segunda barreira contra
 * vazamento, como no {@code infra/pgbouncer/pgbouncer.ini} do deploy). Os
 * tres pools tem teto 1 e usuario proprio: no PgBouncer, o pool e por par
 * (usuario, banco), entao cada papel tem o seu pool no pooler — e duas
 * transacoes de tenants diferentes na mesma conexao fisica do servidor nao
 * podem ver o contexto uma da outra, porque o contexto e LOCAL a transacao
 * (P0.28) e descartado no commit.
 *
 * <p>Se o binario estiver ausente ou for anterior a 1.24 (quando o suporte
 * a prepared statements em modo transaction chegou), o teste e pulado com
 * o motivo — e a pendencia vai para o relatorio da frente, nao vira verde
 * falso. As senhas sao as sinteticas do banco de integracao, em diretorio
 * descartavel; com {@code auth_type=trust} a senha da userlist NAO e
 * validada contra o cliente — ela so alimenta a autenticacao SCRAM no
 * backend (o mesmo motivo do PgBouncerPreparedIT para o texto claro).
 */
class RolePoolPgBouncerIT {

    private static final String RUN = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final int PGB_PORT = Integer.parseInt(
            System.getenv().getOrDefault("ZIIPSAN_PGB_POOLS_PORT", "55444"));

    private static Process pgbouncer;
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
    static void startPgBouncerAndPools() throws Exception {
        BatchItDatabase.ensure();
        String versionOut;
        try {
            Process v = new ProcessBuilder("pgbouncer", "--version").start();
            versionOut = new String(v.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            v.waitFor();
        } catch (Exception e) {
            assumeTrue(false, "pgbouncer ausente no host: teste pulado (ver relatorio P2.2)");
            return;
        }
        String[] parts = versionOut.trim().split("\\s+")[1].split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        assumeTrue(major > 1 || (major == 1 && minor >= 24),
                "PgBouncer " + major + "." + minor + " < 1.24: sem max_prepared_statements em modo transaction");
        System.out.println("P2.2 PgBouncer: versao " + versionOut.trim());

        String itUrl = BatchItDatabase.url.replace("jdbc:postgresql://", "");
        String host = itUrl.substring(0, itUrl.indexOf(':'));
        String rest = itUrl.substring(itUrl.indexOf(':') + 1);
        String port = rest.substring(0, rest.indexOf('/'));
        String db = rest.substring(rest.indexOf('/') + 1);
        String appPw = requireEnv("ZIIPSAN_IT_DB_PASSWORD");
        String batchPw = requireEnv("ZIIPSAN_IT_BATCH_PASSWORD");
        String ingestPw = requireEnv("ZIIPSAN_IT_INGEST_PASSWORD");

        File dir = new File("/var/tmp", "pgb-p22-" + RUN);
        dir.mkdirs();
        dir.setReadable(true, false);
        dir.setExecutable(true, false);
        Files.writeString(new File(dir, "userlist.txt").toPath(),
                "\"ziipsan_app\" \"" + appPw + "\"\n"
                        + "\"ziipsan_batch\" \"" + batchPw + "\"\n"
                        + "\"ziipsan_ingest\" \"" + ingestPw + "\"\n");
        new File(dir, "userlist.txt").setReadable(true, false);
        Files.writeString(new File(dir, "pgbouncer.ini").toPath(), """
                [databases]
                ziipsan = host=%s port=%s dbname=%s
                [pgbouncer]
                listen_addr = 127.0.0.1
                listen_port = %d
                pool_mode = transaction
                max_prepared_statements = 100
                server_reset_query = DISCARD ALL
                auth_type = trust
                auth_file = %s
                admin_users = ziipsan_app
                """.formatted(host, port, db, PGB_PORT,
                new File(dir, "userlist.txt").getAbsolutePath()));
        new File(dir, "pgbouncer.ini").setReadable(true, false);
        List<String> command = new ArrayList<>();
        if ("root".equals(System.getProperty("user.name"))
                && new File("/usr/bin/setpriv").canExecute()) {
            command.addAll(List.of("/usr/bin/setpriv", "--reuid=postgres",
                    "--regid=postgres", "--clear-groups"));
        }
        command.addAll(List.of("pgbouncer",
                new File(dir, "pgbouncer.ini").getAbsolutePath()));
        pgbouncer = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean up = false;
        for (int i = 0; i < 30 && !up; i++) {
            try (Socket s = new Socket("127.0.0.1", PGB_PORT)) {
                up = true;
            } catch (Exception ignored) {
                Thread.sleep(1000);
            }
        }
        assumeTrue(up, "PgBouncer nao subiu na porta " + PGB_PORT);

        String pgbUrl = "jdbc:postgresql://127.0.0.1:" + PGB_PORT + "/ziipsan";
        pools = Map.of(
                PoolRole.API, RoleDataSourceFactory.create(PoolRole.API, pgbUrl,
                        new RoleDataSourceFactory.RoleCredentials("ziipsan_app", appPw), 1),
                PoolRole.BATCH, RoleDataSourceFactory.create(PoolRole.BATCH, pgbUrl,
                        new RoleDataSourceFactory.RoleCredentials("ziipsan_batch", batchPw), 1),
                PoolRole.INGEST, RoleDataSourceFactory.create(PoolRole.INGEST, pgbUrl,
                        new RoleDataSourceFactory.RoleCredentials("ziipsan_ingest", ingestPw), 1));
    }

    @AfterAll
    static void stopPoolsAndPgBouncer() {
        if (pools != null) {
            pools.values().forEach(HikariDataSource::close);
        }
        if (pgbouncer != null) {
            pgbouncer.destroy();
        }
    }

    @Test
    @DisplayName("via PgBouncer: lote de A pelo pool de lote nao e visto por B em nenhum pool")
    void isolationHoldsThroughPgBouncerOnAllPools() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String cycle = "2027-03-" + UUID.randomUUID().toString().substring(0, 8);

        BatchJobStore batchStore = new JdbcBatchJobStore(pools.get(PoolRole.BATCH));
        batchStore.createRun(tenantA, cycle, List.of("rota-a"),
                LocalDate.of(2027, 3, 1), "America/Sao_Paulo", null, null);

        for (PoolRole role : PoolRole.values()) {
            BatchJobStore store = new JdbcBatchJobStore(pools.get(role));
            assertThat(store.findRun(tenantA, cycle))
                    .as("via PgBouncer, pool %s: A ve o proprio lote", role)
                    .isPresent();
            assertThat(store.findRun(tenantB, cycle))
                    .as("via PgBouncer, pool %s: B nao ve o lote de A", role)
                    .isEmpty();
            assertThat(store.findRun(tenantA, cycle))
                    .as("via PgBouncer, pool %s: A continua vendo apos B", role)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("via PgBouncer: pool de lote saturado nao impede o pool da API")
    void saturatedBatchPoolDoesNotStarveApiPoolThroughPgBouncer() throws Exception {
        HikariDataSource batch = pools.get(PoolRole.BATCH);
        Connection held = batch.getConnection();
        held.setAutoCommit(false);
        try {
            assertThat(batch.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
            try (Connection apiConn = pools.get(PoolRole.API).getConnection();
                 PreparedStatement ps = apiConn.prepareStatement(
                         "SELECT count(*) FROM public.tenant_catalog");
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(0);
            }
        } finally {
            held.rollback();
            held.setAutoCommit(true);
            held.close();
        }
    }
}
