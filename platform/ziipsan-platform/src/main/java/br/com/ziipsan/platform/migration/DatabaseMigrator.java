package br.com.ziipsan.platform.migration;

import br.com.ziipsan.core.migration.CoreMigrationRunner;
import br.com.ziipsan.platform.tenant.TenantMigrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * Migrations do job {@code migrate}: core (schema public) e depois cada tenant ACTIVE do catálogo.
 *
 * <p>P0.6: este bean só existe no perfil {@code migrate} (decisão D10 do plano, agora
 * cumprida na forma: job separado antes da troca de versão). A api e o worker NÃO têm
 * este bean e nunca migram: a ordem é externa (job migrate antes da api) e o
 * {@code @DependsOn} em PlatformConfig foi removido junto. Ligado por
 * {@code ziipsan.db.migrate-on-startup} (o entrypoint do job passa {@code true});
 * desligado, o bean só registra que não migrou e o MigrateExitRunner sai do mesmo jeito.
 *
 * <p>Usa o DataSource de migração (ziipsan_migrator). O Flyway do Spring Boot fica desligado
 * ({@code spring.flyway.enabled=false}): com o flyway-core no classpath ele varreria
 * db/migration recursivamente e aplicaria as migrations de tenant em public.
 */
@Slf4j
@Component("databaseMigrator")
@Profile("migrate")
public class DatabaseMigrator implements InitializingBean {

    private final DataSource migratorDataSource;
    private final boolean enabled;

    public DatabaseMigrator(@Qualifier("migratorDataSource") DataSource migratorDataSource,
                            @Value("${ziipsan.db.migrate-on-startup:false}") boolean enabled) {
        this.migratorDataSource = migratorDataSource;
        this.enabled = enabled;
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            log.info("Migrations na subida desligadas (ziipsan.db.migrate-on-startup=false)");
            return;
        }
        new CoreMigrationRunner(migratorDataSource).run(null);

        // tenant_catalog não tem RLS (cadastro global da plataforma): o migrador lê sem contexto.
        List<TenantRow> tenants = new JdbcTemplate(migratorDataSource).query(
                "SELECT id, schema_name FROM public.tenant_catalog WHERE status = 'ACTIVE' ORDER BY schema_name",
                (rs, i) -> new TenantRow(rs.getObject(1, UUID.class), rs.getString(2)));
        TenantMigrator migrator = new TenantMigrator(migratorDataSource);
        for (TenantRow tenant : tenants) {
            var result = migrator.migrate(tenant.schema(), tenant.id());
            log.info("Tenant {}: {} migration(s) aplicada(s), versão {}",
                    tenant.schema(), result.migrationsExecuted, result.targetSchemaVersion);
        }
    }

    private record TenantRow(UUID id, String schema) {
    }
}
