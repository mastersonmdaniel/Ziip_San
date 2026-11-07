package br.com.ziipsan.core.exit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas da V10_5 sem banco, no padrao dos MigrationConsistencyTest
 * do projeto: as tabelas do mapa do plano, os CHECKs espelhando os
 * enums do codigo, o teto de 120 dias da consulta no banco e os
 * formatos CSV/JSON/XML na exportacao da V9.
 */
class TenantExitMigrationConsistencyTest {

    private static final String SQL = readSql();

    private static String readSql() {
        try (InputStream in = TenantExitMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/core/V10_5__tenant_exit_readonly_period.sql")) {
            assertThat(in).as("migration V10_5 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("V10_5 cria as tabelas do processo de saida e do periodo de consulta")
    void tablesExist() {
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS platform.tenant_exit ");
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS platform.tenant_readonly_period");
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS platform.tenant_readonly_user");
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS platform.tenant_exit_contestation");
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS platform.tenant_exit_court_order");
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS platform.tenant_exit_download");
    }

    @Test
    @DisplayName("CHECK de level da V10_5 lista exatamente o enum Level; formatos batem com o enum Format")
    void checksMatchEnums() {
        for (String name : enumNames(TenantExitCalculator.Level.class)) {
            assertThat(SQL).as("level %s no CHECK", name).contains("'" + name + "'");
        }
        for (String name : enumNames(TenantExitExport.Format.class)) {
            assertThat(SQL).as("formato %s aceito", name).contains("'" + name + "'");
        }
    }

    @Test
    @DisplayName("V10_5 altera o CHECK de formato da tenant_export da V9 (nao basta o enum aparecer no arquivo)")
    void tenantExportFormatCheckIsWidened() {
        assertThat(SQL).contains("ALTER TABLE platform.tenant_export");
        assertThat(SQL).contains("ADD CONSTRAINT tenant_export_format_check");
        assertThat(SQL).contains("pg_constraint");
    }

    @Test
    @DisplayName("teto de 120 dias da consulta e guardas de ELIMINATED estao no banco")
    void readonlyCapAndEliminatedGuards() {
        assertThat(SQL).contains("INTERVAL '120 days'");
        assertThat(SQL).contains("status <> 'ELIMINATED' OR key_destroyed_at IS NOT NULL");
        assertThat(SQL).contains("status <> 'ELIMINATED' OR certificate_hash IS NOT NULL");
        assertThat(SQL).contains("status <> 'ELIMINATION_SCHEDULED' OR (scheduled_by IS NOT NULL AND scheduled_mfa)");
    }
}
