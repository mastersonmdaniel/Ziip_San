package br.com.ziipsan.commercial.collection.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas textuais da V6_2 (sem banco), no padrao das guardas de MigrationConsistency
 * dos outros modulos: estrutura conferida contra o acceptance da P3.1.
 */
class CollectionMigrationConsistencyTest {

    private static String sql;
    private static String paymentsBlock;
    private static String detailsBlock;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = CollectionMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V6_2__payment_return_and_reconciliation.sql")) {
            assertThat(in).as("migration V6_2 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        paymentsBlock = sql.substring(sql.indexOf("CREATE TABLE IF NOT EXISTS payments"),
                sql.indexOf("CREATE TABLE IF NOT EXISTS return_files"));
        detailsBlock = sql.substring(sql.indexOf("CREATE TABLE IF NOT EXISTS return_file_details"),
                sql.indexOf("CREATE TABLE IF NOT EXISTS payment_classifications"));
    }

    @Test
    void paymentsTemReferenciaDeArrecadacaoEDocumentoFiscal() {
        assertThat(paymentsBlock).contains("collection_month DATE NOT NULL");
        assertThat(paymentsBlock).contains("fiscal_document_id UUID");
        assertThat(sql).contains("fk_payments_fiscal_document");
        assertThat(sql).contains("REFERENCES fiscal_documents (tenant_id, id)");
        assertThat(sql).contains("DATE_TRUNC('month', collection_month)");
    }

    @Test
    void colunasFebraban150NaoEstaoEmPaymentsEEstaoNoDetalheDoRetorno() {
        for (String column : new String[]{"capture_form", "payment_form", "collecting_branch",
                "authentication", "line_number", "barcode"}) {
            assertThat(paymentsBlock).as("payments nao pode declarar %s", column)
                    .doesNotContain(column + " VARCHAR");
            assertThat(detailsBlock).as("detalhe do retorno deve declarar %s", column)
                    .contains(column);
            assertThat(sql).as("payments deve remover %s se preexistir", column)
                    .contains("DROP COLUMN IF EXISTS " + column);
        }
    }

    @Test
    void idempotenciaDoRetornoPorNsaEHash() {
        assertThat(sql).contains("CREATE UNIQUE INDEX idx_return_files_idempotency "
                + "ON return_files(tenant_id, nsa, file_hash)");
    }

    @Test
    void aprovacaoDuplaTemGuardasNoBanco() {
        assertThat(sql).contains("first_approver_id <> requested_by");
        assertThat(sql).contains("second_approver_id <> first_approver_id");
    }

    @Test
    void tabelasRegistradasParaRlsENenhumDadoSemeado() {
        for (String table : new String[]{"payments", "return_files", "return_file_details",
                "payment_classifications", "withholding_credits", "payment_events"}) {
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
        assertThat(sql).doesNotContain("INSERT INTO");
    }

    @Test
    void payloadJsonbDoEventoUsaJdbcTypeCodeJson() throws Exception {
        // Padrao do projeto (guarda equivalente no billing, P2.5): String em coluna
        // jsonb sem @JdbcTypeCode(JSON) e ligada como VARCHAR e o PostgreSQL recusa
        // o INSERT em runtime. Correcao aplicada na revisao cruzada da P3.1.
        var field = br.com.ziipsan.commercial.collection.domain.PaymentEventEntity.class
                .getDeclaredField("payload");
        var ann = field.getAnnotation(org.hibernate.annotations.JdbcTypeCode.class);
        assertThat(ann).as("payload jsonb exige @JdbcTypeCode").isNotNull();
        assertThat(ann.value()).isEqualTo(org.hibernate.type.SqlTypes.JSON);
    }

    @Test
    void situacoesEEfeitosDaClassificacaoEstaoNosChecks() {
        for (String value : new String[]{"EXACT_SETTLEMENT", "UNDERPAID_WITHIN_TOLERANCE",
                "OVERPAID_WITHIN_TOLERANCE", "DIFFERENCE_BEYOND_TOLERANCE", "DUPLICATE_PAYMENT",
                "PRESCRIBED_BILL_PAYMENT", "PAYMENT_TRANSFER", "UNIDENTIFIED",
                "DUPLICATE_REFUND_HANDOFF", "CREDIT_RECOVERY"}) {
            assertThat(sql).contains(value);
        }
    }
}
