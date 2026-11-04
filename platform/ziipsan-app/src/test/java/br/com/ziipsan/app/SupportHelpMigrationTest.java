package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.21 — prova da migration core V9_3 (schema {@code support}) e da publicação
 * do conteúdo pela migration repetível {@code R__help_content.sql}, contra
 * PostgreSQL real, no banco virgem compartilhado (ProvisionedTenant).
 *
 * <p>Precisa das variáveis do banco de integração (as mesmas do ci-linux.sh):
 * {@code ZIIPSAN_IT_JDBC_URL}, {@code ZIIPSAN_IT_DB_USER/PASSWORD} e
 * {@code ZIIPSAN_IT_MIGRATOR_USER/PASSWORD}.
 */
class SupportHelpMigrationTest {

    private static final List<String> TABLES = List.of(
            "article", "article_version", "article_feedback", "tour_completion", "error_explanation",
            // P1-C (integracao): o schema support ganhou as tabelas da fila de
            // chamados (P1.23, V9_4) e o visto de release-notes (P1.24, V9_5).
            // RLS forçada + policy continuam exigidas para todas (ver problems).
            "release_note_view",
            "ticket", "ticket_attachment", "ticket_event", "ticket_message", "ticket_number_counter");

    @BeforeAll
    static void installFromScratch() {
        ProvisionedTenant.ensure();
    }

