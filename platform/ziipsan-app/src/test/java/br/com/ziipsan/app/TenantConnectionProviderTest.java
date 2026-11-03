package br.com.ziipsan.app;

import br.com.ziipsan.commercial.registry.domain.Locality;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.tenant.MultiTenantConnectionProvider;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import br.com.ziipsan.platform.tenant.TenantSchemaResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O caminho de produção do isolamento: Hibernate com o {@link MultiTenantConnectionProvider} e o
 * {@link TenantSchemaResolver} reais, sem nenhuma GUC definida à mão pelo teste.
 *
 * <p>Achado C2 do PDCA de 12/10/2026: o provider só trocava o schema e nada definia
 * app.current_tenant. Com RLS FORCE, toda leitura via JPA voltaria vazia e toda gravação seria
 * barrada; os testes de RLS existentes passavam porque definiam a GUC eles mesmos.
 *
 * <p>Dois tenants: o de {@link ProvisionedTenant} (A) e um segundo provisionado aqui (B). O pool
 * tem UMA conexão, então toda sessão reaproveita a mesma conexão física: se o contexto de um tenant
 * vazasse para a próxima sessão, os testes veriam.
 */
class TenantConnectionProviderTest {

    private static final UUID TENANT_B = UUID.randomUUID();
    private static final String SCHEMA_B = "it_b_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    /** bcrypt válido de teste (não é senha de ninguém). */

