package br.com.ziipsan.platform.tenant;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

/**
 * Aplica as migrations de tenant (db/migration/tenant, todos os módulos) num schema.
 *
 * <p>Usado no provisionamento de um tenant novo e pelo job {@code migrate}
 * (via {@code DatabaseMigrator}/{@code TenantMigrationRollout}), para levar os
 * tenants existentes à versão atual. Fica fora do {@link TenantProvisioningService}
 * porque o migrador roda antes do EntityManagerFactory e não pode depender de
 * nada que dependa dele. A api e o worker nunca chamam esta classe (P0.6).
 *
 * <p>P3.12 — fim do {@code outOfOrder} (17:D1; G10a; COD-14): até a P3.12 cada
 * módulo tinha sua faixa de versão (V1_x platform, V3_x registry, V6_x
 * collection...) no mesmo histórico por tenant, e uma migration nova numa
 * faixa baixa chegava depois de versões maiores já aplicadas — daí o
 * {@code outOfOrder(true)} da decisão G10a. Com as últimas faixas publicadas
 * (V15_1 da P3.22, V6_5 da P3.29, V1_8 da P3.30 e V21_3 da P3.31), o
 * {@code outOfOrder} é retirado nesta tarefa: daqui em diante as migrations de
 * tenant seguem uma sequência global única a partir de {@code V100} (ver
 * {@link TenantMigrationVersionPolicy}), sempre crescente, e o Flyway volta a
 * recusar/ignorar versão menor ou igual à já aplicada em vez de aplicá-la
 * fora de ordem. Pré-condição da virada, registrada para a integração: todo
 * tenant existente precisa estar com todas as migrations de faixa aplicadas
 * (job {@code migrate} verde) antes desta versão entrar — uma migration de
 * faixa pendente num tenant já além dela deixaria de ser aplicada.
 */
public final class TenantMigrator {

    /** Só o diretório de tenant: o Flyway varre o location de forma recursiva. */
    public static final String LOCATION = "db/migration/tenant";

    private final DataSource migratorDataSource;

    public TenantMigrator(DataSource migratorDataSource) {
        this.migratorDataSource = migratorDataSource;
    }

    public MigrateResult migrate(String schemaName, UUID tenantId) {
        return flyway(schemaName, tenantId).migrate();
    }

    /**
     * P1.14: versão Flyway corrente do schema (o que o {@code flyway info} imprime
     * como "Schema version"), ou {@code null} se nenhuma migration versionada foi
     * aplicada. O {@code DatabaseMigrator} usa como fallback quando
     * {@code migrate()} não aplicou nada: nesse caso o {@code MigrateResult} não
     * traz {@code targetSchemaVersion} (observado no IT: tenant atualizado saía
     * com {@code tenant_schema_version} NULL mesmo com o rollout SUCCEEDED) e o
     * catálogo ficaria sem a versão de um tenant atualizado. Só leitura do
     * histórico — não valida checksums nem aplica nada — e só é chamado para
     * tenants cuja migração concluiu.
     */
    public String currentVersion(String schemaName, UUID tenantId) {
        var current = flyway(schemaName, tenantId).info().current();
        return current == null || current.getVersion() == null ? null : current.getVersion().getVersion();
    }

    private Flyway flyway(String schemaName, UUID tenantId) {
        // defaultSchema define onde o Flyway cria a tabela de histórico. As extensões
        // (uuid-ossp, pgcrypto) vivem em public, e as migrations chamam uuid_generate_v4(),
        // então o search_path precisa incluir public junto do schema do tenant. Sem isso,
        // toda migration de tenant falha com "function uuid_generate_v4() does not exist".
        //
        // O placeholder tenant_id permite que as migrations gravem ou verifiquem com o tenant
        // correto: dentro de uma migration não há contexto de tenant (a GUC app.current_tenant
        // está vazia), então current_tenant_id() devolve NULL. O checksum do Flyway é
        // calculado sobre o texto original, antes da substituição, então o valor não o altera.
        return Flyway.configure()
                .dataSource(migratorDataSource)
                .schemas(schemaName, "public")
                .defaultSchema(schemaName)
                .createSchemas(false)
                .placeholders(Map.of("tenant_id", tenantId.toString()))
                .locations(LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                // P3.12: outOfOrder DESLIGADO (era true pela G10a, enquanto havia
                // faixa de módulo pendente). Explícito, e não só removido, para a
                // virada ficar auditável no código e no teste de CI
                // (TenantMigrationGlobalSequenceCiTest recusa outOfOrder(true)
                // nesta classe). Sequência global a partir de V100:
                // TenantMigrationVersionPolicy.
                .outOfOrder(false)
                .table("flyway_schema_history")
                .load();
    }
}
