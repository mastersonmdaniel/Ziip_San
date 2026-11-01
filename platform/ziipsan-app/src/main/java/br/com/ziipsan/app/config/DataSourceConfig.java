package br.com.ziipsan.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Os dois DataSources: o da aplicação (padrão, @Primary) e o de migração.
 *
 * <p>A aplicação nunca usa o de migração: ziipsan_migrator é dono das tabelas e pode desligar a RLS.
 * Ele só é injetado por nome ({@code @Qualifier("migratorDataSource")}) no DatabaseMigrator e no
 * provisionamento de tenant.
 *
 * <p>P0.6: o DataSource de migração só existe no perfil {@code migrate}. Api e worker
 * sobem sem a senha do migrador (ela nem é validada fora desse perfil) e sem o bean:
 * migração é um job separado, que roda ANTES da api (ver scripts/run-app.sh e o
 * entrypoint da imagem). Sem o perfil, não há bean para injetar nem indicador de
 * saúde de um DataSource que o processo não usa.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public HikariDataSource dataSource(ZiipsanProperties properties) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("ziipsan-app");
        ds.setJdbcUrl(properties.db().url());
        ds.setUsername(properties.db().appUser());
        ds.setPassword(properties.db().appPassword());
        // P0.9: pool da aplicacao dimensionado por processo e registrado na
        // ARQUITETURA-ALVO. Soma por processo (app 20 + migrador 2) cabe com folga
        // no max_connections=200 do postgres-primary, contando Keycloak e operacao.
        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(30_000);
        ds.setMaxLifetime(1_800_000);
        return ds;
    }

    @Bean
    @Profile("migrate")
    public HikariDataSource migratorDataSource(ZiipsanProperties properties,
                                               MigratorProperties migrator) {
        if (migrator.user() == null || migrator.user().isBlank()
                || migrator.password() == null || migrator.password().isBlank()) {
            throw new IllegalStateException(
                    "Perfil migrate sem credencial do migrador: defina ZIIPSAN_DB_MIGRATOR_USER "
                            + "e ZIIPSAN_DB_MIGRATOR_PASSWORD (a api/worker nao exigem essas variaveis)");
        }
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("ziipsan-migrator");
        ds.setJdbcUrl(properties.db().url());
        ds.setUsername(migrator.user());
        ds.setPassword(migrator.password());
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        return ds;
    }
}
