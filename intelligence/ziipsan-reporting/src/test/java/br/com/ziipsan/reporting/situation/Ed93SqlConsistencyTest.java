package br.com.ziipsan.reporting.situation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-93: coerencia do SQL pendente com o nucleo (padrao das ondas). */
class Ed93SqlConsistencyTest {

    private static String sql() throws Exception {
        String path = System.getProperty("ed93.sql");
        assertTrue(path != null && !path.isBlank(), "propriedade ed93.sql obrigatoria no harness");
        return Files.readString(Path.of(path));
    }

    @Test
    void bothTablesExistWithTenantUniqueAndRegistration() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS ops_situation_state"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS ops_console_indicator_bindings"));
        assertTrue(sql.contains("uq_ops_situation_state_tenant_id UNIQUE (tenant_id, id)"));
        assertTrue(sql.contains("uq_ops_console_indicator_bindings_tenant_id UNIQUE (tenant_id, id)"));
        assertTrue(sql.contains("register_tenant_table('ops_situation_state')"));
        assertTrue(sql.contains("register_tenant_table('ops_console_indicator_bindings')"));
        assertTrue(sql.contains("uq_ops_situation_state_item UNIQUE (tenant_id, item_kind, item_ref)"));
    }

    @Test
    void itemKindsInSqlMatchTheConsoleEnum() throws Exception {
        String sql = sql();
        for (SituationItemKind kind : SituationItemKind.values()) {
            assertTrue(sql.contains("'" + kind.name() + "'"), "kind ausente no SQL: " + kind);
        }
    }

    @Test
    void noReservedFlywayNumbersAndNoSeeds() throws Exception {
        String sql = sql();
        assertFalse(sql.contains("V102"), "V102 e reservado e proibido nesta entrega");
        assertFalse(sql.contains("V103"), "V103 e reservado e proibido nesta entrega");
        assertFalse(sql.contains("V6_4"), "V6_4 nao pode ser tocada");
        assertFalse(sql.toUpperCase().contains("INSERT INTO"), "nenhuma linha semeada");
    }

    @Test
    void projectionOrderAndCoordinateChecksExist() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("ck_ops_situation_projection_order CHECK (projected_at >= source_at)"));
        assertTrue(sql.contains("ck_ops_situation_coords_pair"));
    }

    @Test
    void statusLabelVocabularyIsCheckedPerKind() throws Exception {
        // D3 (revisao ED-93-REV-1): segunda barreira no banco — o
        // vocabulario de status_label por item_kind e exactamente o
        // que o projetor grava (fontes conferidas no cabecalho do SQL).
        String sql = sql();
        assertTrue(sql.contains("ck_ops_situation_status_by_kind"));
        assertTrue(sql.contains("(item_kind = 'INCIDENT' AND status_label IN ('OPEN', 'CONTROLLED'))"));
        assertTrue(sql.contains("(item_kind = 'TELEMETRY_ALARM' AND status_label IN ('OPEN', 'ACKNOWLEDGED'))"));
        assertTrue(sql.contains("'IN_EXECUTION', 'EXECUTED', 'WAITING_DEPENDENCY', 'PENDING_APPROVAL'"));
        assertTrue(sql.contains("(item_kind = 'UNIT_OUTAGE' AND status_label IN ('OUT_OF_OPERATION'))"));
    }
}
