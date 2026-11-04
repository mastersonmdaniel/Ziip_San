package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.domain.JdbcTenantDomainStore;
import br.com.ziipsan.platform.domain.TenantDomainStore.DomainView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.27 (core V10_2_1) no PostgreSQL real: tabela, índices, RLS,
 * funções SECURITY DEFINER, parâmetro {@code brand.custom_domain} e
 * o store JDBC contra o banco provisionado.
 *
 * <p>Desvio documentado, provado na migration: a RLS de
 * {@code platform.tenant_domain} é ATIVADA mas NÃO FORCED — com FORCE,
 * a função SECURITY DEFINER pré-login não leria a tabela nem com
 * {@code SET LOCAL row_security = off} (PG 17 recusa para dono sem
 * BYPASSRLS). A propriedade que importa é comportamental e está
 * provada aqui: o papel da aplicação, sem contexto de tenant, enxerga
 * zero linhas; com contexto, só as do próprio tenant.
 */
class TenantDomainMigrationIT {

    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;
    private static final UUID ACTOR = UUID.randomUUID();
    private static final String HOST = "app.it-cliente.example";
    private static final String TOKEN_HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private static Connection migrator() throws SQLException {
        return ProvisionedTenant.migratorDs.getConnection();
    }

    private static Connection app() throws SQLException {
        return ProvisionedTenant.appDs.getConnection();
    }

