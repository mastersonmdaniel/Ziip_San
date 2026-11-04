package br.com.ziipsan.platform.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * P2.2: fabrica dos pools por papel.
 *
 * <p>Um {@link HikariDataSource} por {@link PoolRole}, cada um com usuario e
 * teto proprios, sobre a mesma URL JDBC (direta ou via PgBouncer em modo
 * transaction — o pooler distingue os papeis pelo usuario da conexao).
 * Quem fecha os pools e quem os criou: os DataSources devolvidos sao
 * {@link AutoCloseable} e o chamador (ou o Spring, no caso dos beans de
 * {@code RolePoolConfig}) deve fecha-los no fim do processo.
 *
 * <p>Regras herdadas, deliberadas:
 * <ul>
 *   <li>{@code minimumIdle = 0} e {@code initializationFailTimeout = -1}:
 *       criar o pool nao abre conexao nenhuma e nao falha se o banco estiver
 *       fora do ar na subida — o processo sobe (e o teste de fabrica roda)
 *       sem banco, e a conexao so abre no primeiro uso, quando um erro de
 *       conexao ou de autenticacao aparece com clareza. E uma diferenca
 *       DELIBERADA para o pool da API do {@code DataSourceConfig} (que falha
 *       rapido na subida): lote e ingestao rodam em processos proprios, que
 *       nao devem cair porque o banco ainda nao voltou numa reinicializacao.
 *       O IT de isolamento prova a conectividade real dos tres pools.</li>
 *   <li>{@code prepareThreshold} NUNCA e definido (regra da P0.21, PLT-35):
 *       o padrao do pgJDBC (5) e mantido, nunca 0 — a combinacao proibida
 *       e pgJDBC 42.7.8 com {@code prepareThreshold=0} (defeito 3896). O
 *       teste de fabrica trava a ausencia da propriedade.</li>
 *   <li>Sem senha padrao: credencial em branco e recusada aqui, em vez de
 *       falhar la na frente como erro de autenticacao do banco.</li>
 * </ul>
 */
public final class RoleDataSourceFactory {

    /** Credencial de um papel: usuario e senha (a senha vem do ambiente/cofre, nunca do repo). */
    public record RoleCredentials(String username, String password) {
        public RoleCredentials {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Usuario do pool e obrigatorio");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("Senha do pool e obrigatoria");
            }
        }
    }

    private RoleDataSourceFactory() {
    }

    /** Pool do papel com o teto padrao de {@link PoolRole#defaultMaxPoolSize()}. */
    public static HikariDataSource create(PoolRole role, String jdbcUrl, RoleCredentials credentials) {
        return create(role, jdbcUrl, credentials, role.defaultMaxPoolSize());
    }

    /** Pool do papel com teto explicito (o IT de saturacao usa 1 para provar o isolamento). */
    public static HikariDataSource create(PoolRole role, String jdbcUrl, RoleCredentials credentials,
                                          int maxPoolSize) {
        Objects.requireNonNull(role, "O papel do pool e obrigatorio");
        Objects.requireNonNull(credentials, "A credencial do pool e obrigatoria");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("URL JDBC e obrigatoria");
        }
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("Teto do pool deve ser >= 1");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName(role.poolName());
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(credentials.username());
        config.setPassword(credentials.password());
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(30_000);
        config.setMaxLifetime(1_800_000);
        // NENHUM addDataSourceProperty("prepareThreshold", ...): regra da P0.21.
        return new HikariDataSource(config);
    }

    /**
     * Os tres pools de uma vez, cada um com a sua credencial.
     *
     * <p>Todas as credenciais sao validadas ANTES de criar qualquer pool:
     * sem isso, um mapa incompleto criaria (e vazaria) os pools dos papeis
     * anteriores ao papel faltante, antes de falhar.
     */
    public static Map<PoolRole, HikariDataSource> createAll(String jdbcUrl,
                                                            Map<PoolRole, RoleCredentials> credentials) {
        Objects.requireNonNull(credentials, "As credenciais dos pools sao obrigatorias");
        for (PoolRole role : PoolRole.values()) {
            if (credentials.get(role) == null) {
                throw new IllegalArgumentException("Falta a credencial do pool " + role);
            }
        }
        Map<PoolRole, HikariDataSource> pools = new EnumMap<>(PoolRole.class);
        for (PoolRole role : PoolRole.values()) {
            pools.put(role, create(role, jdbcUrl, credentials.get(role)));
        }
        return pools;
    }
}
