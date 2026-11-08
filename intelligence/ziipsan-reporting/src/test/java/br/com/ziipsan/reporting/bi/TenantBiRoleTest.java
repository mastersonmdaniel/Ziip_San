package br.com.ziipsan.reporting.bi;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-47: a role do tenant é determinística, única por tenant, um
 * identificador PostgreSQL válido e sem nenhum dado do tenant/pessoa no
 * nome (só o UUID, que é o identificador interno do tenant).
 */
class TenantBiRoleTest {

    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void roleDerivadaDoTenant() {
        assertThat(TenantBiRole.roleNameFor(A)).isEqualTo("bi_11111111111111111111111111111111");
        assertThat(TenantBiRole.roleNameFor(A)).isEqualTo(TenantBiRole.roleNameFor(A));
        assertThat(TenantBiRole.roleNameFor(A)).isNotEqualTo(TenantBiRole.roleNameFor(B));
        assertThat(TenantBiRole.roleNameFor(A)).matches("[a-z][a-z0-9_]{0,62}");
    }

    @Test
    void mapeamentoRecusaRoleDeOutroTenant() {
        assertThatThrownBy(() -> new TenantBiRole(A, TenantBiRole.roleNameFor(B),
                TenantBiRole.IsolationMode.SHARED_SCHEMA, "tenant_shared"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role do tenant");
    }

    @Test
    void mapeamentoRecusaSchemaForaDoPadrao() {
        assertThatThrownBy(() -> TenantBiRole.of(A,
                TenantBiRole.IsolationMode.DEDICATED_SCHEMA, "Tenant A; DROP TABLE x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantBiRole.of(A,
                TenantBiRole.IsolationMode.DEDICATED_SCHEMA, "schema-com-hifen"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schemaDeAcessoPorTenant() {
        TenantBiRole mapping = TenantBiRole.of(A, TenantBiRole.IsolationMode.SHARED_SCHEMA, "tenant_shared");
        assertThat(mapping.accessSchema()).isEqualTo("bi_11111111111111111111111111111111");
        assertThat(mapping.accessSchema()).isNotEqualTo(
                TenantBiRole.of(B, TenantBiRole.IsolationMode.SHARED_SCHEMA, "tenant_shared").accessSchema());
    }
}
