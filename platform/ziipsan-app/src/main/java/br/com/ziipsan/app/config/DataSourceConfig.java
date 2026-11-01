package br.com.ziipsan.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Os dois DataSources: o da aplicação (padrão, @Primary) e o de migração.
 *
 * <p>A aplicação nunca usa o de migração: ziipsan_migrator é dono das tabelas e pode desligar a RLS.
 * Ele só é injetado por nome ({@code @Qualifier("migratorDataSource")}) no DatabaseMigrator e no
 * provisionamento de tenant.
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
        return ds;
    }

    @Bean
    public HikariDataSource migratorDataSource(ZiipsanProperties properties) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("ziipsan-migrator");
        ds.setJdbcUrl(properties.db().url());
        ds.setUsername(properties.db().migratorUser());
        ds.setPassword(properties.db().migratorPassword());
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        return ds;
    }
}
