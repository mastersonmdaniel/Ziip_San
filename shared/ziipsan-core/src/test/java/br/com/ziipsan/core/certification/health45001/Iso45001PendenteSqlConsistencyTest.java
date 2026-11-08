package br.com.ziipsan.core.certification.health45001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Iso45001PendenteSqlConsistencyTest {

    private static String sql() throws Exception {
        try (InputStream in = Iso45001PendenteSqlConsistencyTest.class
                .getResourceAsStream("/db/pendente/ED-71__certification_health_iso45001.sql")) {
            assertThat(in).as("SQL pendente da ED-71 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("as nove tabelas do pacote estão no SQL pendente e no RLS")
    void tablesExist() throws Exception {
        String sql = sql();
        for (String table : List.of("cert_h45001_hazards", "cert_h45001_os_assessments",
                "cert_h45001_os_qualifications", "cert_h45001_ppe_deliveries",
                "cert_h45001_accident_investigations", "cert_h45001_accident_health_data",
                "cert_h45001_health_access_log", "cert_h45001_clause_assessments",
                "cert_h45001_clause_evidence")) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
    }

    @Test
    @DisplayName("sem DELETE nem DROP: desligar a norma não apaga registro")
    void noDelete() throws Exception {
        String upper = sql().toUpperCase();
        assertThat(upper).doesNotContain("DELETE FROM").doesNotContain("DROP TABLE")
                .doesNotContain("TRUNCATE");
    }

    @Test
    @DisplayName("enums do banco casam com os enums Java")
    void enumsMatch() throws Exception {
        String sql = sql();
        for (HazardRiskEntry.Category c : HazardRiskEntry.Category.values()) {
            assertThat(sql).contains("'" + c.name() + "'");
        }
        for (OsSafetyGate.Channel c : OsSafetyGate.Channel.values()) {
            assertThat(sql).contains("'" + c.name() + "'");
        }
        for (AccidentInvestigation.Severity s : AccidentInvestigation.Severity.values()) {
            assertThat(sql).contains("'" + s.name() + "'");
        }
        for (OhsClauseAssessment.Conformity c : OhsClauseAssessment.Conformity.values()) {
            assertThat(sql).contains("'" + c.name() + "'");
        }
        for (OhsClauseAssessment.EvidenceKind k : OhsClauseAssessment.EvidenceKind.values()) {
            assertThat(sql).contains("'" + k.name() + "'");
        }
    }

    @Test
    @DisplayName("toda tabela nova tem UNIQUE (tenant_id, id) e as FKs compostas apontam para pais reais")
    void compositeForeignKeys() throws Exception {
        String sql = sql();
        for (String constraint : List.of("uq_h45001_hazards_tenant_id",
                "uq_h45001_os_assessments_tenant_id", "uq_h45001_os_qualifications_tenant_id",
                "uq_h45001_ppe_deliveries_tenant_id", "uq_h45001_investigations_tenant_id",
                "uq_h45001_health_data_tenant_id", "uq_h45001_health_access_log_tenant_id",
                "uq_h45001_clause_assessments_tenant_id", "uq_h45001_clause_evidence_tenant_id")) {
            assertThat(sql).contains(constraint + " UNIQUE (tenant_id, id)");
        }
        // Pais externos com UNIQUE (tenant_id, id) nas suas migrations:
        // org_units (V1_8/P3.30), service_orders (V9_1/P1.31), cert_occurrence (V21_2/P2.25).
        assertThat(sql).contains("REFERENCES org_units(tenant_id, id)")
                .contains("REFERENCES service_orders(tenant_id, id)")
                .contains("REFERENCES cert_occurrence(tenant_id, id)")
                .contains("REFERENCES cert_h45001_os_assessments(tenant_id, id)")
                .contains("REFERENCES cert_h45001_accident_investigations(tenant_id, id)")
                .contains("REFERENCES cert_h45001_clause_assessments(tenant_id, id)");
    }

    @Test
    @DisplayName("nenhum título literal de cláusula é semeado: só o marcador [A DEFINIR], travado")
    void noLiteralClauseTitles() throws Exception {
        String sql = sql();
        assertThat(sql).contains("CHECK (literal_title = '[A DEFINIR]')");
        assertThat(sql).doesNotContain("INSERT INTO");
    }

    @Test
    @DisplayName("dado de saúde mora em tabela própria, separada da investigação")
    void healthDataSeparated() throws Exception {
        String sql = sql();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS cert_h45001_accident_health_data");
        String investigations = sql.substring(
                sql.indexOf("CREATE TABLE IF NOT EXISTS cert_h45001_accident_investigations"),
                sql.indexOf("CREATE TABLE IF NOT EXISTS cert_h45001_accident_health_data"));
        assertThat(investigations.toLowerCase()).doesNotContain("detail");
    }
}
