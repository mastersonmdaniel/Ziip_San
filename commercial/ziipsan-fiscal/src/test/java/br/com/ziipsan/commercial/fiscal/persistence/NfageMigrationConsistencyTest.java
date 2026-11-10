package br.com.ziipsan.commercial.fiscal.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência da V4_8 COMPLETA (P3.6 + P2.6): a parte da NFS-e avulsa permanece
 * intacta (SQL verbatim da entrega da P3.6) e os acréscimos da NFAg-e estão presentes
 * — sem recriar o dicionário fiscal da V4_5 nem o CHECK de total da V4_6.
 *
 * <p>Nota de integração: o {@code NfseMigrationConsistencyTest} da P3.6 afirmava que
 * a V4_8 NÃO continha tabela de NFAg-e ("a parte da P2.6 fica para a P2.6"). Com a
 * P2.6 entregue, essa premissa expirou e aquele teste precisa ser aposentado/
 * substituído por este na integração — ele reprovaria a V4_8 completa por desenho.
 *
 * <p>Lote R22-A-CURA-P01 (10/11/2026, P-R22-A-01): as secções 1-2 da parte 2
 * (bills/bill_items) mudaram para a V107 do billing; a guarda delas vive em
 * {@code BillNfageColumnsMigrationConsistencyTest} e aqui fica a guarda negativa.
 */
class NfageMigrationConsistencyTest {

    private static String v47() throws IOException {
        try (InputStream in = NfageMigrationConsistencyTest.class.getResourceAsStream(
                "/db/migration/tenant/V4_8__nfse_avulsa_parte.sql")) {
            assertThat(in).as("migration V4_8 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("parte NFS-e da P3.6 intacta: tabelas, etapas, status e registros de tenant originais")
    void nfsePartIntact() throws IOException {
        String sql = v47();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS fiscal_avulso_service_types")
                .contains("CREATE TABLE IF NOT EXISTS fiscal_nfse_avulsas")
                .contains("ISS_FORA_HIPOTESES_ESPECIAIS")
                .contains("DEMAIS_HIPOTESES").contains("SIMPLES_NACIONAL")
                .contains("GERADA_NAO_TRANSMITIDA").contains("PRODUCAO_RESTRITA")
                .contains("register_tenant_table('fiscal_avulso_service_types')")
                .contains("register_tenant_table('fiscal_nfse_avulsas')");
    }

    @Test
    @DisplayName("conta e item NÃO ganham colunas fiscais aqui: secções 1-2 da parte 2 mudaram para a V107 do billing (P-R22-A-01)")
    void billAndItemAdditions() throws IOException {
        String sql = v47();
        // Lote R22-A-CURA-P01 (10/11/2026, P-R22-A-01): as secções 1 e 2 da parte 2
        // (bills.fiscal_document_id; colunas fiscais de bill_items) mudaram para a
        // V107__nfage_bill_fiscal_columns.sql do billing -- a V4_8 corria ALTER TABLE
        // bills sem bills no classpath de teste do platform ('relation "bills" does
        // not exist') e a V4_14 recriava bills/bill_items sem as colunas. A guarda
        // com a mesma força vive em BillNfageColumnsMigrationConsistencyTest (billing).
        assertThat(sql).doesNotContain("ALTER TABLE bills ADD COLUMN IF NOT EXISTS fiscal_document_id UUID")
                .doesNotContain("ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS fiscal_item_code")
                .doesNotContain("fk_bill_fiscal_document")
                .doesNotContain("uq_bills_fiscal_document")
                .doesNotContain("chk_bill_items_ibs_cbs_nonnegative");
    }

    @Test
    @DisplayName("extensão do modelo 75, vínculo com pagamentos (NT 2026.001) e eventos, todos registrados como tabelas de tenant")
    void nfageExtensionTables() throws IOException {
        String sql = v47();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS fiscal_nfage_details")
                .contains("recipient_municipality_ibge")
                .contains("recipient_without_document")
                .contains("recipient_document_encrypted")
                .contains("recipient_document_hash")
                .contains("contingency_reason")
                .contains("layout_version")
                .contains("CREATE TABLE IF NOT EXISTS fiscal_nfage_payments")
                .contains("NT 2026.001")
                .contains("CREATE TABLE IF NOT EXISTS fiscal_nfage_events")
                .contains("'CANCELLATION', 'SUBSTITUTION', 'VOIDING'")
                // Correção da revisão cruzada da P2.6: a contradição do
                // destinatário é sem-documento × documento presente (a mesma
                // do serviço), nunca a exigência de cifrado/hash do MOC.
                .contains("CHECK (NOT recipient_without_document OR (recipient_document_encrypted IS NULL AND recipient_document_hash IS NULL))")
                .contains("register_tenant_table('fiscal_nfage_details')")
                .contains("register_tenant_table('fiscal_nfage_payments')")
                .contains("register_tenant_table('fiscal_nfage_events')");
    }

    @Test
    @DisplayName("a V4_8 NÃO recria o dicionário da V4_5 nem o CHECK de total da V4_6 (DAD-17/DAD-18)")
    void dictionaryAndTotalCheckNotDuplicated() throws IOException {
        String sql = v47();
        assertThat(sql).doesNotContain("CREATE TABLE IF NOT EXISTS fiscal_documents")
                .doesNotContain("CREATE TABLE IF NOT EXISTS fiscal_series")
                .doesNotContain("CREATE TABLE IF NOT EXISTS fiscal_issuers")
                .doesNotContain("ADD CONSTRAINT chk_bills_total_with_withholding_tax");
        // Trava reforçada na revisão cruzada da P2.6 (mesmo padrão da revisão
        // da P3.6): os literais acima deixam passar variações de formatação
        // (espaços/quebras múltiplos, aspas, prefixo de schema) de um CREATE
        // TABLE do dicionário ou do CHECK da V4_6 duplicado.
        String lower = sql.toLowerCase();
        assertThat(lower).doesNotContainPattern(
                "create\\s+table\\s+(if\\s+not\\s+exists\\s+)?[\"\\w.]*fiscal_(documents|series|issuers)\\b");
        assertThat(lower).doesNotContainPattern(
                "add\\s+constraint\\s+chk_bills_total_with_withholding_tax");
    }
}
