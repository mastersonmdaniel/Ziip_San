package br.com.ziipsan.integration.erp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Coerencia SQL x Java da ED-62, incl. a licao da C3 (FK composta exige UNIQUE no pai). */
class ErpMigrationConsistencyTest {

    private String sql() throws Exception {
        return Files.readString(Path.of(System.getProperty("ed62.sql")));
    }

    @Test
    void sqlHasAllTablesTenantRegistrationAndCompositeFkParents() throws Exception {
        String sql = sql();
        for (String table : new String[]{"erp_connections", "erp_layout_mappings", "erp_cost_center_links",
                "erp_material_hooks", "erp_contract_hooks", "erp_export_batches", "erp_indicator_snapshots"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "sem tabela " + table);
            assertTrue(sql.contains("register_tenant_table('" + table + "')"), "sem RLS de " + table);
        }
        // Licao C3: pai de toda FK composta (tenant_id, connection_id)
        assertTrue(sql.contains("uq_erp_connections_tenant_id UNIQUE (tenant_id, id)"));
        assertTrue(sql.contains("FOREIGN KEY (tenant_id, connection_id)"));
    }

    @Test
    void sqlSeedsOnlyErpParametersAllWithoutFactoryValues() throws Exception {
        String sql = sql();
        for (String p : new String[]{"betha", "ipm", "fiorilli"}) {
            for (String k : new String[]{"layout_version", "stock_official", "transport"}) {
                assertTrue(sql.contains("erp." + p + "." + k), "sem definition erp." + p + "." + k);
            }
        }
        assertTrue(sql.contains("'erp.betha.layout_version', 'TEXT', 'd', NULL"), "layout_version deve nascer NULL ([A DEFINIR])");
        assertTrue(sql.contains("'erp.ipm.stock_official', 'TEXT', 'd', NULL"), "stock_official deve nascer NULL ([A DEFINIR])");
        assertTrue(sql.contains("'erp.fiorilli.transport', 'TEXT', 'd', NULL"), "transport deve nascer NULL ([A DEFINIR])");
        // contrato: empenho OU ordem de compra (CHECK do aceite)
        assertTrue(sql.contains("commitment_number IS NOT NULL OR external_purchase_order IS NOT NULL"));
    }
}
