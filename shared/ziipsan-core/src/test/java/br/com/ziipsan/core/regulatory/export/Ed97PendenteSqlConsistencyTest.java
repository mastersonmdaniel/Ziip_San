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
}
