package br.com.ziipsan.app;

import br.com.ziipsan.core.migration.CoreMigrationRunner;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * P0.21: instruções preparadas sob PgBouncer em modo transaction, testadas.
 *
 * <p>O teste sobe um PgBouncer real (binário do host, porta própria, diretório em
 * /var/tmp) em modo transaction com {@code max_prepared_statements=100} e, através
 * dele, executa 20 vezes a mesma consulta preparada sobre um pool Hikari de 2
 * conexões — o cenário que quebra sem suporte a prepared statements no pooler
 * (a conexão do cliente volta ao pool e a próxima transação pode cair em outra
 * conexão do servidor, onde o prepared statement não existe). Se o binário
 * estiver ausente ou for anterior à 1.24 (quando o suporte em modo transaction
 * chegou), o teste é pulado com o motivo — e a pendência vai para o relatório
 * da frente, não vira verde falso.
 *
 * <p>COMBINAÇÃO PROIBIDA (defeito 3896 do pgJDBC): pgJDBC 42.7.8 com
 * {@code prepareThreshold=0}. A conexão do teste NÃO define prepareThreshold
 * (padrão 5) e a aplicação também não o define (ver application.yml): o teste
 * prova o caminho usado, não o proibido. As versões exatas vão para a
 * ARQUITETURA-ALVO §4 e para o relatório.
 */
class PgBouncerPreparedIT {

    private static final String RUN = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final int PGB_PORT = Integer.parseInt(
            System.getenv().getOrDefault("ZIIPSAN_PGB_PORT", "55442"));

    private static Process pgbouncer;
    private static String jdbcUrl;
    private static String migratorUser;
    private static String migratorPassword;

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel " + name + " nao definida (ver scripts/ci-linux.sh)");
        }
        return value;
    }

    @BeforeAll
    static void startPgBouncer() throws Exception {
        String versionOut;
        try {
            Process v = new ProcessBuilder("pgbouncer", "--version").start();
            versionOut = new String(v.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            v.waitFor();
        } catch (Exception e) {
            assumeTrue(false, "pgbouncer ausente no host: teste pulado (ver relatório P0.21)");
            return;
        }
        // "PgBouncer 1.24.1" -> [1, 24]
        String[] parts = versionOut.trim().split("\\s+")[1].split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        assumeTrue(major > 1 || (major == 1 && minor >= 24),
                "PgBouncer " + major + "." + minor + " < 1.24: sem max_prepared_statements em modo transaction");
        System.out.println("P0.21 PgBouncer: versão " + versionOut.trim());

        String itUrl = System.getenv().getOrDefault(
                "ZIIPSAN_IT_JDBC_URL", "jdbc:postgresql://host.docker.internal:55433/ziipsan");
        String hostPort = itUrl.replace("jdbc:postgresql://", "");
        String host = hostPort.substring(0, hostPort.indexOf(':'));
        if (host.equals("host.docker.internal")) {
            host = "127.0.0.1";
        }
        String rest = hostPort.substring(hostPort.indexOf(':') + 1);
        String port = rest.substring(0, rest.indexOf('/'));
        String db = rest.substring(rest.indexOf('/') + 1);
        migratorUser = System.getenv().getOrDefault("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator");
        migratorPassword = requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD");

        // Autossuficiência: a consulta do teste lê public.tenant_catalog (core V1).
        // Rodado sozinho num banco virgem, a tabela não existe — a suíte completa
        // a criava por acidente (outras classes provisionam tenants antes). Migrar
        // o core aqui torna o teste independente da ordem das classes.
        new CoreMigrationRunner(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        "jdbc:postgresql://" + host + ":" + port + "/" + db,
                        migratorUser, migratorPassword)).run(null);

        File dir = new File("/var/tmp", "pgb-p0r22-" + RUN);
        dir.mkdirs();
        // O pgbouncer roda como postgres (ver abaixo): o diretório e os arquivos
        // precisam ser legíveis por outro usuário — a umask deste host é 007, e
        // sem isto o pgbouncer morre com "Permission denied" no ini.
        dir.setReadable(true, false);
        dir.setExecutable(true, false);
        // userlist com a senha em TEXTO CLARO, não o hash md5: o PgBouncer precisa
        // da senha clara para fazer SCRAM no backend PG, e com um hash md5 no
        // userlist ele falha com "cannot do SCRAM authentication: wrong password
        // type" (reproduzido em 2026-11-03). Com auth_type=trust a senha do
        // userlist NÃO é validada contra o cliente — ela só alimenta a autenticação
        // no backend — então o texto claro aqui não afrouxa a autenticação do
        // cliente. Senha sintética do banco de integração, diretório descartável.
        Files.writeString(new File(dir, "userlist.txt").toPath(),
                "\"" + migratorUser + "\" \"" + migratorPassword + "\"\n");
        new File(dir, "userlist.txt").setReadable(true, false);
        // password= na seção [databases]: com auth_type=trust o cliente não entrega
        // senha ao PgBouncer, mas o backend PG exige SCRAM — sem senha o PgBouncer
        // não consegue autenticar no backend e o cliente recebe "wrong password
        // type". A senha aqui é a sintética do banco de integração, em diretório
        // descartável (o userlist ao lado já contém o hash md5 equivalente).
        Files.writeString(new File(dir, "pgbouncer.ini").toPath(), """
                [databases]
                ziipsan = host=%s port=%s dbname=%s password=%s
                [pgbouncer]
                listen_addr = 127.0.0.1
                listen_port = %d
                pool_mode = transaction
                max_prepared_statements = 100
                auth_type = trust
                auth_file = %s
                admin_users = %s
                """.formatted(host, port, db, migratorPassword, PGB_PORT,
                new File(dir, "userlist.txt").getAbsolutePath(), migratorUser));
        new File(dir, "pgbouncer.ini").setReadable(true, false);
        // PgBouncer recusa rodar como root ("PgBouncer should not run as root") e
        // este agente roda como root: nesse caso o processo é lançado como o
        // usuário postgres via setpriv (exec direto, sem wrapper — o destroy() do
        // @AfterAll mata o próprio pgbouncer). Sem setpriv ou sem o usuário
        // postgres, cai no lançamento direto e o assumeTrue abaixo pula o teste
        // com mensagem clara, como antes — nunca verde falso.
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
        assumeTrue(up, "PgBouncer não subiu na porta " + PGB_PORT);
        jdbcUrl = "jdbc:postgresql://127.0.0.1:" + PGB_PORT + "/ziipsan";
    }

    @AfterAll
    static void stopPgBouncer() {
        if (pgbouncer != null) {
            pgbouncer.destroy();
        }
    }

    @Test
    @DisplayName("20 execuções da mesma prepared statement via pool em modo transaction, sem erro")
    void preparedStatementsSurviveTransactionPooling() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(migratorUser);
        config.setPassword(migratorPassword);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(2);
        // Sem prepareThreshold na URL: padrão do pgJDBC (5), nunca 0 (defeito 3896).
        try (HikariDataSource ds = new HikariDataSource(config)) {
            String driverVersion;
            try (Connection c = ds.getConnection()) {
                driverVersion = c.getMetaData().getDriverVersion();
            }
            System.out.println("P0.21 pgJDBC: driver " + driverVersion);
            for (int i = 0; i < 20; i++) {
                try (Connection c = ds.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT count(*) FROM public.tenant_catalog WHERE status = ?")) {
                    ps.setString(1, "ACTIVE");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(0);
                    }
                }
            }
        }
    }
}
