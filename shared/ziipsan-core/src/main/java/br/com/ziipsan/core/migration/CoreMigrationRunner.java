package br.com.ziipsan.core.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import javax.sql.DataSource;

/**
 * Executa as migrations de core (schema public) na subida da aplicação.
 *
 * Sem isto, public.tenant_catalog e public.audit_log nunca eram criadas: a migration
 * V1__create_core_schema.sql existia no classpath, mas nenhuma classe a executava. Em
 * desenvolvimento os testes criavam as tabelas na mão, o que escondia o buraco — o primeiro
 * provisionamento de tenant em um banco recém-criado falharia com
 * "relation tenant_catalog does not exist".
 *
 * A migration roda com a role de migracao, porque DDL exige privilégio que a ziipsan_app
 * deliberadamente não tem (NOCREATEDB).
 *
 * <p>Não é mais um bean que roda sozinho: na aplicação quem a chama é o DatabaseMigrator (platform),
 * antes do EntityManagerFactory, com o DataSource de migração. Como ApplicationRunner com injeção por
 * tipo, ela recebia o DataSource da aplicação (@Primary) e rodaria o DDL como ziipsan_app.
 */
@Slf4j
@RequiredArgsConstructor
public class CoreMigrationRunner implements ApplicationRunner {

    /**
     * Só o diretório core. O Flyway varre o location de forma recursiva, então apontar para
     * "classpath:db/migration" faria esta execução (schema public) aplicar também as migrations
     * de tenant que vivem em db/migration/tenant, criando users, roles e service_orders em public.
     */
    private static final String CORE_MIGRATIONS_LOCATION = "classpath:db/migration/core";

    private final DataSource coreMigratorDataSource;

    @Override
    public void run(ApplicationArguments args) {
        Flyway flyway = Flyway.configure()
                .dataSource(coreMigratorDataSource)
                .schemas("public")
                .defaultSchema("public")
                .locations(CORE_MIGRATIONS_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                // P0.19: as versoes reservadas do core (V3, V4, ...) nao seguem a ordem
                // de aplicacao — a V5 ja foi aplicada antes da V3 existir. Sem outOfOrder,
                // um banco existente recusaria a V3. Mesma decisao do TenantMigrator (G10a).
                .outOfOrder(true)
                .table("flyway_schema_history")
                .load();

        var result = flyway.migrate();

        if (result.migrationsExecuted > 0) {
            log.info("Migrations de core aplicadas: {} (versao atual {})",
                    result.migrationsExecuted, result.targetSchemaVersion);
        } else {
            log.info("Schema public ja atualizado (versao {})", result.targetSchemaVersion);
        }
    }
}
