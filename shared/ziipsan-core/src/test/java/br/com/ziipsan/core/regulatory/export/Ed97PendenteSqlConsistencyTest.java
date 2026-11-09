package br.com.ziipsan.core.regulatory.export;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

class Ed97PendenteSqlConsistencyTest {
    static String sql() throws Exception {
        var p = Path.of("src/main/resources/db/pendente/ED-97__regulatory_export_social_tariff.sql");
        if (!Files.exists(p)) p = Path.of(System.getProperty("user.dir"),
            "entrega/shared/ziipsan-core/src/main/resources/db/pendente/ED-97__regulatory_export_social_tariff.sql");
        return Files.readString(p);
    }
    @Test void sqlIsPendenteIdempotentAndRegistersTablesAndParams() throws Exception {
        String s = sql();
        assertTrue(s.contains("CREATE TABLE IF NOT EXISTS reg_export_submissions"));
        assertTrue(s.contains("register_tenant_table('reg_export_submissions')"));
        assertTrue(s.contains("'reg.reports'"));
        assertTrue(s.contains("package_sha256"));
        assertFalse(s.contains("DROP TABLE"));
        assertFalse(s.contains("DELETE FROM"));
    }
    @Test void sqlHasNoNumberedMigrationName() throws Exception {
        assertTrue(sql().contains("PENDENTE DE NUMERACAO"));
    }
    @Test void sqlRequiresNonBlankProtocolForSubmitted() throws Exception {
        // Correcao R7-B2: o CHECK do protocolo exige nao-branco, como o
        // Java (SubmissionRecord: isBlank) — '' nao passa mais.
        String s = sql();
        assertTrue(s.contains("ck_reg_export_submitted_needs_protocol"));
        assertTrue(s.contains("btrim(agency_protocol) <> ''"));
    }
    @Test void sqlValidatesEvidenceRefFormat() throws Exception {
        // Correcao R7-B2: o formato de evidence_ref e validado no SQL
        // com o mesmo padrao que o Java produz em evidenceRef().
        String s = sql();
        assertTrue(s.contains("ck_reg_export_evidence_ref_format"));
        assertTrue(s.contains("^ED97-EVIDENCE:"));
        assertTrue(s.contains("[0-9a-f]{16}"));
    }
}
