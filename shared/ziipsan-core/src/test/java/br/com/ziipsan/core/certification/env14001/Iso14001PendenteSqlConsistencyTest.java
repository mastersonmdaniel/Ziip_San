package br.com.ziipsan.core.certification.env14001;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia do SQL pendente da ED-70 com o nucleo: tabelas,
 * isolamento, ausencia de DELETE e de numeros reservados, trava do
 * titulo literal e NENHUMA tabela de lodo/residuo (o lodo e da
 * ED-105 — duplicar seria violar o aceite).
 */
class Iso14001PendenteSqlConsistencyTest {

    private String sql() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/db/pendente/ED-70__certification_env_iso14001.sql")) {
            assertThat(in).as("SQL pendente da ED-70 no classpath de teste").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void sqlDeclaresAllTablesWithTenantIsolation() throws Exception {
        String sql = sql();
        for (String table : new String[] {"cert_e14001_aspects",
                "cert_e14001_significance_criteria", "cert_e14001_significance_evaluations",
                "cert_e14001_licenses", "cert_e14001_license_conditions",
                "cert_e14001_license_evidence", "cert_e14001_emergency_scenarios",
                "cert_e14001_clause_assessments", "cert_e14001_clause_evidence"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
            assertThat(sql).contains("UNIQUE (tenant_id, id)");
        }
    }

    @Test
    void sqlHasNoDeleteNoReservedFlywayNumbersAndNoSludgeTable() throws Exception {
        String sql = sql();
        String upper = sql.toUpperCase();
        assertThat(upper).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("V102").doesNotContain("V103").doesNotContain("V6_4");
        // O comentario do cabecalho CITA a tabela da ED-105 como fronteira;
        // a invariante e nao CRIAR tabela de residuo/lodo neste pacote:
        assertThat(upper).doesNotContain("CREATE TABLE IF NOT EXISTS SLUDGE")
                .doesNotContain("CREATE TABLE IF NOT EXISTS CERT_E14001_SLUDGE");
    }

    @Test
    void sqlLocksLiteralTitleAndSituationEnums() throws Exception {
        String sql = sql();
        assertThat(sql).contains("literal_title = '[A DEFINIR]'");
        assertThat(sql).contains("'NORMAL', 'ABNORMAL', 'EMERGENCY'");
        assertThat(sql).contains("'WTP', 'WWTP', 'NETWORK'");
    }
}
