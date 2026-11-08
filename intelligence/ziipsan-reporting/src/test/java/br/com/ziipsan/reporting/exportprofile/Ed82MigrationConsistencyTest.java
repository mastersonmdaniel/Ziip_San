package br.com.ziipsan.reporting.exportprofile;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Consistencia estatica do SQL pendente da ED-82 (padrao das EDs da cascata). */
class Ed82MigrationConsistencyTest {

    private String sql() throws Exception {
        String p = System.getProperty("ed82.sql.path");
        assertNotNull(p, "rode com -Ded82.sql.path apontando para ED-82__export_profiles.sql");
        return Files.readString(Path.of(p));
    }

    @Test void compositeFkHasUniqueInParentAndNoSeed() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("UNIQUE (tenant_id, id)"), "pai da FK composta exige UNIQUE (tenant_id, id) (C3)");
        assertTrue(sql.contains("FOREIGN KEY (tenant_id, document_id)"));
        assertFalse(sql.contains("INSERT INTO"), "SQL da ED-82 nao semeia dado");
    }

    // Correcoes D1/D2 da revisao: a paridade Java x SQL tem de estar escrita no SQL.
    @Test void sqlMirrorsJavaParityChecks() throws Exception {
        String sql = sql();
        // D1(a): kind desnormalizado nas linhas + FK por kind + regra ISO/PNQS da referencia.
        assertTrue(sql.contains("profile_kind VARCHAR(20) NOT NULL"), "linhas desnormalizam profile_kind");
        assertTrue(sql.contains("UNIQUE (tenant_id, id, profile_kind)"), "pai da FK por kind");
        assertTrue(sql.contains("FOREIGN KEY (tenant_id, document_id, profile_kind)"), "FK por kind");
        assertTrue(sql.contains("profile_kind = 'ISO_37120' AND external_reference IS NOT NULL"));
        assertTrue(sql.contains("profile_kind = 'PNQS' AND external_reference IS NULL"));
        // D1(b): item de rede so conclui com o vinculo ligado.
        assertTrue(sql.contains("item_code NOT LIKE '%-REDE-REFERENCIA'"));
        assertTrue(sql.contains("ck_cadastre_checklist_network_link"));
        // D1(c): coerencia item x norma.
        assertTrue(sql.contains("ck_cadastre_checklist_item_norm"));
        assertTrue(sql.contains("item_code LIKE 'NBR12586-%' AND norm_reference = 'ABNT NBR 12586'"));
        // D2: NOT_APPLICABLE exige note nao-vazia (com guarda de NULL do btrim).
        assertTrue(sql.contains("ck_cadastre_checklist_note_for_not_applicable"));
        assertTrue(sql.contains("note IS NOT NULL AND btrim(note) <> ''"));
    }

    @Test void kindsAndNormsAreClosedLists() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("'ISO_37120'"));
        assertTrue(sql.contains("'PNQS'"));
        for (String n : new String[]{"12586", "12587", "13133", "14166"}) {
            assertTrue(sql.contains(n), n);
        }
    }
}
