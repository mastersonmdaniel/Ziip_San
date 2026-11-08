package br.com.ziipsan.core.portal;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** P3.35 — guardas textuais da V8_3 (sem banco, padrao das MigrationConsistencyTest). */
class BillVerificationMigrationConsistencyTest {

    @Test
    void v8_3CriaVerificacaoEDenunciasSemDadoDoTitular() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/tenant/V8_3__public_bill_verification.sql")) {
            assertThat(in).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql).contains("bill_verification_codes", "bill_verification_queries",
                "fraud_reports", "published_official_channels");
        assertThat(sql).contains("code_index", "document_hash");
        // Codigo nunca em claro e NENHUMA coluna de titular/endereco/documento do titular:
        String upper = sql.toUpperCase();
        assertThat(upper).doesNotContain("HOLDER_NAME", "HOLDER_DOCUMENT", "CONSUMER_UNIT_ID",
                "HOLDER_ADDRESS", "VERIFICATION_CODE VARCHAR", "CODE VARCHAR");
        assertThat(upper).doesNotContain("INSERT INTO");
        // Guarda ampliada (correcao D9 da revisao cruzada): a lista acima era
        // estreita — uma futura coluna address/titular/cpf/matricula/consumo
        // passaria. A ampliacao roda sobre o DDL SEM os comentarios (os
        // comentarios da migration citam os dados proibidos em prosa, para
        // documentar a ausencia; coluna de verdade esta no DDL):
        String ddl = sql.lines()
                .filter(line -> !line.strip().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b)
                .toUpperCase();
        // (DOCUMENT fica fora de proposito: document_hash e coluna legitima —
        // o hash do documento, nao documento do titular; HOLDER_DOCUMENT ja
        // esta recusado na guarda original acima.)
        assertThat(ddl).doesNotContain("ADDRESS", "TITULAR", "HOLDER", "CPF",
                "MATRICULA", "CONSUMPTION", "CONSUMO", "DOCUMENTO");
    }
}
