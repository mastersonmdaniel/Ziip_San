package br.com.ziipsan.core.inventory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Coerencia SQL x Java da ED-58 (padrao das MigrationConsistencyTest vizinhas). */
class InventoryMigrationConsistencyTest {

    private String sql() throws Exception {
        String prop = System.getProperty("ed58.sql");
        Path p = prop != null ? Path.of(prop)
                : Path.of("src/main/resources/db/pendente/ED-58__inventory_warehouse.sql");
        if (prop == null && !Files.exists(p)) {
            p = Path.of("shared/ziipsan-core/src/main/resources/db/pendente/ED-58__inventory_warehouse.sql");
        }
        return Files.readString(p);
    }

    @Test
    void sqlHasAllTablesAndTenantRegistration() throws Exception {
        String sql = sql();
        for (String table : new String[]{"inv_locations", "inv_items", "inv_balances", "inv_movements",
                "inv_requisitions", "inv_requisition_lines", "inv_transfers", "inv_lots", "inv_serial_units",
                "inv_serial_events", "inv_field_tasks", "inv_posterior_approvals", "inv_shortfall_pendencies",
                "inv_task_consumptions", "inv_counts", "inv_kits", "inv_kit_lines"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "sem tabela " + table);
            assertTrue(sql.contains("register_tenant_table('" + table + "')"), "sem RLS de " + table);
        }
    }

    @Test
    void parameterKeysInSqlMatchJavaAndHaveNoFactoryValues() throws Exception {
        String sql = sql();
        for (String key : new String[]{InventoryParameters.AUTO_APPROVAL_LIMIT, InventoryParameters.APPROVAL_BANDS,
                InventoryParameters.NEGATIVE_REGULARIZATION_DAYS, InventoryParameters.LOT_EXPIRY_ALERT_DAYS}) {
            assertTrue(sql.contains("'" + key + "'"), "SQL sem a chave " + key);
        }
        // nenhuma definition com valor de fabrica: as 4 linhas usam NULL apos param_class 'd'
        long definitions = sql.lines().filter(l -> l.contains("'inv.")).count();
        assertEquals(4, definitions);
        assertFalse(sql.contains("'inv.auto_approval_limit', 'DECIMAL', 'd', '"), "alcada com valor de fabrica inventado");
    }

    @Test
    void movementTypesInSqlMatchJavaEnum() throws Exception {
        String sql = sql();
        for (StockMovement.Type t : StockMovement.Type.values()) assertTrue(sql.contains("'" + t.name() + "'"), "SQL sem o tipo " + t);
        // autoaprovacao nao fere o CHECK de autoaprovacao do banco
        assertTrue(sql.contains("approver_id IS NULL OR approval_mode = 'AUTO'"));
    }
}
