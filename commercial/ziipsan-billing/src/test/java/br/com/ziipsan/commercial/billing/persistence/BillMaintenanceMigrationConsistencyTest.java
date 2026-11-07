package br.com.ziipsan.commercial.billing.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas textuais da V4_16 (P2.20), no modelo das guardas da P2.5/P2.6: os
 * invariantes de DAD-16 estao no SQL -- catalogos no lugar do texto livre,
 * eventos so de insercao, parametros registrados e nenhuma semente.
 *
 * <p>A migration e a V4_16, nao a V4_8 da spec: a nota de desvio no
 * cabecalho dela (correcao G2 da revisao cruzada) e parte do que este
 * teste guarda -- numero trocado sem nota seria desvio silencioso.
 */
class BillMaintenanceMigrationConsistencyTest {

    private static String sql;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = BillMaintenanceMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V4_16__bill_rectification_and_maintenance.sql")) {
            assertThat(in).as("migration V4_16 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    @DisplayName("numeracao: o cabecalho registra o desvio da V4_8 reservada e o motivo (ordem contra a V4_14)")
    void numberingDeviationIsDocumented() {
        assertThat(sql).contains("Version: 4.16.0");
        assertThat(sql).contains("spec reservava a V4_8");
        assertThat(sql).contains("V4_14");
        assertThat(sql).contains("primeira");
        assertThat(sql).contains("livre");
        // A dependencia declarada e compativel com a posicao: roda DEPOIS da V4_14/V4_15.
        assertThat(sql).contains("V4_14/V4_15 (bills particionada na forma");
    }

    @Test
    @DisplayName("tabelas novas da P2.20 existem e sao registradas para isolamento")
    void tables() {
        for (String table : new String[]{"bill_review_reasons", "bill_cancel_reasons", "bill_status_events",
                "bill_field_changes", "bill_reviews", "bill_maintenance_operations", "reading_analysis_decisions"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
    }

    @Test
    @DisplayName("conta: ids de motivo, protocolo, OS e versao entram; o texto livre sai")
    void billColumns() {
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS review_reason_id");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS cancel_reason_id");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS protocol_id");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS service_order_id");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS version_no");
        assertThat(sql).contains("DROP COLUMN IF EXISTS review_reason");
        assertThat(sql).contains("DROP COLUMN IF EXISTS cancel_reason");
    }

    @Test
    @DisplayName("bill_status_events e bill_field_changes sao so de insercao (REVOKE + gatilho nas DUAS, sem assimetria)")
    void insertOnly() {
        assertThat(sql).contains("REVOKE UPDATE, DELETE ON bill_status_events FROM ziipsan_app");
        assertThat(sql).contains("REVOKE UPDATE, DELETE ON bill_field_changes FROM ziipsan_app");
        assertThat(sql).contains("BEFORE UPDATE OR DELETE ON bill_status_events");
        assertThat(sql).contains("BEFORE UPDATE OR DELETE ON bill_field_changes");
        assertThat(sql).contains("trg_bill_field_changes_insert_only");
    }

    @Test
    @DisplayName("parametros da manutencao registrados no catalogo global, com SUSPEND_ALL de padrao declarado")
    void parameters() {
        for (String key : new String[]{"bill.review_effects", "bill.review_max_days",
                "bill.rectification_limits_against_user", "bill.due_change_max_per_year",
                "bill.due_change_min_days_before_due", "service_type.internal_approval"}) {
            assertThat(sql).contains("'" + key + "'");
        }
        assertThat(sql).contains("ON CONFLICT (definition_key) DO NOTHING");
        // A semantica do teto de vencimento no catalogo e por conta/cadeia (correcao A6).
        assertThat(sql).contains("cadeia de versoes da conta");
    }

    @Test
    @DisplayName("nenhum dado de negocio semeado: sem INSERT em tabela de tenant")
    void noSeeds() {
        assertThat(sql).doesNotContain("INSERT INTO bill_review_reasons");
        assertThat(sql).doesNotContain("INSERT INTO bill_cancel_reasons");
        assertThat(sql).doesNotContain("INSERT INTO bills");
    }
}