    private static HikariDataSource pool;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void setUp() throws SQLException {
        ProvisionedTenant.ensure();

        var result = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(AuditLogRepository.class),
                ProvisionedTenant.migratorDs)
                .provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                        .tenantId(TENANT_B)
                        .schemaName(SCHEMA_B)
                        .displayName("Segundo tenant de integracao")
                        .cnpj("98765432000110")
                        .email("b@teste.com.br")
                        .plan("PRO")
                        .build());
        assertThat(result.success()).as("provisionamento do tenant B: %s", result.error()).isTrue();

        // Uma localidade em cada tenant, gravada pelo migrador com a GUC do respectivo tenant.
        insertLocality(ProvisionedTenant.SCHEMA, ProvisionedTenant.TENANT_ID, 901, "Localidade do tenant A");
        insertLocality(SCHEMA_B, TENANT_B, 902, "Localidade do tenant B");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(ProvisionedTenant.url);
        config.setUsername(ProvisionedTenant.appUser);
        config.setPassword(ProvisionedTenant.appPassword);
        config.setMaximumPoolSize(1);
        pool = new HikariDataSource(config);

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.multi_tenant_connection_provider", new MultiTenantConnectionProvider(pool))
                .applySetting("hibernate.tenant_identifier_resolver", new TenantSchemaResolver())
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(Locality.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (pool != null) {
            pool.close();
        }
        try (Connection c = ProvisionedTenant.migratorDs.getConnection(); Statement st = c.createStatement()) {
            if (SCHEMA_B.startsWith("it_b_")) {
                st.execute("DROP SCHEMA IF EXISTS " + SCHEMA_B + " CASCADE");
                // Apagar a linha do catálogo junto: o DatabaseMigrator do perfil
                // migrate percorre todo tenant ACTIVE e roda Flyway no schema dele;
                // deixar a linha sem o schema quebra teste migrate posterior.
                st.execute("DELETE FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA_B + "'");
            }
        }
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("com o tenant A no contexto, o Hibernate só lê as linhas de A")
    void tenantAReadsOnlyItsRows() {
        useTenant(ProvisionedTenant.SCHEMA, ProvisionedTenant.TENANT_ID);
        List<Locality> rows = readAll();
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(l -> assertThat(l.getTenantId()).isEqualTo(ProvisionedTenant.TENANT_ID));
        assertThat(rows).extracting(Locality::getCode).contains(901).doesNotContain(902);
    }

    @Test
    @DisplayName("com o tenant B no contexto, o Hibernate só lê as linhas de B, na mesma conexão física")
    void tenantBReadsOnlyItsRows() {
        useTenant(ProvisionedTenant.SCHEMA, ProvisionedTenant.TENANT_ID);
        readAll(); // usa a única conexão do pool com o tenant A

        useTenant(SCHEMA_B, TENANT_B);
        List<Locality> rows = readAll();
        assertThat(rows).extracting(Locality::getCode).containsExactly(902);
        assertThat(rows).allSatisfy(l -> assertThat(l.getTenantId()).isEqualTo(TENANT_B));
    }

    @Test
    @DisplayName("sem tenant no contexto, a sessão não alcança nenhuma tabela de tenant")
    void noTenantReachesNoTenantTable() {
        // Sem tenant o search_path fica só em public, onde não há tabela de negócio, e a GUC fica
        // vazia (a RLS não devolveria nada mesmo que a tabela fosse alcançada).
        assertThatThrownBy(TenantConnectionProviderTest::readAll)
                .hasStackTraceContaining("does not exist");
    }

    @Test
    @DisplayName("com o tenant A no contexto, gravar uma linha com tenant_id de B é barrado pela RLS")
    void crossTenantWriteIsRejected() {
        useTenant(ProvisionedTenant.SCHEMA, ProvisionedTenant.TENANT_ID);
        Locality intrusa = new Locality();
        intrusa.setTenantId(TENANT_B);
        intrusa.setCode(990);
        intrusa.setName("Linha de B gravada no contexto de A");
        assertThatThrownBy(() -> {
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();
                session.persist(intrusa);
                session.getTransaction().commit();
            }
        }).hasStackTraceContaining("row-level security");
    }

    @Test
    @DisplayName("a conexão volta ao pool sem o contexto do tenant anterior")
    void connectionIsCleanAfterRelease() throws SQLException {
        useTenant(SCHEMA_B, TENANT_B);
        readAll();

        // Pool de uma conexão: esta é a mesma conexão física que a sessão acabou de usar.
        try (Connection c = pool.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT coalesce(current_setting('app.current_tenant', true), ''), current_setting('search_path')")) {
            rs.next();
            assertThat(rs.getString(1)).as("app.current_tenant depois da devolução").isEmpty();
            assertThat(rs.getString(2)).as("search_path depois da devolução").isEqualTo("public");
        }
    }

    @Test
    @DisplayName("gravação via Hibernate gera auditoria com o tenant certo, sem GUC manual")
    void writeIsAuditedWithTheRightTenant() throws SQLException {
        useTenant(ProvisionedTenant.SCHEMA, ProvisionedTenant.TENANT_ID);
        Locality nova = new Locality();
        nova.setTenantId(ProvisionedTenant.TENANT_ID);
        nova.setCode(903);
        nova.setName("Gravada pelo Hibernate");
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(nova);
            session.getTransaction().commit();
        }

        try (Connection c = ProvisionedTenant.appDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + ProvisionedTenant.TENANT_ID + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT tenant_id FROM public.audit_log WHERE entity_type = 'localities' AND entity_id = ?")) {
                ps.setObject(1, nova.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("linha de auditoria da localidade gravada").isTrue();
                    assertThat(rs.getObject(1, UUID.class)).isEqualTo(ProvisionedTenant.TENANT_ID);
                }
            }
            c.rollback();
        }
    }

    private static void useTenant(String schema, UUID tenantId) {
        TenantContextHolder.setTenant(schema);
        TenantContextHolder.setTenantId(tenantId);
    }

    private static List<Locality> readAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createSelectionQuery("from Locality", Locality.class).getResultList();
        }
    }

    private static void insertLocality(String schema, UUID tenantId, int code, String name) throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + tenantId + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + schema + ".localities (tenant_id, code, name) VALUES (?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setInt(2, code);
                ps.setString(3, name);
                ps.executeUpdate();
            }
            c.commit();
        }
    }
}