    @Test
    @DisplayName("schema support tem as 11 tabelas com RLS forçada e policy")
    void supportTablesExistWithForcedRls() throws SQLException {
        String sql = """
                SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,
                       EXISTS (SELECT 1 FROM pg_policies p
                               WHERE p.schemaname = 'support' AND p.tablename = c.relname) AS has_policy
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'support' AND c.relkind = 'r'
                ORDER BY c.relname
                """;
        List<String> problems = new ArrayList<>();
        List<String> found = new ArrayList<>();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                found.add(rs.getString(1));
                if (!rs.getBoolean(2)) problems.add(rs.getString(1) + ": RLS desligada");
                if (!rs.getBoolean(3)) problems.add(rs.getString(1) + ": RLS sem FORCE");
                if (!rs.getBoolean(4)) problems.add(rs.getString(1) + ": sem policy");
            }
        }
        assertThat(found).containsExactlyInAnyOrderElementsOf(TABLES);
        assertThat(problems).as("tabelas fora do padrão de isolamento").isEmpty();
    }

    @Test
    @DisplayName("ziipsan_app lê conteúdo e grava feedback/conclusão, sem escrever artigo")
    void appGrants() throws SQLException {
        String sql = "SELECT has_table_privilege('ziipsan_app', ?, ?)";
        checkPrivilege(sql, "support.article", "SELECT", true);
        checkPrivilege(sql, "support.article_version", "SELECT", true);
        checkPrivilege(sql, "support.error_explanation", "SELECT", true);
        checkPrivilege(sql, "support.article_feedback", "INSERT", true);
        checkPrivilege(sql, "support.tour_completion", "INSERT", true);
        checkPrivilege(sql, "support.article", "INSERT", false);
        checkPrivilege(sql, "support.error_explanation", "INSERT", false);
    }

    private static void checkPrivilege(String sql, String table, String privilege, boolean expected)
            throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, privilege);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getBoolean(1)).as("%s %s para ziipsan_app", table, privilege).isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("R__help_content publica os artigos e os erros semeados")
    void seededContentPublished() throws SQLException {
        List<String> slugs = queryStrings(ProvisionedTenant.migratorDs,
                "SELECT slug FROM support.article ORDER BY slug");
        assertThat(slugs).containsExactly(
                "cadastrar-cliente", "consultar-territorio", "pesquisar-imoveis");
        List<String> codes = queryStrings(ProvisionedTenant.migratorDs,
                "SELECT code FROM support.error_explanation ORDER BY code");
        // P1.22 + integração P1-B: o catálogo passa a cobrir todos os códigos problem emitidos.
        // P1.32: +4 códigos do núcleo de Certificações (51 -> 55).
        // P1-C (integração): +9 códigos revelados pela regeneração do bundle
        // gerado (55 -> 64) — os 7 YAMLs novos da rodada 4 + saved-view-title-in-use
        // e user-required, que tinham YAML desde a P1.33 mas estavam fora do bundle
        // commitado. A lista continua exata: código novo exige YAML + bundle.
        // P1-D (integração): +10 códigos das situações do titular da P1.41
        // (64 -> 74) — os YAMLs novos da rodada 1 do CI total.
        assertThat(codes).containsExactly(
                "urn:ziipsan:problem:access-denied",
                "urn:ziipsan:problem:activation-exists",
                "urn:ziipsan:problem:activation-state",
                "urn:ziipsan:problem:alert-not-pending",
                "urn:ziipsan:problem:approval-required",
                "urn:ziipsan:problem:classe-inativa",
                "urn:ziipsan:problem:clause-map-frozen",
                "urn:ziipsan:problem:code",
                "urn:ziipsan:problem:confirmation-required",
                "urn:ziipsan:problem:content-too-large",
                "urn:ziipsan:problem:customer-deleted",
                "urn:ziipsan:problem:customer-has-active-link",
                "urn:ziipsan:problem:customer-without-document",
                "urn:ziipsan:problem:data-conflict",
                "urn:ziipsan:problem:deletion-unavailable",
                "urn:ziipsan:problem:draft-closed",
                "urn:ziipsan:problem:effects-pending",
                "urn:ziipsan:problem:evidence-link-exists",
                "urn:ziipsan:problem:filter-unavailable",
                "urn:ziipsan:problem:inactive-type",
                "urn:ziipsan:problem:internal-error",
                "urn:ziipsan:problem:invalid-cursor",
                "urn:ziipsan:problem:invalid-filter",
                "urn:ziipsan:problem:invalid-inscription",
                "urn:ziipsan:problem:invalid-page",
                "urn:ziipsan:problem:invalid-request",
                "urn:ziipsan:problem:invalid-resource",
                "urn:ziipsan:problem:invalid-sort",
                "urn:ziipsan:problem:invalid-transition",
                "urn:ziipsan:problem:invalid-version",
                "urn:ziipsan:problem:item-duplicado",
                "urn:ziipsan:problem:item-nao-aprovado",
                "urn:ziipsan:problem:item-nao-pendente",
                "urn:ziipsan:problem:merge-not-applied",
                "urn:ziipsan:problem:merge-reverse-missing",
                "urn:ziipsan:problem:merge-reverse-pending",
                "urn:ziipsan:problem:mfa-required",
                "urn:ziipsan:problem:name",
                "urn:ziipsan:problem:no-effect-handler",
                "urn:ziipsan:problem:no-verifier",
                "urn:ziipsan:problem:not-found",
                "urn:ziipsan:problem:not-pending",
                "urn:ziipsan:problem:not-pending-approval",
                "urn:ziipsan:problem:operational-types-disabled",
                "urn:ziipsan:problem:order-closed",
                "urn:ziipsan:problem:precondition-failed",
                "urn:ziipsan:problem:precondition-required",
                "urn:ziipsan:problem:record-already-deleted",
                "urn:ziipsan:problem:record-deleted",
                "urn:ziipsan:problem:resource",
                "urn:ziipsan:problem:safety-gate",
                "urn:ziipsan:problem:saved-view-title-in-use",
                "urn:ziipsan:problem:search-in-url",
                "urn:ziipsan:problem:split-link-changed",
                "urn:ziipsan:problem:split-not-applied",
                "urn:ziipsan:problem:split-reverse-missing",
                "urn:ziipsan:problem:split-reverse-pending",
                "urn:ziipsan:problem:stale-version",
                "urn:ziipsan:problem:state-conflict",
                "urn:ziipsan:problem:step",
                "urn:ziipsan:problem:table",
                "urn:ziipsan:problem:targets-frozen",
                "urn:ziipsan:problem:tenant-inactive",
                "urn:ziipsan:problem:tenant-mismatch",
                "urn:ziipsan:problem:tenant-missing",
                "urn:ziipsan:problem:tenant-not-allowed",
                "urn:ziipsan:problem:tipo-duplicado",
                "urn:ziipsan:problem:tipo-nao-aprovado",
                "urn:ziipsan:problem:tipo-nao-pendente",
                "urn:ziipsan:problem:tipo-nao-usavel",
                "urn:ziipsan:problem:too-many-requests",
                "urn:ziipsan:problem:unauthenticated",
                "urn:ziipsan:problem:user-required",
                "urn:ziipsan:problem:validation");
        int versions = queryInt(ProvisionedTenant.migratorDs,
                "SELECT count(*) FROM support.article_version v "
                        + "JOIN support.article a ON a.id = v.article_id "
                        + "WHERE v.version = a.current_version");
        assertThat(versions).as("toda artigo tem a versão corrente publicada").isEqualTo(3);
    }

    @Test
    @DisplayName("busca em português ignora acento (imovel encontra imóvel)")
    void portugueseSearchIgnoresAccents() throws SQLException {
        List<String> plain = searchSlugs("imovel");
        List<String> accented = searchSlugs("imóvel");
        assertThat(plain).contains("pesquisar-imoveis");
        assertThat(accented).contains("pesquisar-imoveis");
        assertThat(searchSlugs("territorio")).contains("consultar-territorio");
        assertThat(searchSlugs("zzz-inexistente")).isEmpty();
    }

    private static List<String> searchSlugs(String query) throws SQLException {
        String sql = """
                SELECT a.slug FROM support.article a
                JOIN support.article_version v ON v.article_id = a.id
                WHERE v.search_vector @@ plainto_tsquery('portuguese', public.immutable_unaccent(?))
                ORDER BY a.slug
                """;
        List<String> slugs = new ArrayList<>();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, query);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slugs.add(rs.getString(1));
                }
            }
        }
        return slugs;
    }

    @Test
    @DisplayName("feedback de um tenant é invisível para outro e não aceita tenant alheio")
    void feedbackIsolatedByTenant() throws SQLException {
        UUID articleId = queryUuid(ProvisionedTenant.migratorDs,
                "SELECT id FROM support.article WHERE slug = 'pesquisar-imoveis'");
        UUID otherTenant = UUID.randomUUID();
        try (Connection c = ProvisionedTenant.appDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + ProvisionedTenant.TENANT_ID + "', true)");
                st.execute("INSERT INTO support.article_feedback (tenant_id, article_id, user_id, helpful) "
                        + "VALUES ('" + ProvisionedTenant.TENANT_ID + "', '" + articleId + "', '"
                        + UUID.randomUUID() + "', TRUE)");
                // Outro tenant não vê o feedback (RLS), no mesmo banco.
                st.execute("SELECT set_config('app.current_tenant', '" + otherTenant + "', true)");
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM support.article_feedback")) {
                    rs.next();
                    assertThat(rs.getInt(1)).as("feedback de outro tenant visível").isZero();
                }
                // E não consegue gravar como se fosse o tenant alheio (WITH CHECK).
                assertThatThrownBy(() -> st.execute(
                        "INSERT INTO support.article_feedback (tenant_id, article_id, user_id, helpful) "
                                + "VALUES ('" + ProvisionedTenant.TENANT_ID + "', '" + articleId + "', '"
                                + UUID.randomUUID() + "', TRUE)"))
                        .isInstanceOf(SQLException.class);
            } finally {
                c.rollback();
            }
        }
    }

    private static List<String> queryStrings(javax.sql.DataSource ds, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    private static int queryInt(javax.sql.DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static UUID queryUuid(javax.sql.DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return (UUID) rs.getObject(1);
        }
    }
}
