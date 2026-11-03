package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Colunas das migrations desta frente no banco real, depois do provisionamento
 * (que roda core + todas as migrations de tenant):
 * core V14_1 (P1.14: situação do rollout por tenant) e tenant V2_11 (P1.15:
 * exclusão lógica do lgpd).
 */
class P1PlatDataMigrationColumnsTest {

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private Set<String> columns(String schema, String table) throws Exception {
        Set<String> found = new HashSet<>();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT column_name FROM information_schema.columns "
                             + "WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.add(rs.getString(1));
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("core V14_1: catálogo tem situação, erro e data da tentativa por tenant")
    void coreV14_1ColumnsExist() throws Exception {
        assertThat(columns("public", "tenant_catalog"))
                .contains("migration_status", "migration_error", "migration_attempted_at");
    }

    @Test
    @DisplayName("tenant V2_11: as quatro tabelas do lgpd têm deleted_at")
    void tenantV2_11ColumnsExist() throws Exception {
        String schema = ProvisionedTenant.SCHEMA;
        for (String table : new String[]{"lgpd_consent", "lgpd_data_subject_request",
                "lgpd_dpia", "lgpd_retention_schedule"}) {
            assertThat(columns(schema, table)).as("tabela %s", table).contains("deleted_at");
        }
    }
}
