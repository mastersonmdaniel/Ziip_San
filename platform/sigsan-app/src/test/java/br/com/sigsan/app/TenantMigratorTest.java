package br.com.sigsan.app;

import br.com.sigsan.platform.tenant.TenantMigrator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migrar um tenant já existente (o que o DatabaseMigrator faz na subida) é idempotente: rodar de novo
 * não aplica nada e não falha.
 */
class TenantMigratorTest {

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    @Test
    @DisplayName("tenant já na versão atual: nova migração não aplica nada")
    void migratingAnUpToDateTenantIsANoOp() {
        var result = new TenantMigrator(ProvisionedTenant.migratorDs).migrate(ProvisionedTenant.SCHEMA, ProvisionedTenant.TENANT_ID);
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isZero();
    }
}
