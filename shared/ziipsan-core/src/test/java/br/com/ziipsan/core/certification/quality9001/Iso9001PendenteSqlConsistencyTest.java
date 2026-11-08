package br.com.ziipsan.core.certification.quality9001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência entre o SQL pendente da ED-69 e as réguas do código:
 * tabelas existem, enums do banco casam com os enums Java, travas
 * declaradas no Java têm eco no banco e NÃO há DELETE (desligar não
 * apaga registro).
 */
class Iso9001PendenteSqlConsistencyTest {

    private static String sql() throws Exception {
        try (InputStream in = Iso9001PendenteSqlConsistencyTest.class
                .getResourceAsStream("/db/pendente/ED-69__certification_quality_iso9001.sql")) {
            assertThat(in).as("SQL pendente da ED-69 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("as seis tabelas do pacote estão no SQL pendente e no RLS")
    void tablesExist() throws Exception {
        String sql = sql();
        for (String table : List.of("cert_q9001_processes", "cert_q9001_calibrations",
                "cert_q9001_nonconforming_outputs", "cert_q9001_clause_assessments",
                "cert_q9001_clause_evidence", "cert_q9001_readiness_items")) {
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
    @DisplayName("disposições do banco casam com o enum Disposition do código")
    void dispositionsMatch() throws Exception {
        String sql = sql();
        for (NonconformingOutput.Disposition d : NonconformingOutput.Disposition.values()) {
            assertThat(sql).contains("'" + d.name() + "'");
        }
    }

    @Test
    @DisplayName("conformidades do banco casam com o enum Conformity do código")
    void conformitiesMatch() throws Exception {
        String sql = sql();
        for (QualityClauseAssessment.Conformity c : QualityClauseAssessment.Conformity.values()) {
            assertThat(sql).contains("'" + c.name() + "'");
        }
    }

    @Test
    @DisplayName("saída não conforme tem FK para a ocorrência da P2.25 e validade da calibração tem trava")
    void foreignKeyAndCalibrationCheck() throws Exception {
        String sql = sql();
        assertThat(sql).contains("REFERENCES cert_occurrence(tenant_id, id)");
        assertThat(sql).contains("valid_until >= calibrated_on");
    }

    @Test
    @DisplayName("edições do checklist no banco são as do parâmetro: 2015 e 2026")
    void editionsMatch() throws Exception {
        assertThat(sql()).contains("edition IN ('2015', '2026')");
    }
}
