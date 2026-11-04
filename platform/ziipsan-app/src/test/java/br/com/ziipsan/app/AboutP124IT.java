package br.com.ziipsan.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1.24 — prova ponta a ponta do Sobre completo contra PostgreSQL real:
 * migration core V9_5 (tabelas, RLS com FORCE, grants de imutabilidade),
 * {@code GET /api/v1/about} estendido, aceite de documento, novidades por
 * usuário e licenças.
 *
 * <p>Dados sintéticos apenas: o documento "terms" semeado aqui e a nota
 * 9.9.9 (recurso de teste em src/test/resources) NÃO são conteúdo real —
 * o catálogo de produção começa vazio, porque texto legal é conteúdo do
 * dono. O que se prova é o mecanismo, não um documento inventado.
 *
 * <p>Limpeza pré/pós teste: o banco de integração é compartilhado pela JVM
 * e outra classe poderia, em tese, tocar estas tabelas novas; por isso o
 * teste limpa os próprios códigos/versões antes de semear e depois de rodar.
 */
class AboutP124IT extends AppIntegrationTestBase {

    private static final String HASH = "a".repeat(64);

    private String token;

    @BeforeEach
    void cleanAndSeed() {
        token = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        clean(migrator);
        migrator.update(
                "INSERT INTO platform.document (code, title, current_version, current_content_hash)"
                        + " VALUES ('terms', 'Termos de Uso (sintético)', '1.0.0', ?)",
                HASH);
    }

    @AfterEach
    void cleanUp() {
        clean(new JdbcTemplate(ProvisionedTenant.migratorDs));
    }

