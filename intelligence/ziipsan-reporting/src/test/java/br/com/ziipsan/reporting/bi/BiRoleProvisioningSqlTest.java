package br.com.ziipsan.reporting.bi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-47: o SQL de provisionamento é a prova em texto do modelo:
 * identidade por current_user (nunca pela GUC), grant só na camada do
 * tenant, role sem LOGIN e sem senha, e nada de grant em tabela-base.
 */
class BiRoleProvisioningSqlTest {

    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final TenantBiRole MAPPING = TenantBiRole.of(
            A, TenantBiRole.IsolationMode.SHARED_SCHEMA, "tenant_shared");
    private static final List<String> VIEWS = List.of("rpt_arrecadacao_diaria", "rpt_contas_emitidas");

    @Test
    void objetosCompartilhadosResolvemTenantPelaConexaoENaoPelaGuc() {
        String all = String.join("\n", BiRoleProvisioningSql.sharedObjectsSql());
        assertThat(all).contains("reporting_bi_role_tenant");
        assertThat(all).contains("current_user");
        // A funcao de contexto NAO le a GUC: SET app.current_tenant nao pode
        // mover a identidade da conexao (coracao do teste de isolamento).
        assertThat(all).doesNotContain("current_setting");
        assertThat(all).doesNotContain("app.current_tenant");
    }

    @Test
    void provisionamentoCriaRoleSemLoginESemSenha() {
        String all = String.join("\n", BiRoleProvisioningSql.provisionSql(MAPPING, VIEWS));
        assertThat(all).contains("CREATE ROLE bi_11111111111111111111111111111111 NOLOGIN");
        assertThat(all).contains("NOSUPERUSER");
        assertThat(all).doesNotContain("PASSWORD");
    }

    @Test
    void grantsSoNaCamadaDoProprioTenant() {
        List<String> sql = BiRoleProvisioningSql.provisionSql(MAPPING, VIEWS);
        String all = String.join("\n", sql);
        assertThat(all).contains("GRANT SELECT ON bi_11111111111111111111111111111111"
                + ".bi_rpt_arrecadacao_diaria TO bi_11111111111111111111111111111111");
        assertThat(all).contains("WHERE tenant_id = public.bi_current_tenant_id()");
        for (String statement : sql) {
            String grant = statement.toUpperCase(java.util.Locale.ROOT);
            if (grant.startsWith("GRANT SELECT")) {
                // Nenhum SELECT fora da camada bi_<tenant>.bi_rpt_*:
                // nem na view crua da P3.22, nem em tabela-base.
                assertThat(statement).contains(".bi_rpt_");
            }
        }
        assertThat(all).doesNotContain("GRANT SELECT ON tenant_shared.rpt_");
        assertThat(all).doesNotContain("GRANT ALL");
    }

    @Test
    void viewForaDoPadraoEhRecusada() {
        assertThatThrownBy(() -> BiRoleProvisioningSql.provisionSql(
                MAPPING, List.of("payments; DROP TABLE payments")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BiRoleProvisioningSql.provisionSql(MAPPING, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void desprovisionamentoRevogaETiraOMapeamento() {
        String all = String.join("\n", BiRoleProvisioningSql.deprovisionSql(MAPPING, VIEWS));
        assertThat(all).contains("REVOKE SELECT ON bi_11111111111111111111111111111111.bi_rpt_arrecadacao_diaria");
        assertThat(all).contains("DELETE FROM public.reporting_bi_role_tenant");
        assertThat(all).contains("NOLOGIN");
        // Desprovisionar nao apaga a role nem o schema (historico/forense).
        assertThat(all).doesNotContain("DROP ROLE");
        assertThat(all).doesNotContain("DROP SCHEMA");
    }
}
