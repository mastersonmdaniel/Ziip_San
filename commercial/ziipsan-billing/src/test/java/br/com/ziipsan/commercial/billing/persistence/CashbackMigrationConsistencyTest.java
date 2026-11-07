package br.com.ziipsan.commercial.billing.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência da V4_12 (P2.34, migration reservada no mapa do plano):
 * tabelas do cashback, garantia da devolução única, {@code tax_event_date}
 * separado, efeito por competência, parâmetro {@code tax.profile} — e a
 * NÃO duplicação da tabela de alíquotas da P2.4 (nenhum CREATE de tabela de
 * tributo nem semente de alíquota nesta migration).
 */
class CashbackMigrationConsistencyTest {

    private static String v412() throws IOException {
        try (InputStream in = CashbackMigrationConsistencyTest.class.getResourceAsStream(
                "/db/migration/tenant/V4_12__cashback_cbs_ibs.sql")) {
            assertThat(in).as("migration V4_12 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("lista importada com origem/data/versão, entradas por período e registro de cada acesso (G2)")
    void listAndAccessTables() throws IOException {
        String sql = v412();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS cashback_list_imports")
                .contains("source_origin").contains("imported_on").contains("list_version")
                .contains("CREATE TABLE IF NOT EXISTS cashback_list_entries")
                .contains("domicile_key_hash").contains("assessment_month")
                .contains("CREATE TABLE IF NOT EXISTS cashback_list_accesses")
                .contains("accessed_by").contains("access_type")
                .contains("register_tenant_table('cashback_list_accesses')");
    }

    @Test
    @DisplayName("devolução única por domicílio e período garantida no banco; total = CBS + IBS")
    void singleReturnGuarantee() throws IOException {
        String sql = v412();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS cashback_returns")
                .contains("uq_cashback_return_domicile_period")
                .contains("UNIQUE (tenant_id, domicile_key_hash, assessment_month)")
                .contains("CHECK (total_return_amount = cbs_return_amount + ibs_return_amount)");
    }

    @Test
    @DisplayName("bills ganha tax_event_date separado; efeito por competência registrado; tax.profile classe b/JUR")
    void billTaxEventEffectAndProfile() throws IOException {
        String sql = v412();
        assertThat(sql).contains("ALTER TABLE bills ADD COLUMN IF NOT EXISTS tax_event_date DATE")
                .contains("CREATE TABLE IF NOT EXISTS cashback_tax_effects")
                .contains("'tax.profile', 'OBJECT', 'b'")
                .contains("'JUR'")
                .contains("cashback_enabled").contains("tax_event_date_rule").contains("split_payment_mode")
                .contains("SEGREGATED_AT_SETTLEMENT");
    }

    @Test
    @DisplayName("a V4_12 NÃO duplica a tabela de alíquotas da P2.4 nem semeia alíquota ou dado de negócio")
    void taxRateTableNotDuplicated() throws IOException {
        String sql = v412();
        assertThat(sql).doesNotContain("CREATE TABLE IF NOT EXISTS tax_rates")
                .doesNotContain("CREATE TABLE IF NOT EXISTS fiscal_documents")
                .doesNotContain("INSERT INTO cashback_list_entries")
                .doesNotContain("INSERT INTO cashback_returns");
    }
}