    private void clean(JdbcTemplate migrator) {
        // P1-C (integracao): document_acceptance tem RLS com FORCE e policy
        // USING (tenant_id = current_tenant_id()) — sem app.current_tenant na
        // conexao, o DELETE casa 0 linhas em silencio (a policy nao ve nada)
        // e o DELETE do documento quebra na FK. Por isso as tres exclusoes
        // rodam numa so transacao, com o GUC fixado antes: SET LOCAL so vale
        // na transacao corrente, e um update() por chamada perderia o GUC
        // entre as chamadas (pool).
        migrator.execute((Connection c) -> {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + ProvisionedTenant.TENANT_ID + "', true)");
                st.executeUpdate(
                        "DELETE FROM platform.document_acceptance WHERE tenant_id = '" + ProvisionedTenant.TENANT_ID
                                + "' AND document_code = 'terms'");
                st.executeUpdate(
                        "DELETE FROM support.release_note_view WHERE tenant_id = '" + ProvisionedTenant.TENANT_ID
                                + "' AND version = '9.9.9'");
                st.executeUpdate("DELETE FROM platform.document WHERE code = 'terms'");
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(oldAutoCommit);
            }
            return null;
        });
    }

    @Test
    @DisplayName("V9_5 cria as 3 tabelas com RLS forçada nas duas de tenant e grants de imutabilidade")
    void migrationCreatesTablesWithForcedRlsAndAppendOnlyGrants() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,"
                             + " EXISTS (SELECT 1 FROM pg_policies p"
                             + " WHERE p.schemaname IN ('platform', 'support')"
                             + " AND p.tablename = c.relname) AS has_policy,"
                             + " has_table_privilege('ziipsan_app', n.nspname || '.' || c.relname, 'SELECT') AS can_select,"
                             + " has_table_privilege('ziipsan_app', n.nspname || '.' || c.relname, 'INSERT') AS can_insert,"
                             + " has_table_privilege('ziipsan_app', n.nspname || '.' || c.relname, 'UPDATE') AS can_update,"
                             + " has_table_privilege('ziipsan_app', n.nspname || '.' || c.relname, 'DELETE') AS can_delete"
                             + " FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                             + " WHERE (n.nspname = 'platform' AND c.relname IN ('document', 'document_acceptance'))"
                             + " OR (n.nspname = 'support' AND c.relname = 'release_note_view')"
                             + " ORDER BY n.nspname, c.relname")) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                String table = rs.getString(1);
                if (table.equals("document")) {
                    // Catálogo global: sem RLS (como platform.deployment), só leitura.
                    assertThat(rs.getBoolean(5)).as("document: SELECT").isTrue();
                    assertThat(rs.getBoolean(6)).as("document: sem INSERT").isFalse();
                } else {
                    // Dado do tenant: RLS com FORCE + policy, INSERT sem UPDATE/DELETE.
                    assertThat(rs.getBoolean(2)).as(table + ": RLS").isTrue();
                    assertThat(rs.getBoolean(3)).as(table + ": FORCE").isTrue();
                    assertThat(rs.getBoolean(4)).as(table + ": policy").isTrue();
                    assertThat(rs.getBoolean(5)).as(table + ": SELECT").isTrue();
                    assertThat(rs.getBoolean(6)).as(table + ": INSERT").isTrue();
                    assertThat(rs.getBoolean(7)).as(table + ": sem UPDATE (imutável)").isFalse();
                    assertThat(rs.getBoolean(8)).as(table + ": sem DELETE (imutável)").isFalse();
                }
            }
            assertThat(rows).as("tabelas da V9_5").isEqualTo(3);
        }
    }

    @Test
    @DisplayName("about mostra marca e documento vigente sem aceite; aceitar marca vigente e é idempotente")
    void aboutShowsBrandAndDocumentAndAcceptIsIdempotent() throws Exception {
        mvc.perform(get("/api/v1/about").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.brand.productName").value("ZIIPSAN"))
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].code").value("terms"))
                .andExpect(jsonPath("$.documents[0].currentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.documents[0].acceptedVersion").value(nullValue()))
                .andExpect(jsonPath("$.documents[0].upToDate").value(false));

        // Aceite de código inexistente: 404, não 500 nem aceite órfão.
        mvc.perform(post("/api/v1/about/documents/privacy/accept")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        String first = mvc.perform(post("/api/v1/about/documents/terms/accept")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentCode").value("terms"))
                .andExpect(jsonPath("$.documentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.contentHash").value(HASH))
                .andExpect(jsonPath("$.acceptedAt").exists())
                .andReturn().getResponse().getContentAsString();

        // Segundo aceite: mesma data (idempotente, sem novo registro).
        mvc.perform(post("/api/v1/about/documents/terms/accept")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedAt").value(
                        br.com.ziipsan.core.json.Json.readTree(first).path("acceptedAt").asString()));

        mvc.perform(get("/api/v1/about").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].acceptedVersion").value("1.0.0"))
                .andExpect(jsonPath("$.documents[0].upToDate").value(true));
    }

    @Test
    @DisplayName("aceite de um usuário não aparece para outro usuário do mesmo tenant")
    void acceptanceIsPerUser() throws Exception {
        mvc.perform(post("/api/v1/about/documents/terms/accept")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        // Outro subject (outro usuário), mesmo tenant: vigente sem aceite.
        String otherUser = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        mvc.perform(get("/api/v1/about").header("Authorization", bearer(otherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].acceptedVersion").value(nullValue()))
                .andExpect(jsonPath("$.documents[0].upToDate").value(false));
    }

    @Test
    @DisplayName("novidades: lista com visto=false, marcar vista, idempotência, 404 de inédita e 400 de inválida")
    void releaseNotesOncePerVersionPerUser() throws Exception {
        mvc.perform(get("/api/v1/release-notes").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].version").value("9.9.9"))
                .andExpect(jsonPath("$[0].viewed").value(false));

        String first = mvc.perform(post("/api/v1/release-notes/9.9.9/view")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("9.9.9"))
                .andExpect(jsonPath("$.viewed").value(true))
                .andExpect(jsonPath("$.viewedAt").exists())
                .andReturn().getResponse().getContentAsString();

        // Segunda marcação: mesma data (a primeira visualização é a que vale).
        mvc.perform(post("/api/v1/release-notes/9.9.9/view")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewedAt").value(
                        br.com.ziipsan.core.json.Json.readTree(first).path("viewedAt").asString()));

        mvc.perform(get("/api/v1/release-notes").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viewed").value(true));

        // Outro usuário ainda não viu.
        String otherUser = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        mvc.perform(get("/api/v1/release-notes").header("Authorization", bearer(otherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viewed").value(false));

        // Versão bem formada mas não publicada: 404 (sem estado órfão).
        mvc.perform(post("/api/v1/release-notes/1.2.3/view")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        // Versão mal formada: 400.
        mvc.perform(post("/api/v1/release-notes/abc/view")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("licenças: endpoint devolve a lista embalada (recurso de teste) com available=true")
    void licensesEndpointReturnsPackagedList() throws Exception {
        mvc.perform(get("/api/v1/about/licenses").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.licenses", hasSize(1)))
                .andExpect(jsonPath("$.licenses[0].licenses[0]").value("ISC"));
    }

    @Test
    @DisplayName("rotas do Sobre sem token são 401")
    void aboutRoutesWithoutTokenAre401() throws Exception {
        mvc.perform(get("/api/v1/about")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/release-notes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/release-notes/9.9.9/view")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/about/documents/terms/accept")).andExpect(status().isUnauthorized());
    }
}
