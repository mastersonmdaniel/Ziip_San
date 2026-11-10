package br.com.ziipsan.commercial.collection.cession;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** ED-103/E6 — consistencia do SQL pendente com os enums e travas do codigo. */
class Ed103PendenteSqlConsistencyTest {

    static String sql() throws Exception {
        var p = Path.of("src/main/resources/db/pendente/ED-103__receivables_cession_linked_account.sql");
        if (!Files.exists(p)) p = Path.of(System.getProperty("user.dir"),
                "src/main/resources/db/pendente/ED-103__receivables_cession_linked_account.sql");
        return Files.readString(p);
    }

    @Test
    void sqlIsPendenteSemFonteAndRegistersAllTables() throws Exception {
        String s = sql();
        assertTrue(s.contains("PENDENTE DE NUMERACAO"));
        assertTrue(s.contains("[SEM FONTE]"));
        for (String t : java.util.List.of("cession_registrations", "cession_events",
                "cession_export_audits", "cession_linked_account_movements",
                "cession_reconciliation_results")) {
            assertTrue(s.contains("CREATE TABLE IF NOT EXISTS " + t), t);
            assertTrue(s.contains("register_tenant_table('" + t + "')"), t);
        }
        assertFalse(s.contains("INSERT INTO"));
        assertFalse(s.contains("DROP TABLE"));
        assertFalse(s.contains("DELETE FROM"));
    }

    @Test
    void sqlStatusChecksMatchTheLifecycleEnumExactly() throws Exception {
        String s = sql();
        for (CessionRegistry.Status st : CessionRegistry.Status.values())
            assertTrue(s.contains("'" + st.name() + "'"), "estado ausente no SQL: " + st.name());
        for (Reconciliation.DivergenceKind k : Reconciliation.DivergenceKind.values())
            assertTrue(s.contains("'" + k.name() + "'"), "divergencia ausente no SQL: " + k.name());
        assertTrue(s.contains("'CASADO'"));
    }

    @Test
    void sqlCarriesTheDoubleCessionLockAndReversalReasonGuard() throws Exception {
        String s = sql();
        // Indice unico parcial anti-dupla-cessao sobre registos activos
        assertTrue(s.contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_cession_registrations_active_receivable"));
        assertTrue(s.contains("WHERE status IN ('RESERVADO','CEDIDO')"));
        // Reversao exige motivo
        assertTrue(s.contains("to_status <> 'REVERTIDO'"));
        // Auditoria de exportacao guarda o hash do conteudo
        assertTrue(s.contains("content_hash CHAR(64)"));
    }
}
