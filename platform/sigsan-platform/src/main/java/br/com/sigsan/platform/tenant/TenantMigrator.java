package br.com.sigsan.platform.tenant;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

/**
 * Aplica as migrations de tenant (db/migration/tenant, todos os módulos) num schema.
 *
 * <p>Usado no provisionamento de um tenant novo e na subida da aplicação, para levar os tenants
 * existentes à versão atual. Fica fora do {@link TenantProvisioningService} porque o migrador de
 * subida roda antes do EntityManagerFactory e não pode depender de nada que dependa dele.
 */
public final class TenantMigrator {

    /** Só o diretório de tenant: o Flyway varre o location de forma recursiva. */
    public static final String LOCATION = "db/migration/tenant";

    private final DataSource migratorDataSource;

    public TenantMigrator(DataSource migratorDataSource) {
        this.migratorDataSource = migratorDataSource;
    }

    public MigrateResult migrate(String schemaName, UUID tenantId) {
        // defaultSchema define onde o Flyway cria a tabela de histórico. As extensões
        // (uuid-ossp, pgcrypto) vivem em public, e as migrations chamam uuid_generate_v4(),
        // então o search_path precisa incluir public junto do schema do tenant. Sem isso,
        // toda migration de tenant falha com "function uuid_generate_v4() does not exist".
        //
        // O placeholder tenant_id permite que as migrations gravem ou verifiquem com o tenant
        // correto: dentro de uma migration não há contexto de tenant (a GUC app.current_tenant
        // está vazia), então current_tenant_id() devolve NULL. O checksum do Flyway é
        // calculado sobre o texto original, antes da substituição, então o valor não o altera.
        Flyway flyway = Flyway.configure()
                .dataSource(migratorDataSource)
                .schemas(schemaName, "public")
                .defaultSchema(schemaName)
                .createSchemas(false)
                .placeholders(Map.of("tenant_id", tenantId.toString()))
                .locations(LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                // Cada modulo tem sua faixa de versao (V1_x platform, V3_x registry, V6_x collection...)
                // e todas vao para o mesmo historico por tenant. Uma migration nova numa faixa baixa
                // (ex.: V1_3) chega depois de V6_1 ja aplicada: sem outOfOrder o Flyway a recusa num
                // tenant existente. Decisao G10a do plano de 12/10/2026.
                .outOfOrder(true)
                .table("flyway_schema_history")
                .load();
        return flyway.migrate();
    }
}
