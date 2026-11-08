package br.com.ziipsan.reporting.panels;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ED-49: coerencia do SQL pendente com o codigo — tabelas, CHECK dos sete
 * paineis, UNIQUE (tenant_id, id) da licao da C3, registro de tenant e
 * ausencia de qualquer seed (ligacao, recorte, mediana ou parametro).
 * O caminho do SQL vem da propriedade ed49.sql do harness.
 */
class Ed49SqlConsistencyTest {

    private static String sql() throws Exception {
        String path = System.getProperty("ed49.sql");
        assertThat(path).as("propriedade ed49.sql do harness").isNotBlank();
        return Files.readString(Path.of(path));
    }

    @Test
    void sqlDeclaresBothTablesWithTenantUniqueAndRegistration() throws Exception {
        String sql = sql();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS panel_indicator_bindings")
                .contains("CREATE TABLE IF NOT EXISTS panel_benchmark_slices")
                .contains("CONSTRAINT uq_panel_indicator_bindings_tenant_id UNIQUE (tenant_id, id)")
                .contains("CONSTRAINT uq_panel_benchmark_slices_tenant_id UNIQUE (tenant_id, id)")
                .contains("register_tenant_table('panel_indicator_bindings', true, false)")
                .contains("register_tenant_table('panel_benchmark_slices', true, false)");
    }

    @Test
    void panelCheckListsExactlyTheSevenPanelKinds() throws Exception {
        String sql = sql();
        for (PanelKind kind : PanelKind.values()) {
            assertThat(sql).contains("'" + kind.name() + "'");
        }
    }

    @Test
    void sqlSeedsNothingAndTouchesNoParameterOrP39Value() throws Exception {
        String sql = sql().toUpperCase();
        assertThat(sql).doesNotContain("INSERT INTO");
        assertThat(sql).doesNotContain("PARAMETER_DEFINITIONS");
        assertThat(sql).doesNotContain("MEDIAN NUMERIC");
    }

    @Test
    void sqlIsPendingWithoutAnInventedFlywayNumber() throws Exception {
        String sql = sql();
        assertThat(sql).contains("PENDENTE DE NUMERACAO FLYWAY");
        assertThat(Path.of(System.getProperty("ed49.sql")).getFileName().toString())
                .startsWith("ED-49__");
    }
}
