package br.com.ziipsan.core.regulatory.agenda;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

class Ed98PendenteSqlConsistencyTest {
    static String sql() throws Exception {
        var p = Path.of("src/main/resources/db/pendente/ED-98__regulatory_obligations_agenda.sql");
        if (!Files.exists(p)) p = Path.of(System.getProperty("user.dir"),
            "entrega/shared/ziipsan-core/src/main/resources/db/pendente/ED-98__regulatory_obligations_agenda.sql");
        return Files.readString(p);
    }
    @Test void sqlIsPendenteIdempotentAndRegistersTablesAndParams() throws Exception {
        String s = sql();
        assertTrue(s.contains("CREATE TABLE IF NOT EXISTS reg_obligation_items"));
        assertTrue(s.contains("register_tenant_table('reg_obligation_items')"));
        assertTrue(s.contains("'reg.obligations'"));
        assertTrue(s.contains("evidence_ref"));
        assertTrue(s.contains("ck_reg_obligation_done_needs_evidence"));
        assertTrue(s.contains("ck_reg_obligation_inspection"));
        assertFalse(s.contains("DROP TABLE"));
        assertFalse(s.contains("DELETE FROM"));
    }
    @Test void sqlHasNoNumberedMigrationName() throws Exception {
        assertTrue(sql().contains("PENDENTE DE NUMERACAO"));
    }

    // ---- Correcao D7 (REVISAO-ED-98): consistencia codigo x SQL ----

    @Test void sqlRegistersObligationsParamWithClassD() throws Exception {
        // O parametro reg.obligations nasce com a classe d do aceite.
        assertTrue(sql().contains("'reg.obligations', 'OBJECT', 'd'"),
                "reg.obligations tem de ser registrado com param_class 'd'");
    }

    @Test void sqlKindCheckCoversExactlyTheEnumKinds() throws Exception {
        String s = sql();
        int from = s.indexOf("kind IN (");
        int to = s.indexOf("))", from);
        assertTrue(from >= 0 && to > from, "CHECK de kind presente no SQL");
        String listed = s.substring(from, to);
        java.util.Set<String> inSql = new java.util.HashSet<>();
        for (ObligationKind k : ObligationKind.values()) {
            assertTrue(listed.contains("'" + k.name() + "'"),
                    "kind do enum ausente no CHECK do SQL: " + k.name());
            inSql.add(k.name());
        }
        // Nenhum kind a mais no SQL alem do enum (conta as aspas da lista).
        long quoted = listed.chars().filter(c -> c == '\'').count() / 2;
        assertEquals(ObligationKind.values().length, quoted,
                "o CHECK de kind lista exatamente os kinds do enum");
    }

    @Test void sqlHasManualDoneCheckAndUniqueIndexes() throws Exception {
        String s = sql();
        assertTrue(s.contains("ck_reg_obligation_manual_done_needs_note"),
                "CHECK de DONE manual (RAS-17) presente");
        assertTrue(s.contains("ck_reg_obligation_due"), "CHECK de vencimento presente");
        assertTrue(s.contains("uq_reg_obligation_item_period"), "indice unico de periodo presente");
        assertTrue(s.contains("(tenant_id, kind, period_ref)"),
                "indice unico e sobre (tenant_id, kind, period_ref)");
        assertTrue(s.contains("uq_reg_obligation_item_evidence"),
                "indice unico da evidencia presente (D1: uma evidencia, um item)");
    }

    @Test void sqlTextGuardsRequireNonBlank() throws Exception {
        // D7: as travas de texto do SQL sao de NAO-BRANCO (btrim), como
        // as do codigo — string vazia nao passa.
        String s = sql();
        for (String field : java.util.List.of(
                "evidence_ref", "completion_note", "occurrence_ref", "action_plan", "due_label")) {
            assertTrue(s.contains("btrim(" + field + ") <> ''"),
                    "CHECK de nao-branco ausente para " + field);
        }
    }
}
