package br.com.ziipsan.operations.metering.hydrometry.renewal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consistência textual dos SQL pendentes da II-5 (modelo dos testes de
 * consistência da ED-14/ED-103): ficheiros em {@code db/pendente/} com
 * prefixo II-5 e SEM número Flyway; tabelas e registos declarados;
 * parâmetros novos sem duplicar as chaves da ED-14.
 */
class RenewalSqlConsistencyTest {

    private static final String PLAN_SQL = "II-5__meter_renewal_plan.sql";
    private static final String CATALOG_SQL = "II-5__core_parameter_catalog.sql";

    /** Localiza o SQL pendente subindo a partir do user.dir (entrega do pacote ou árvore do repo). */
    private static Path locate(String fileName) {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && dir != null; i++) {
            Path inPackage = dir.resolve("entrega/operations/ziipsan-metering/src/main/resources/db/pendente").resolve(fileName);
            if (Files.isRegularFile(inPackage)) return inPackage;
            Path inRepo = dir.resolve("operations/ziipsan-metering/src/main/resources/db/pendente").resolve(fileName);
            if (Files.isRegularFile(inRepo)) return inRepo;
            Path direct = dir.resolve("src/main/resources/db/pendente").resolve(fileName);
            if (Files.isRegularFile(direct)) return direct;
            dir = dir.getParent();
        }
        return null;
    }

    private static String read(String fileName) throws IOException {
        Path p = locate(fileName);
        assertNotNull(p, "SQL pendente não localizado: " + fileName);
        return Files.readString(p);
    }

    @Test @DisplayName("nomes sem número Flyway e com prefixo do pacote")
    void fileNamesHaveNoFlywayNumber() {
        assertFalse(PLAN_SQL.matches("V\\d+.*"), PLAN_SQL);
        assertFalse(CATALOG_SQL.matches("V\\d+.*"), CATALOG_SQL);
        assertTrue(PLAN_SQL.startsWith("II-5__"));
        assertTrue(CATALOG_SQL.startsWith("II-5__"));
    }

    @Test @DisplayName("SQL do plano: tabelas, registo tenant, unicidades anti-duplicado e marca de pendente")
    void planSqlDeclaresTables() throws IOException {
        String sql = read(PLAN_SQL);
        assertTrue(sql.contains("SEM NUMERO FLYWAY"), "cabeçalho deve declarar pendente de numeração");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS meter_renewal_plans"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS meter_renewal_plan_lines"));
        assertTrue(sql.contains("register_tenant_table('meter_renewal_plans')"));
        assertTrue(sql.contains("register_tenant_table('meter_renewal_plan_lines')"));
        assertTrue(sql.contains("idx_renewal_lines_meter"), "um hidrômetro por plano");
        assertTrue(sql.contains("REFERENCES meters(tenant_id, id)"), "linha referencia o hidrômetro da ED-14 sem o alterar");
        assertFalse(sql.contains("ALTER TABLE meters"), "a II-5 não altera tabelas da ED-14");
    }

    @Test @DisplayName("SQL do catálogo: 4 chaves meter.renewal_* novas e nenhuma chave da ED-14 duplicada")
    void catalogSqlDeclaresOnlyNewKeys() throws IOException {
        String sql = read(CATALOG_SQL);
        for (String key : new String[]{"meter.renewal_score_weights", "meter.renewal_age_reference_years",
                "meter.renewal_technology_scores", "meter.renewal_annual_restriction"})
            assertTrue(sql.contains("('" + key + "'"), "chave em falta: " + key);
        assertFalse(sql.contains("('meter.replace_by_volume_m3_by_class'"),
                "chave da ED-14 não pode ser registada outra vez");
        assertFalse(sql.contains("('meter.verification_interval_years'"),
                "chave da ED-14 não pode ser registada outra vez");
        assertTrue(sql.contains("[A DEFINIR]"), "valores marcados [A DEFINIR] (P-II5-PARAM)");
    }
}
