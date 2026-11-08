package br.com.ziipsan.reporting.sheet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coerencia estatica do SQL pendente da ED-77 (padrao das frentes anteriores). */
class Ed77MigrationConsistencyTest {

    private String sql() throws Exception {
        Path p = Path.of("intelligence/ziipsan-reporting/src/main/resources/db/pendente/ED-77__indicator_sheets.sql");
        if (!Files.exists(p)) {
            p = Path.of(System.getProperty("ed77.sql.path", ""));
        }
        return Files.readString(p);
    }

    @Test
    void fkCompostaTemUniqueNoPaiLicaoC3() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("CONSTRAINT uq_incentive_assessments_tenant_id UNIQUE (tenant_id, id)"),
                "pai da FK composta sem UNIQUE (tenant_id, id) - licao C3");
        assertTrue(sql.contains("FOREIGN KEY (tenant_id, assessment_id)"), "FK composta da trilha ausente");
        assertTrue(sql.contains("REFERENCES incentive_assessments (tenant_id, id)"));
    }

    @Test
    void semSeedSemValorDeFabrica() throws Exception {
        String sql = sql();
        assertFalse(sql.contains("INSERT INTO"), "SQL da ED-77 nao semeia dado nenhum");
        assertTrue(sql.contains("register_tenant_table('indicator_sheet_meta'"));
        assertTrue(sql.contains("register_tenant_table('incentive_assessment_lineage'"));
        // Piso federal como CHECK espelho, nao como linha.
        assertTrue(sql.contains("target_percent >= 99"));
        assertTrue(sql.contains("target_percent >= 90"));
    }
}
