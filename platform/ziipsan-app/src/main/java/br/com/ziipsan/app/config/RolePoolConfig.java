package br.com.ziipsan.app.config;

import br.com.ziipsan.platform.tenant.PoolRole;
import br.com.ziipsan.platform.tenant.RoleDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P2.2: beans dos pools por papel (lote e ingestao).
 *
 * <p>O pool da API continua sendo o {@code dataSource} primario do
 * {@code DataSourceConfig} (role {@code ziipsan_app}, teto 20): esta classe
 * so acrescenta os outros dois papeis, cada um com a sua credencial e o
 * seu teto (padroes da {@link PoolRole}: lote 10, ingestao 5).
 *
 * <p>Desligado por padrao ({@code ziipsan.db.pools.enabled=false}): sem a
 * flag, nenhum contexto existente muda e nenhum bean novo exige senha —
 * o deploy que opera lote/ingestao em processo proprio liga a flag e
 * informa as senhas ({@code ZIIPSAN_DB_BATCH_PASSWORD} e
 * {@code ZIIPSAN_DB_INGEST_PASSWORD}, ver application.yml e .env.example).
 * Os beans NAO sao primarios: nada que injete {@code DataSource} sem
 * qualificador muda de pool por causa deles.
 *
 * <p>Os pools nascem com {@code minimumIdle = 0} (ver
 * {@code RoleDataSourceFactory}): criar o bean nao abre conexao; a
 * autenticacao so acontece no primeiro uso, com a credencial do papel.
 */
@Configuration
@ConditionalOnProperty(prefix = "ziipsan.db.pools", name = "enabled", havingValue = "true")
public class RolePoolConfig {

    private final String jdbcUrl;
    private final String batchUser;
    private final String batchPassword;
    private final int batchMaxPoolSize;
    private final String ingestUser;
    private final String ingestPassword;
    private final int ingestMaxPoolSize;

    public RolePoolConfig(
            @Value("${ziipsan.db.url}") String jdbcUrl,
            @Value("${ziipsan.db.batch.user:ziipsan_batch}") String batchUser,
            @Value("${ziipsan.db.batch.password:}") String batchPassword,
            @Value("${ziipsan.db.batch.max-pool-size:10}") int batchMaxPoolSize,
            @Value("${ziipsan.db.ingest.user:ziipsan_ingest}") String ingestUser,
            @Value("${ziipsan.db.ingest.password:}") String ingestPassword,
            @Value("${ziipsan.db.ingest.max-pool-size:5}") int ingestMaxPoolSize) {
        this.jdbcUrl = jdbcUrl;
        this.batchUser = batchUser;
        this.batchPassword = batchPassword;
        this.batchMaxPoolSize = batchMaxPoolSize;
        this.ingestUser = ingestUser;
        this.ingestPassword = ingestPassword;
        this.ingestMaxPoolSize = ingestMaxPoolSize;
    }

    @Bean(destroyMethod = "close")
    public HikariDataSource batchDataSource() {
        return RoleDataSourceFactory.create(PoolRole.BATCH, jdbcUrl,
                new RoleDataSourceFactory.RoleCredentials(batchUser, batchPassword), batchMaxPoolSize);
    }

    @Bean(destroyMethod = "close")
    public HikariDataSource ingestDataSource() {
        return RoleDataSourceFactory.create(PoolRole.INGEST, jdbcUrl,
                new RoleDataSourceFactory.RoleCredentials(ingestUser, ingestPassword), ingestMaxPoolSize);
    }
}
