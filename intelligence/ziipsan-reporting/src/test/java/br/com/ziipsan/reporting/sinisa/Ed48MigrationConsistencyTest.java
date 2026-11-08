package br.com.ziipsan.reporting.sinisa;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia do SQL pendente da ED-48 (artefato pendente
 * ED-48__sinisa_catalog_benchmark.sql em db/pendente/, numeracao NAO inventada)
 * com as classes: tabelas, travas de adesao e o padrao desligado no banco.
 */
class Ed48MigrationConsistencyTest {

    private String migration() throws Exception {
        Path path = Path.of("src/main/resources/db/pendente/ED-48__sinisa_catalog_benchmark.sql");
        if (!Files.exists(path)) {
            path = Path.of("intelligence/ziipsan-reporting").resolve(path);
        }
        return Files.readString(path);
    }

    @Test
    void numberingIsDeclaredPendingNotInvented() throws Exception {
        assertThat(migration()).contains("NUMERACAO PENDENTE - NAO INVENTADA");
    }

    @Test
    void tenantTablesExistAndAreRegistered() throws Exception {
        String sql = migration();
        for (String table : List.of("sinisa_indicator_links", "cross_tenant_opt_ins")) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "'");
        }
    }

    @Test
    void globalBenchmarkBaseHasNoTenantRegistration() throws Exception {
        String sql = migration();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS benchmark_references");
        assertThat(sql).doesNotContain("register_tenant_table('benchmark_references'");
        // Nenhuma mediana semeada: a base e importada de dado publico.
        assertThat(sql).doesNotContain("INSERT INTO benchmark_references");
    }

    @Test
    void optInIsOffByDefaultAndClauseIsLockedInTheDatabase() throws Exception {
        String sql = migration();
        assertThat(sql).contains("opted_in BOOLEAN NOT NULL DEFAULT FALSE");
        assertThat(sql).contains("contract_clause_reference IS NOT NULL");
    }

    @Test
    void formsInSqlMatchTheJavaEnum() throws Exception {
        String sql = migration();
        for (SinisaPanelForm form : SinisaPanelForm.values()) {
            assertThat(sql).contains("'" + form.name() + "'");
        }
    }

    @Test
    void linkTableCarriesNoInventedOfficialCodeColumn() throws Exception {
        assertThat(migration()).doesNotContain("official_code");
    }
}
