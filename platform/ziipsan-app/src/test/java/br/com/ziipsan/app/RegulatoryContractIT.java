package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ED-96 (GES-02): instrumento contratual regulatorio no PostgreSQL
 * real — as 7 tabelas reg_* com RLS, FKs compostas para cert_risks
 * (P3.31) e asset (P1.35/ED-56). Segue o padrao do Gis19AssetIT da
 * ED-56: migrations do classpath contra schema descartavel; o SQL
 * entra de db/pendente e recebe numero na integracao.
 *
 * <p>NAO RODADA nesta VM como IT Spring: pendencia nomeada
 * P-ED96-03 — prova da CI no PC do dono. Nesta VM os dois SQL foram
 * APLICADOS de verdade em banco descartavel com stubs declarados
 * (harness cascata-ed96, ver RELATORIO-ED-96) — parse nao substitui
 * aplicacao (licao da ED-92/ED-56).
 */
class RegulatoryContractIT {

    private static final List<String> LEGACY_MIGRATIONS = List.of(
            "db/migration/tenant/V1_1__create_tenant_schema.sql",
            "db/migration/tenant/V1_2__tenant_table_helper.sql",
            "db/migration/tenant/V1_3__harden_audit_function.sql",
            "db/migration/tenant/V1_5__tenant_parameters.sql",
            "db/migration/tenant/V21_3__certification_governance.sql");

    private static final List<String> ED96_MIGRATIONS = List.of(
            // P1.35 (asset) entra aqui na integracao, antes da ED-96:
            "db/pendente/ED-96__regulatory_contract.sql");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private static String sql(String resource, UUID tenant) {
        try (InputStream in = RegulatoryContractIT.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("migration ausente no classpath: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${tenant_id}", tenant.toString());
        } catch (IOException e) {
            throw new IllegalStateException("falha lendo " + resource, e);
        }
    }

    @Test
    @DisplayName("ED-96: tabelas do instrumento existem com RLS")
    void regulatoryTablesExistWithRls() throws Exception {
        try (Connection c = ProvisionedTenant.dataSource().getConnection();
             Statement st = c.createStatement()) {
            UUID tenant = UUID.randomUUID();
            String schema = "it_reg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            st.execute("CREATE SCHEMA " + schema);
            st.execute("SET LOCAL search_path = " + schema + ", public");
            for (String m : LEGACY_MIGRATIONS) st.execute(sql(m, tenant));
            for (String m : ED96_MIGRATIONS) st.execute(sql(m, tenant));
            for (String table : List.of("reg_contracts", "reg_contract_goals", "reg_contract_amendments",
                    "reg_contract_documents", "reg_contract_risk_matrix",
                    "reg_contract_reversible_assets", "reg_financial_capacity_snapshots")) {
                try (ResultSet rs = st.executeQuery(
                        "SELECT relrowsecurity FROM pg_class WHERE relname = '" + table + "'")) {
                    assertThat(rs.next()).as(table).isTrue();
                    assertThat(rs.getBoolean(1)).as("RLS de " + table).isTrue();
                }
            }
        }
    }
}