    /** Conexão do migrador com o contexto do tenant (padrão dos ITs, para a RLS FORCED de settings). */
    private static Connection migratorAsTenant() throws SQLException {
        Connection c = migrator();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
        }
        return c;
    }

    private static void cleanupDomain(String hostname) throws SQLException {
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM platform.tenant_domain WHERE hostname = ?")) {
            ps.setString(1, hostname);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("tabela, RLS ativada (não FORCED, por desenho), policy e parâmetro brand.custom_domain existem")
    void schemaAndParameterExist() throws SQLException {
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT relrowsecurity, relforcerowsecurity FROM pg_class cl"
                             + " JOIN pg_namespace n ON n.oid = cl.relnamespace"
                             + " WHERE n.nspname = 'platform' AND cl.relname = 'tenant_domain'");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).as("RLS ativada").isTrue();
            assertThat(rs.getBoolean(2)).as("RLS não FORCED (função pré-login precisa ler como dono)").isFalse();
        }
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM pg_policies WHERE schemaname = 'platform'"
                             + " AND tablename = 'tenant_domain' AND policyname = 'tenant_domain_isolation'");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
        }
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT param_class, factory_default FROM public.parameter_definitions"
                             + " WHERE definition_key = 'brand.custom_domain'");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("d");
            assertThat(rs.getString(2)).isNull();
        }
    }

    @Test
    @DisplayName("funções pré-login existem; só VERIFIED resolve, e a marca vem com o slug do catálogo")
    void preloginFunctionsGateOnVerified() throws SQLException {
        cleanupDomain(HOST);
        // Marca do tenant para a projeção: slug + display_name (via migrador com contexto).
        try (Connection c = migratorAsTenant()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE public.tenant_catalog SET subdomain_slug = 'it-cliente' WHERE id = ?")) {
                ps.setObject(1, TENANT);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO public.tenant_settings (id, tenant_id, setting_key, setting_value, is_sensitive)"
                            + " VALUES (uuid_generate_v4(), ?, 'branding.display_name', '\"IT Cliente\"', false)"
                            + " ON CONFLICT (tenant_id, setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value")) {
                ps.setObject(1, TENANT);
                ps.executeUpdate();
            }
            c.commit();
        }
        try {
            preloginFunctionsGateOnVerifiedBody();
        } finally {
            // O tenant é compartilhado entre ITs da JVM: restaura slug e marca.
            try (Connection c = migratorAsTenant()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE public.tenant_catalog SET subdomain_slug = NULL WHERE id = ?")) {
                    ps.setObject(1, TENANT);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM public.tenant_settings WHERE tenant_id = ? AND setting_key = 'branding.display_name'")) {
                    ps.setObject(1, TENANT);
                    ps.executeUpdate();
                }
                c.commit();
            }
            cleanupDomain(HOST);
        }
    }

    private void preloginFunctionsGateOnVerifiedBody() throws SQLException {
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO platform.tenant_domain (tenant_id, hostname, status,"
                             + " verification_token_hash, cname_target, cert_status)"
                             + " VALUES (?, ?, 'PENDING_VERIFICATION', ?, 'edge.ziip.example', 'NONE')")) {
            ps.setObject(1, TENANT);
            ps.setString(2, HOST);
            ps.setString(3, TOKEN_HASH);
            ps.executeUpdate();
        }
        try (Connection c = app()) {
            assertThat(resolveHost(c, HOST)).isEmpty();
            assertThat(brandHost(c, HOST)).isNull();
        }
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE platform.tenant_domain SET status = 'VERIFIED', verified_at = NOW(),"
                             + " cname_ok = true, cert_status = 'ISSUED' WHERE hostname = ?")) {
            ps.setString(1, HOST);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }
        try (Connection c = app()) {
            assertThat(resolveHost(c, HOST)).contains(TENANT.toString());
            String brand = brandHost(c, HOST);
            assertThat(brand).contains("IT Cliente");
            assertThat(brand).contains("it-cliente");
        }
        // REVOKED volta a não resolver.
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE platform.tenant_domain SET status = 'REVOKED', cert_status = 'REVOKED'"
                             + " WHERE hostname = ?")) {
            ps.setString(1, HOST);
            ps.executeUpdate();
        }
        try (Connection c = app()) {
            assertThat(resolveHost(c, HOST)).isEmpty();
        }
        cleanupDomain(HOST);
    }

    @Test
    @DisplayName("unicidade parcial: hostname ativo recusa segundo pedido; após REMOVED, aceita")
    void partialUniqueIndex() throws SQLException {
        cleanupDomain(HOST);
        UUID otherTenant = UUID.randomUUID();
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, status)"
                             + " VALUES (?, ?, 'Outro', '99999999000199', 'ACTIVE')")) {
            ps.setObject(1, otherTenant);
            ps.setString(2, "it_outro_" + otherTenant.toString().replace("-", "").substring(0, 8));
            ps.executeUpdate();
        }
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO platform.tenant_domain (tenant_id, hostname, status,"
                             + " verification_token_hash, cname_target, cert_status)"
                             + " VALUES (?, ?, 'VERIFIED', ?, 'edge.ziip.example', 'ISSUED')")) {
            ps.setObject(1, TENANT);
            ps.setString(2, HOST);
            ps.setString(3, TOKEN_HASH);
            ps.executeUpdate();
        }
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO platform.tenant_domain (tenant_id, hostname, status,"
                             + " verification_token_hash, cname_target, cert_status)"
                             + " VALUES (?, ?, 'PENDING_VERIFICATION', ?, 'edge.ziip.example', 'NONE')")) {
            ps.setObject(1, otherTenant);
            ps.setString(2, HOST);
            ps.setString(3, TOKEN_HASH);
            assertThatThrownBy(ps::executeUpdate)
                    .hasMessageContaining("tenant_domain_hostname_active");
        }
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE platform.tenant_domain SET status = 'REMOVED', removed_at = NOW() WHERE hostname = ?")) {
            ps.setString(1, HOST);
            ps.executeUpdate();
        }
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO platform.tenant_domain (tenant_id, hostname, status,"
                             + " verification_token_hash, cname_target, cert_status)"
                             + " VALUES (?, ?, 'PENDING_VERIFICATION', ?, 'edge.ziip.example', 'NONE')")) {
            ps.setObject(1, otherTenant);
            ps.setString(2, HOST);
            ps.setString(3, TOKEN_HASH);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }
        cleanupDomain(HOST);
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement("DELETE FROM public.tenant_catalog WHERE id = ?")) {
            ps.setObject(1, otherTenant);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("CHECK do hostname recusa maiúsculas; store JDBC insere, resolve e recusa duplicata com 409")
    void hostnameCheckAndJdbcStore() throws SQLException {
        cleanupDomain(HOST);
        try (Connection c = migrator();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO platform.tenant_domain (tenant_id, hostname, status,"
                             + " verification_token_hash, cname_target, cert_status)"
                             + " VALUES (?, ?, 'PENDING_VERIFICATION', ?, 'edge.ziip.example', 'NONE')")) {
            ps.setObject(1, TENANT);
            ps.setString(2, "APP.IT-Cliente.EXAMPLE");
            ps.setString(3, TOKEN_HASH);
            assertThatThrownBy(ps::executeUpdate).hasMessageContaining("tenant_domain_hostname_check");
        }
        JdbcTenantDomainStore store = new JdbcTenantDomainStore(ProvisionedTenant.appDs);
        DomainView created = store.insert(TENANT, ACTOR, HOST, TOKEN_HASH, "edge.ziip.example");
        assertThat(created.status()).isEqualTo("PENDING_VERIFICATION");
        assertThatThrownBy(() -> store.insert(TENANT, ACTOR, HOST, TOKEN_HASH, "edge.ziip.example"))
                .isInstanceOf(ApiException.Conflict.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("data-conflict"));
        // Sem contexto de tenant, o papel da aplicação enxerga zero linhas da tabela.
        try (Connection c = app();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM platform.tenant_domain")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
        // Com contexto, só as do próprio tenant.
        try (Connection c = app()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
                try (ResultSet rs = st.executeQuery(
                        "SELECT count(*) FROM platform.tenant_domain WHERE hostname = '" + HOST + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
            c.rollback();
        }
        cleanupDomain(HOST);
    }

    private static java.util.Optional<String> resolveHost(Connection c, String host) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT tenant_id FROM public.tenant_domain_by_host(?)")) {
            ps.setString(1, host);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? java.util.Optional.of(rs.getString(1)) : java.util.Optional.empty();
            }
        }
    }

    private static String brandHost(Connection c, String host) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT public.tenant_brand_by_host(?)")) {
            ps.setString(1, host);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
