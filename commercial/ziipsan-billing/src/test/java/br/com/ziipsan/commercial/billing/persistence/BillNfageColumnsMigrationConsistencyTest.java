package br.com.ziipsan.commercial.billing.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas textuais da V107 (P2.6, lote R22-A-CURA-P01, 10/11/2026): as seccoes 1 e 2
 * da parte 2 da V4_8 fiscal (bills/bill_items) mudaram para aqui, VERBATIM, porque a
 * V4_8 corria ALTER TABLE bills sem bills no classpath de teste do platform
 * ('relation "bills" does not exist', P-R22-A-01) e porque a V4_14 recria
 * bills/bill_items sem as colunas fiscais -- a V107 corre DEPOIS da V4_14 (e da
 * V4_16) e as colunas sobrevivem a recriacao. A guarda negativa correspondente vive
 * em NfageMigrationConsistencyTest (fiscal): a V4_8 NAO contem estes ALTERs.
 *
 * <p>Desvio conhecido (honesto): o cabecalho da V4_16 (linhas 26-27) declara depender
 * de "V4_7 fiscal (P2.6, bills.fiscal_document_id)"; a coluna chega agora na V107,
 * DEPOIS da V4_16. Inofensivo: a V4_16 nunca referencia a coluna em SQL. A V4_16 e
 * publicada e imutavel, por isso o cabecalho dela nao pode ser corrigido.
 */
class BillNfageColumnsMigrationConsistencyTest {

    private static String sql;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = BillNfageColumnsMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V107__nfage_bill_fiscal_columns.sql")) {
            assertThat(in).as("migration V107 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    @DisplayName("conta ganha fiscal_document_id com FK ao dicionário e índice único parcial; item ganha código fiscal e grupo IBS/CBS")
    void billAndItemAdditions() {
        // As MESMAS assercoes do billAndItemAdditions antigo de
        // NfageMigrationConsistencyTest (fiscal), mudadas de ficheiro com a mesma
        // forca -- nenhuma assercao afrouxada, so o lugar da guarda.
        assertThat(sql).contains("ALTER TABLE bills ADD COLUMN IF NOT EXISTS fiscal_document_id UUID")
                .contains("fk_bill_fiscal_document")
                .contains("uq_bills_fiscal_document")
                .contains("fiscal_item_code")
                .contains("ibs_cbs_cst").contains("ibs_cbs_cclass_trib").contains("ibs_cbs_base")
                .contains("ibs_rate").contains("ibs_value")
                .contains("cbs_rate").contains("cbs_value")
                .contains("NT 2026.002");
    }

    @Test
    @DisplayName("a V107 declara a origem na V4_8, o motivo da mudança e a posição depois da V4_14")
    void provenanceIsDocumented() {
        assertThat(sql).contains("V4_8")
                .contains("P-R22-A-01")
                .contains("V4_14")
                .contains("V107");
    }
}
