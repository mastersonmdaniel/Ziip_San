package br.com.ziipsan.commercial.collection.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Guardas textuais da V6_3 (sem banco), no padrao das guardas da P3.1. */
class PixMigrationConsistencyTest {

    private static String sql;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = PixMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V6_3__pix_charges_and_hybrid_slip.sql")) {
            assertThat(in).as("migration V6_3 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    void pixInboxEParticionadaPorMesComParticaoDefault() {
        assertThat(sql).contains("PARTITION BY RANGE (received_at)");
        assertThat(sql).contains("PARTITION OF pix_inbox DEFAULT");
        assertThat(sql).contains("ensure_pix_inbox_partition");
    }

    @Test
    void idempotenciaDoCallbackPorPspEEndToEndId() {
        assertThat(sql).contains("idx_pix_inbox_idempotency");
        assertThat(sql).contains("ON pix_inbox(tenant_id, psp, end_to_end_id, received_at)");
    }

    @Test
    void rotaDeWebhookMatrizPspEHibridoExistem() {
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS pix_webhook_routes");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS pix_psp_profiles");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS hybrid_documents");
        assertThat(sql).contains("open_finance_jsr");
        assertThat(sql).contains("pix_proximity");
    }

    @Test
    void paymentsGanhaColunasPixSemRemoverNada() {
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS end_to_end_id");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS txid");
        assertThat(sql).contains("idx_payments_e2eid");
        assertThat(sql).doesNotContain("DROP COLUMN");
        assertThat(sql).doesNotContain("DROP TABLE");
    }

    @Test
    void referenciaDeArrecadacaoDaP31EstaNasTabelasNovas() {
        assertThat(sql).contains("collection_month DATE NOT NULL");
        assertThat(sql).contains("DATE_TRUNC('month', collection_month)");
        assertThat(sql).contains("fiscal_document_id UUID");
    }

    @Test
    void tabelasRegistradasParaRlsENenhumDadoSemeado() {
        for (String table : new String[]{"pix_webhook_routes", "pix_psp_profiles", "pix_charges",
                "pix_inbox", "hybrid_documents"}) {
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
        assertThat(sql).doesNotContain("INSERT INTO");
    }

    @Test
    void dedupNaoParticionadaGaranteIdempotenciaEntreParticoes() {
        // Correcao pos-revisao M1: a PK sem received_at e a garantia de banco.
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS pix_inbox_dedup");
        assertThat(sql).contains("PRIMARY KEY (tenant_id, psp, end_to_end_id)");
        assertThat(sql).contains("register_tenant_table('pix_inbox_dedup')");
        assertThat(sql).contains("ON CONFLICT (tenant_id, psp, end_to_end_id) DO NOTHING");
    }

    @Test
    void routeSegmentSoEObrigatorioParaCallbackEDecisaoNasceRecebida() {
        // Correcao pos-revisao M3: varredura grava NULL; decisao nasce RECEBIDA.
        assertThat(sql).contains("route_segment VARCHAR(64),");
        assertThat(sql).contains("CHECK (source <> 'CALLBACK' OR route_segment IS NOT NULL)");
        assertThat(sql).contains("decision VARCHAR(28) NOT NULL DEFAULT 'RECEBIDA'");
    }

    @Test
    void payloadJsonbDaInboxUsaJdbcTypeCodeJson() throws Exception {
        var field = br.com.ziipsan.commercial.collection.domain.PixInboxEntity.class
                .getDeclaredField("payload");
        var ann = field.getAnnotation(org.hibernate.annotations.JdbcTypeCode.class);
        assertThat(ann).as("payload jsonb exige @JdbcTypeCode").isNotNull();
        assertThat(ann.value()).isEqualTo(org.hibernate.type.SqlTypes.JSON);
    }
}
