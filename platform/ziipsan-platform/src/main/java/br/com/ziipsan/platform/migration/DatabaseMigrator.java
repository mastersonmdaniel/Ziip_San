package br.com.ziipsan.platform.migration;

import br.com.ziipsan.core.migration.CoreMigrationRunner;
import br.com.ziipsan.platform.deployment.DeploymentEnvironment;
import br.com.ziipsan.platform.tenant.TenantMigrationRollout;
import br.com.ziipsan.platform.tenant.TenantMigrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
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
 *
 * <p>P1.14: o laço sequencial sobre os tenants foi substituído pelo
 * {@link TenantMigrationRollout} (canário + paralelismo limitado + quarentena).
 * Esta classe só orquestra: lê o catálogo, delega, atualiza
 * {@code tenant_schema_version} nos tenants que concluíram e só registra a
 * implantação quando TODOS concluíram — a semântica da P0.42 está preservada.
 * O paralelismo vem de {@code ziipsan.db.migration-parallelism} (padrão 2, igual
 * ao pool do migrador; nunca configure acima dele, ver TenantMigrationRollout).
 *
 * <p>P3.12: este é o ÚNICO caminho de aplicação de migrations de tenant —
 * job {@code migrate}, em ondas (canário, depois lotes de no máximo o
 * paralelismo configurado), com quarentena do tenant cuja migração falha sem
 * bloquear os demais. Não existe mais aplicação em sequência na subida: a api
 * e o worker não têm este bean (P0.6) e nenhum outro bean migra tenant. Na
 * mesma tarefa o {@code TenantMigrator} perdeu o {@code outOfOrder(true)}:
 * a sequência de tenant é global, a partir de V100.
 */
@Slf4j
@Component("databaseMigrator")
@Profile("migrate")
public class DatabaseMigrator implements InitializingBean {

    private final DataSource migratorDataSource;
    private final boolean enabled;
    private final int parallelism;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<GitProperties> gitProperties;
    private final Environment environment;
    private final String configuredEnvironment;

    public DatabaseMigrator(@Qualifier("migratorDataSource") DataSource migratorDataSource,
                            @Value("${ziipsan.db.migrate-on-startup:false}") boolean enabled,
                            @Value("${ziipsan.db.migration-parallelism:2}") int parallelism,
                            ObjectProvider<BuildProperties> buildProperties,
                            ObjectProvider<GitProperties> gitProperties,
                            Environment environment,
                            @Value("${ziipsan.deployment.environment:}") String configuredEnvironment) {
        this.migratorDataSource = migratorDataSource;
        this.enabled = enabled;
        this.parallelism = parallelism;
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
        this.environment = environment;
        this.configuredEnvironment = configuredEnvironment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            log.info("Migrations na subida desligadas (ziipsan.db.migrate-on-startup=false)");
            return;
        }
        Instant startedAt = Instant.now();
        new CoreMigrationRunner(migratorDataSource).run(null);

        // tenant_catalog não tem RLS (cadastro global da plataforma): o migrador lê sem contexto.
        List<TenantRow> tenants = new JdbcTemplate(migratorDataSource).query(
                "SELECT id, schema_name FROM public.tenant_catalog WHERE status = 'ACTIVE' ORDER BY schema_name",
                (rs, i) -> new TenantRow(rs.getObject(1, UUID.class), rs.getString(2)));
        TenantMigrator migrator = new TenantMigrator(migratorDataSource);
        JdbcTemplate jdbc = new JdbcTemplate(migratorDataSource);
        // P1.14: canário + paralelismo limitado + quarentena, com a situação gravada
        // por tenant no catálogo (core V14_1). A versão aplicada (tenant_schema_version)
        // continua sendo atualizada aqui, só nos tenants que concluíram — um tenant em
        // quarentena mantém a versão antiga, como antes da P1.14.
        TenantMigrationRollout rollout = new TenantMigrationRollout(jdbc,
                (schema, tenantId) -> {
                    var result = migrator.migrate(schema, tenantId);
                    // P1.14: tenant já atualizado tem migrationsExecuted=0 e o
                    // MigrateResult não traz targetSchemaVersion — sem o fallback o
                    // catálogo ficaria NULL mesmo com o tenant atualizado (falha
                    // observada no IT do rollout). A versão corrente via info()
                    // é a versão Flyway do schema, na ordenação do próprio Flyway.
                    String target = result.targetSchemaVersion != null
                            ? result.targetSchemaVersion
                            : migrator.currentVersion(schema, tenantId);
                    return new TenantMigrationRollout.MigrationOutcome(
                            result.migrationsExecuted, target);
                },
                parallelism);
        TenantMigrationRollout.RolloutResult rolloutResult = rollout.run(
                tenants.stream()
                        .map(t -> new TenantMigrationRollout.Tenant(t.id(), t.schema()))
                        .toList());
        for (TenantMigrationRollout.TenantResult succeeded : rolloutResult.succeeded()) {
            jdbc.update(
                    "UPDATE public.tenant_catalog SET tenant_schema_version = ? WHERE id = ?",
                    succeeded.targetSchemaVersion(), succeeded.tenant().id());
            log.info("Tenant {}: {} migration(s) aplicada(s), versão {}",
                    succeeded.tenant().schemaName(), succeeded.migrationsExecuted(),
                    succeeded.targetSchemaVersion());
        }
        if (!rolloutResult.quarantined().isEmpty() || rolloutResult.abortedAfterCanary()) {
            // P0.42 preservada: implantação NÃO é registrada como concluída quando a
            // onda não concluiu para todos. O job falha (exceção) para o operador ver,
            // mas os tenants que concluíram ficam migrados — a falha de um não desfez
            // nem bloqueou os demais, que é a garantia da P1.14.
            String quarantined = rolloutResult.quarantined().stream()
                    .map(r -> r.tenant().schemaName())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalStateException("Rollout de migrations incompleto"
                    + (rolloutResult.abortedAfterCanary() ? " (abortado após falha do canário)" : "")
                    + ": tenant(s) em quarentena: " + quarantined);
        }
        // P0.42: cada execução bem-sucedida do job grava uma linha em
        // platform.deployment (core V5_1). Só aqui, e só no fim: se qualquer
        // migração acima falhar, a exceção interrompe antes do INSERT e nenhuma
        // implantação é registrada como concluída. Versão/commit vêm do artefato
        // (build-info/git.properties) quando presentes; sem eles, "dev"/nulo —
        // nunca um valor inventado.
        recordDeployment(startedAt, Instant.now());
    }

    private void recordDeployment(Instant startedAt, Instant finishedAt) {
        BuildProperties build = buildProperties.getIfAvailable();
        GitProperties git = gitProperties.getIfAvailable();
        String version = build != null && build.getVersion() != null ? build.getVersion() : "dev";
        String commit = git != null ? git.getShortCommitId() : null;
        String env = DeploymentEnvironment.resolve(configuredEnvironment,
                environment != null ? environment.getActiveProfiles() : null);
        new JdbcTemplate(migratorDataSource).update(
                "INSERT INTO platform.deployment (version, commit_hash, environment, started_at, finished_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                version, commit, env, Timestamp.from(startedAt), Timestamp.from(finishedAt));
        log.info("Implantação registrada em platform.deployment: versão {} ambiente {}", version, env);
    }

    private record TenantRow(UUID id, String schema) {
    }
}
