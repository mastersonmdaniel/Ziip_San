package br.com.ziipsan.commercial.billing.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia entre o catalogo Java (fonte unica) e as cargas repetiveis
 * R__ da V4_10: o que o codigo exige e o que o banco carrega sao o mesmo dado.
 */
class RequiredContentCatalogConsistencyTest {

    private static String resource(String path) throws Exception {
        try (InputStream in = RequiredContentCatalogConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int countRows(String sql) {
        // Cada linha da carga comeca com o marcador da tupla do tenant
        // (contagem por substring, sem regex).
        String marker = "('${tenant_id}', 1,";
        int count = 0;
        int from = 0;
        while ((from = sql.indexOf(marker, from)) >= 0) {
            count++;
            from += marker.length();
        }
        return count;
    }

    @Test
    @DisplayName("R__ do conteudo minimo: par perfil x campo, norma e contagem REAL de linhas conferem")
    void requiredContentLoadMatchesJavaCatalog() throws Exception {
        String sql = resource("/db/migration/tenant/R__invoice_required_content.sql");
        // [Revisao cruzada: a versao original contava num contador nunca
        // afirmado e "conferia" a contagem afirmando o TEXTO do comentario
        // "16 linhas" — assercao sempre-verdadeira se o comentario existisse.
        // Agora: pares (profile_key, field_key) e a contagem real de tuplas.]
        for (BillFieldRequirement f : RequiredBillContent.federalFields()) {
            assertThat(sql).as("campo federal " + f.fieldKey())
                    .contains("NULL, '" + f.fieldKey() + "', '" + f.sourceNorm() + "'");
        }
        int profileRows = 0;
        for (String profile : RequiredBillContent.profileKeys()) {
            for (BillFieldRequirement f : RequiredBillContent.profileFields(profile)) {
                assertThat(sql).as("campo " + f.fieldKey() + " do perfil " + profile)
                        .contains("'" + profile + "', '" + f.fieldKey() + "', '" + f.sourceNorm()
                                + "', '" + f.sourceReference() + "'");
                profileRows++;
            }
        }
        assertThat(RequiredBillContent.federalFields()).hasSize(5);
        assertThat(profileRows).isEqualTo(11);
        // 5 federais + 11 de perfil = 16 tuplas de verdade na carga.
        assertThat(countRows(sql)).isEqualTo(16);
        // O campo pendente do RAS-28 NAO esta na carga.
        assertThat(sql).doesNotContain(PendingTaxDisclosure.FIELD_KEY);
    }

    @Test
    @DisplayName("R__ de mensagens: chave, norma, CLASSE, disparo, ordem e flags de cada mensagem conferem")
    void messageLoadMatchesJavaCatalog() throws Exception {
        String sql = resource("/db/migration/tenant/R__legal_message_catalog.sql");
        for (LegalMessageDef def : LegalMessageCatalog.all()) {
            // A linha da carga e uma tupla unica por mensagem: chave, norma,
            // classe do parametro, disparo, ordem, legal, slot_only.
            // [Revisao cruzada: a versao original so afirmava a chave — foi
            // assim que a classe errada (A/B em vez de C/D) passou.]
            assertThat(sql).as("mensagem " + def.messageKey())
                    .contains("'" + def.messageKey() + "', '" + def.normKey() + "', '"
                            + def.parameterClass().name() + "', '" + def.trigger().name() + "', "
                            + def.order() + ", " + def.legal() + ", " + def.slotOnly());
        }
        assertThat(LegalMessageCatalog.all()).hasSize(7);
        assertThat(countRows(sql)).isEqualTo(7);
    }

    @Test
    @DisplayName("V4_10 cria as tres tabelas, registra RLS e nao cria nada do RAS-28")
    void migrationShape() throws Exception {
        String sql = resource("/db/migration/tenant/V4_10__bill_required_content_and_legal_messages.sql");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS invoice_required_fields");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS legal_message_catalog");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS bill_message_emissions");
        assertThat(sql).contains("register_tenant_table('invoice_required_fields')");
        assertThat(sql).contains("register_tenant_table('legal_message_catalog')");
        assertThat(sql).contains("register_tenant_table('bill_message_emissions')");
        assertThat(sql).doesNotContain(PendingTaxDisclosure.FIELD_KEY);
    }
}
