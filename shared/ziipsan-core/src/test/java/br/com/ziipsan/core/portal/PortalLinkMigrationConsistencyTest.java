package br.com.ziipsan.core.portal;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** P3.20 - guardas textuais da V8_2 (sem banco, padrao das MigrationConsistencyTest). */
class PortalLinkMigrationConsistencyTest {

    @Test
    void v8_2CriaTabelasDoVinculoSemDadoEmClaro() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/tenant/V8_2__consumer_portal_link.sql")) {
            assertThat(in).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql).contains("consumer_portal_links", "consumer_portal_link_attempts",
                "consumer_contract_deliveries");
        assertThat(sql).contains("holder_document_index", "linking_code_index");
        // Documento e codigo NUNCA em claro: nenhuma coluna de documento/codigo sem o sufixo de indice.
        assertThat(sql).doesNotContain("holder_document VARCHAR", "linking_code VARCHAR");
        // Sem seed nas tabelas de negocio: o UNICO INSERT da migration e o de
        // classificacao de dados (public.data_classification), exigido pelo
        // padrao das vizinhas (correcao D1). A proibicao original era generica
        // ("nenhum INSERT INTO") e ficou incompativel com esse INSERT
        // obrigatorio; a trava agora e mais precisa, nao mais fraca.
        assertThat(sql.toUpperCase())
                .doesNotContain("INSERT INTO CONSUMER_PORTAL_LINKS")
                .doesNotContain("INSERT INTO CONSUMER_PORTAL_LINK_ATTEMPTS")
                .doesNotContain("INSERT INTO CONSUMER_CONTRACT_DELIVERIES");
    }

    @Test
    void v8_2RegistraTabelasParaRlsEClassificaIdentificadores() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/tenant/V8_2__consumer_portal_link.sql")) {
            assertThat(in).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // D1: as 3 tabelas registradas no cadastro que sustenta o RLS, no
        // padrao EXATO das vizinhas (V8_1, V1_15, V1_8).
        assertThat(sql).contains("register_tenant_table('consumer_portal_links')");
        assertThat(sql).contains("register_tenant_table('consumer_portal_link_attempts')");
        assertThat(sql).contains("register_tenant_table('consumer_contract_deliveries')");
        // D1: classificacao dos identificadores/indices cegos (P0.11).
        assertThat(sql).contains("INSERT INTO public.data_classification");
        assertThat(sql).contains("'consumer_portal_links', 'consumer_subject', 'ID'");
        assertThat(sql).contains("'consumer_portal_links', 'holder_document_index', 'ID'");
        assertThat(sql).contains("'consumer_portal_links', 'linking_code_index', 'ID'");
        assertThat(sql).contains("'consumer_portal_link_attempts', 'source_ip', 'ID'");
        assertThat(sql).contains("ON CONFLICT (table_name, column_name) DO NOTHING");
        // O INSERT de classificacao e o unico INSERT da migration.
        assertThat(sql.toUpperCase().split("INSERT INTO", -1)).hasSize(2);
        // D3b: metodo de comprovacao do plano §3 presente como coluna.
        assertThat(sql).contains("proof_method", "'CODE_AND_DOCUMENT'");
    }
}
