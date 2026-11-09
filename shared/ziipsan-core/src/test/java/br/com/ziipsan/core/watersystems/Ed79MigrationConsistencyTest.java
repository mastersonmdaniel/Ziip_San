package br.com.ziipsan.core.watersystems;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consistencia do SQL pendente da ED-79 com o nucleo: tabelas, UNIQUE
 * (tenant_id, id) nos pais de FK composta, FK real para cert_risks da
 * P3.31, ausencia de seeds e das bases legais fora do aceite.
 * O caminho do SQL vem da propriedade {@code ed79.sql} do harness,
 * quando presente; sem ela, o teste localiza o SQL pendente nos
 * caminhos do repo (padrao das MigrationConsistencyTest vizinhas —
 * ED-98/ED-37): a partir do modulo (CWD do surefire) ou da raiz.
 */
class Ed79MigrationConsistencyTest {

    private String sql() throws IOException {
        String prop = System.getProperty("ed79.sql");
        Path p = prop != null ? Path.of(prop)
                : Path.of("src/main/resources/db/pendente/ED-79__water_systems_psa_annual_charter.sql");
        if (prop == null && !Files.exists(p)) {
            p = Path.of("shared/ziipsan-core/src/main/resources/db/pendente/ED-79__water_systems_psa_annual_charter.sql");
        }
        return Files.readString(p);
    }

    @Test void allTablesPresentAndRegistered() throws IOException {
        String sql = sql();
        for (String table : new String[]{"ws_systems", "ws_system_treatment_units", "ws_system_components",
                "ws_safety_plans", "ws_safety_plan_risk_links", "ws_annual_reports", "ws_annual_report_lines",
                "ws_annual_report_months", "ws_annual_report_blocks", "ws_annual_report_cleanings",
                "ws_annual_report_issuances", "ws_service_charters", "ws_service_charter_entries",
                "ws_service_charter_entry_items"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "tabela ausente: " + table);
            assertTrue(sql.contains("register_tenant_table('" + table + "')"), "registro ausente: " + table);
        }
    }

    @Test void compositeFkParentsDeclareTenantUnique() throws IOException {
        String sql = sql();
        for (String unique : new String[]{"uq_ws_systems_tenant_id", "uq_ws_plans_tenant_id",
                "uq_ws_reports_tenant_id", "uq_ws_charters_tenant_id", "uq_ws_issuances_tenant_id",
                "uq_ws_components_tenant_id"}) {
            assertTrue(sql.contains(unique), "UNIQUE (tenant_id, id) ausente: " + unique);
        }
        assertTrue(sql.contains("REFERENCES ws_systems (tenant_id, id)"));
        assertTrue(sql.contains("REFERENCES ws_annual_reports (tenant_id, id)"));
        assertTrue(sql.contains("REFERENCES ws_service_charters (tenant_id, id)"));
        // FK real do PSA para o risco da P3.31 (V21_3 ja esta na main)
        assertTrue(sql.contains("REFERENCES cert_risks (tenant_id, id)"));
    }

    @Test void noSeedDataAndNoInventedFlywayNumber() throws IOException {
        String sql = sql();
        assertFalse(sql.contains("INSERT INTO"), "SQL pendente nao semeia dado nenhum");
        assertTrue(sql.contains("PENDENTE de numeracao Flyway"));
        assertTrue(sql.contains("ED-79"));
        assertFalse(sql.matches("(?s).*V\\d+__.*"), "nenhum numero Flyway inventado no SQL pendente");
    }

    @Test void checksMirrorJavaRules() throws IOException {
        String sql = sql();
        assertTrue(sql.contains("kind = 'SUPPLY' AND source_kind IS NOT NULL"));
        assertTrue(sql.contains("kind = 'SEWAGE' AND source_kind IS NULL"));
        assertTrue(sql.contains("conforming + non_conforming + indeterminate <= analyzed"));
        assertTrue(sql.contains("outcome = 'ISSUED' AND notification_attempt_id IS NOT NULL"));
        assertTrue(sql.contains("status = 'PUBLISHED' AND portal_ref IS NOT NULL"));
        assertTrue(sql.contains("Decreto 5.440/2005"));
        assertTrue(sql.contains("Lei 13.460/2017"));
    }

    @Test void sqlHasEmissionGuardsFromCorrectionD1() throws IOException {
        String sql = sql();
        // CHECK da base legal (a mesma constante do Java) — ataque da base inventada recusado
        assertTrue(sql.contains("legal_basis = 'Decreto 5.440/2005, art. 3"), "CHECK da base legal ausente");
        // Constraint triggers diferidas dos estados terminais (READY completo / ISSUED com emissao)
        assertTrue(sql.contains("trg_ws_reports_terminal_guard"));
        assertTrue(sql.contains("trg_ws_issuances_terminal_guard"));
        assertTrue(sql.contains("DEFERRABLE INITIALLY DEFERRED"));
        // D3: o LOS servico/conjunto esta MARCADO [A DEFINIR] na cobertura (P-ED79-10)
        assertTrue(Ed79Coverage.items().stream()
                .anyMatch(i -> i.acceptanceItem().contains("LOS") && i.acceptanceItem().contains("[A DEFINIR]")
                        && i.status().equals(Ed79Coverage.PENDENTE)));
    }

    @Test void coverageListsEveryAcceptanceItem() {
        assertEquals(13, Ed79Coverage.items().size());
        assertTrue(Ed79Coverage.items().stream()
                .anyMatch(i -> i.status().equals(Ed79Coverage.PENDENTE)));
    }
}
