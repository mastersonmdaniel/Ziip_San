package br.com.ziipsan.reporting.indicator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia da V15_2 (a migration reservada a P3.41 no plano secao 3)
 * com o desenho do motor: tabelas, travas de fonte, fechamento unico e as
 * colunas da base das entidades do core.
 */
class IndicatorMigrationConsistencyTest {

    private String migration() throws Exception {
        Path path = Path.of("src/main/resources/db/migration/tenant/V15_2__indicator_registry.sql");
        if (!Files.exists(path)) {
            path = Path.of("intelligence/ziipsan-reporting").resolve(path);
        }
        return Files.readString(path);
    }

    @Test
    void allRegistryTablesExistAndAreRegistered() throws Exception {
        String sql = migration();
        for (String table : List.of("indicator_definitions", "indicator_variables",
                "indicator_formula_versions", "indicator_targets", "indicator_closings",
                "indicator_audit_trail")) {
            assertThat(sql).as("tabela %s", table).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).as("registro RLS de %s", table)
                    .contains("register_tenant_table('" + table + "'");
        }
    }

    @Test
    void variableSourceIsLockedInTheDatabase() throws Exception {
        String sql = migration();
        // Sem fonte, sem linha — mesmo contornando o servico:
        assertThat(sql).contains("source_type = 'REPORT_VIEW' AND report_view IS NOT NULL");
        assertThat(sql).contains("source_type = 'MANUAL' AND manual_responsible_id IS NOT NULL");
        assertThat(sql).contains("report_view LIKE 'rpt\\_%'");
    }

    @Test
    void closingIsUniqueWhileClosedAndReopenNeedsReason() throws Exception {
        String sql = migration();
        assertThat(sql).contains("uq_indicator_closings_closed_key");
        assertThat(sql).contains("WHERE status = 'CLOSED'");
        assertThat(sql).contains("status <> 'REOPENED' OR (reopen_reason IS NOT NULL");
    }

    @Test
    void tablesCarryTheCoreBaseColumns() throws Exception {
        String sql = migration();
        for (String block : sql.split("CREATE TABLE IF NOT EXISTS ")) {
            if (block.startsWith("indicator_")) {
                assertThat(block).contains("version BIGINT")
                        .contains("tenant_schema")
                        .contains("created_by")
                        .contains("updated_by");
            }
        }
    }

    @Test
    void enumsInSqlMatchTheJavaEnums() throws Exception {
        String sql = migration();
        for (IndicatorPeriodicity p : IndicatorPeriodicity.values()) {
            assertThat(sql).contains("'" + p.name() + "'");
        }
        for (ConfidenceGrade g : ConfidenceGrade.values()) {
            assertThat(sql).contains("'" + g.name() + "'");
        }
        for (Semaphore s : Semaphore.values()) {
            assertThat(sql).contains("'" + s.name() + "'");
        }
        for (IndicatorTarget.Direction d : IndicatorTarget.Direction.values()) {
            assertThat(sql).contains("'" + d.name() + "'");
        }
    }
}
