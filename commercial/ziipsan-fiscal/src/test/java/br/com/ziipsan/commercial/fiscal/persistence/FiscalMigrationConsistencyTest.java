package br.com.ziipsan.commercial.fiscal.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas da migration {@code V4_5__create_fiscal_schema.sql} que NÃO precisam de banco
 * (mesmo padrão do MigrationConsistencyTest do billing): tabelas, exclusão de vigência,
 * imutabilidade do XML, ausência de seed e ausência de material secreto. A prova contra o
 * banco de verdade está no IT (FiscalV45IT, em ziipsan-app).
 */
class FiscalMigrationConsistencyTest {

    private static final Set<String> TABLES = Set.of(
            "tax_rates", "fiscal_issuers", "fiscal_certificates", "fiscal_certificate_access_log",
            "fiscal_series", "fiscal_documents", "fiscal_document_taxes", "fiscal_document_events",
            "fiscal_document_batches", "fiscal_document_batch_items", "fiscal_documents_xml",
            "fiscal_layout_versions");

    private static String sql;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = FiscalMigrationConsistencyTest.class.getResourceAsStream(
                "/db/migration/tenant/V4_5__create_fiscal_schema.sql")) {
            assertThat(in).as("migration no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("as 12 tabelas da V4_5 existem e todas sao registradas para RLS")
    void twelveTablesRegistered() {
        Matcher tables = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)").matcher(sql);
        Set<String> found = new java.util.HashSet<>();
        while (tables.find()) {
            found.add(tables.group(1));
        }
        assertThat(found).isEqualTo(TABLES);
        for (String table : TABLES) {
            assertThat(sql).contains("register_tenant_table('" + table + "'");
        }
    }

    @Test
    @DisplayName("nenhum dado e semeado e nenhum material secreto tem coluna")
    void noSeedNoSecrets() {
        assertThat(sql).doesNotContain("INSERT INTO");
        // Colunas de material secreto (os comentarios citam os termos para proibi-los).
        assertThat(sql).doesNotContain("pfx_ciphertext");
        assertThat(sql).doesNotContain("password_ciphertext");
        assertThat(sql).doesNotContain("senha VARCHAR");
        assertThat(sql).doesNotContain("senha TEXT");
    }

    @Test
    @DisplayName("vigencia sem sobreposicao, tpAmb por ambiente e XML somente-insercao")
    void invariants() {
        // Nome proprio: excl_tax_rates_no_overlap ja existe na V4_1 (tax_withholding_rates)
        // e nomes de constraint sao unicos por schema — colisao provada pelo IT.
        assertThat(sql).contains("excl_fiscal_tax_rates_no_overlap");
        assertThat(sql).doesNotContain("CONSTRAINT excl_tax_rates_no_overlap");
        assertThat(sql).contains("EXCLUDE USING gist");
        assertThat(sql).contains("(environment = 'SIMULATED' AND tp_amb IS NULL)");
        assertThat(sql).contains("fiscal_documents_xml_immutable");
        assertThat(sql).contains("BEFORE UPDATE ON fiscal_documents_xml");
        assertThat(sql).contains("BEFORE DELETE ON fiscal_documents_xml");
        // O XML autorizado nao e auditado (o trigger copiaria o XML inteiro para o audit_log).
        assertThat(sql).contains("register_tenant_table('fiscal_documents_xml', true, false)");
        // Idempotencia por origem e numero unico por serie.
        assertThat(sql).contains("idx_fiscal_documents_source");
        assertThat(sql).contains("idx_fiscal_documents_number");
    }
}
