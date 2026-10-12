package br.com.sigsan.app;

import br.com.sigsan.core.audit.AuditLog;
import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.core.migration.CoreMigrationRunner;
import br.com.sigsan.platform.tenant.KeycloakAdminService;
import br.com.sigsan.platform.tenant.TenantProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Instalação do zero, com todos os módulos no mesmo classpath.
 *
 * <p>Cada módulo isolado passa no build sem enxergar os outros. O que quebra produção é a
 * junção: migration que nunca roda num tenant, entidade sem tabela, coluna com tipo diferente.
 * Este teste faz o que o primeiro deploy faria: roda a migration de core num banco virgem,
 * provisiona um tenant (que aplica as migrations de todos os módulos) e valida o mapeamento
 * JPA de cada entidade contra as tabelas que de fato foram criadas.
 *
 * <p>Precisa de um PostgreSQL <b>virgem</b> com o init de segurança aplicado
 * ({@code infra/postgres/init}). Não use o banco de desenvolvimento: a migration de core roda
 * sobre {@code public}. Configuração: {@code SIGSAN_IT_JDBC_URL}, {@code SIGSAN_IT_DB_USER},
 * {@code SIGSAN_IT_DB_PASSWORD}, {@code SIGSAN_IT_MIGRATOR_USER},
 * {@code SIGSAN_IT_MIGRATOR_PASSWORD}.
 */
class FreshInstallTest {

    private static final String PUBLIC = "public";

    /**
     * Tabelas sem trigger de auditoria por desenho. tenant_metadata é configuração do próprio
     * tenant (V1_1); as demais são séries temporais de alto volume, reproduzíveis, onde auditar
     * cada linha em JSONB duplicaria o armazenamento sem valor. Toda adição aqui precisa de
     * justificativa equivalente.
     */
    private static final Set<String> NOT_AUDITED_BY_DESIGN = Set.of(
            "tenant_metadata", "scada_data_point", "epanet_node_result", "epanet_link_result");

    private static DataSource appDs;
    private static DataSource migratorDs;
    private static String url;
    private static String appUser;
    private static String appPassword;

    private static final UUID TENANT_ID = ProvisionedTenant.TENANT_ID;
    private static final String SCHEMA = ProvisionedTenant.SCHEMA;

    @BeforeAll
    static void installFromScratch() {
        // Banco virgem, migration de core e provisionamento de um tenant com todos os módulos
        // (compartilhado com os demais testes de integração; ver ProvisionedTenant).
        ProvisionedTenant.ensure();
        url = ProvisionedTenant.url;
        appUser = ProvisionedTenant.appUser;
        appPassword = ProvisionedTenant.appPassword;
        appDs = ProvisionedTenant.appDs;
        migratorDs = ProvisionedTenant.migratorDs;
    }

    @Test
    @DisplayName("a migration de core não vaza tabelas de tenant para o schema public")
    void coreMigrationDoesNotLeakTenantTablesIntoPublic() {
        // O Flyway varre o location de forma recursiva. Se o runner de core apontar para
        // db/migration em vez de db/migration/core, ele aplica as migrations de tenant em public.
        List<String> leaked = queryStrings(migratorDs,
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('users', 'roles', 'user_roles', 'consumer_units', "
                        + "'service_orders', 'quality_parameter', 'dma_district', 'waste_mtr')");
        assertThat(leaked).as("tabelas de tenant encontradas em public").isEmpty();
    }

    @Test
    @DisplayName("toda tabela de tenant tem RLS forçada, policy e trilha de auditoria")
    void everyTenantTableIsIsolatedAndAudited() {
        List<String> problems = new ArrayList<>();
        String sql = """
                SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,
                       EXISTS (SELECT 1 FROM pg_policies p
                               WHERE p.schemaname = n.nspname AND p.tablename = c.relname) AS has_policy,
                       EXISTS (SELECT 1 FROM pg_trigger t
                               WHERE t.tgrelid = c.oid AND t.tgname = 'audit_trigger') AS has_audit
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
                ORDER BY c.relname
                """;
        try (Connection c = migratorDs.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t = rs.getString(1);
                    if (!rs.getBoolean(2)) problems.add(t + ": RLS desligada");
                    if (!rs.getBoolean(3)) problems.add(t + ": RLS sem FORCE (o dono ignora a policy)");
                    if (!rs.getBoolean(4)) problems.add(t + ": sem policy");
                    if (!rs.getBoolean(5) && !NOT_AUDITED_BY_DESIGN.contains(t)) {
                        problems.add(t + ": sem trigger de auditoria");
                    }
                    if (rs.getBoolean(5) && NOT_AUDITED_BY_DESIGN.contains(t)) {
                        problems.add(t + ": série temporal auditada linha a linha (duplica o armazenamento)");
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        assertThat(problems).as("tabelas fora do padrão de isolamento/auditoria").isEmpty();
    }

    @Test
    @DisplayName("seed dos módulos usa o tenant provisionado e não fabrica dados de domínio")
    void moduleSeedsBelongToTheProvisionedTenantAndNothingIsFabricated() throws SQLException {
        // Dentro de uma migration não há contexto de tenant: current_tenant_id() é NULL. Os seeds
        // usam o placeholder tenant_id do Flyway. A leitura precisa do contexto, por causa do
        // FORCE ROW LEVEL SECURITY.
        try (Connection c = migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_ID + "', true)");

                // Dado que parece real e é inventado é pior que dado ausente: um DMA fabricado
                // alimentaria o balanço hídrico, uma classe de resíduo errada iria num MTR, e um VMP
                // errado declararia água conforme ou não conforme sem base na Portaria. O seed de
                // parâmetros de qualidade foi removido depois de conferido contra a norma: 16 de 28
                // linhas divergiam (docs/levantamento/09-regulatorio-qualidade-da-agua.md).
                for (String table : List.of("dma_district", "waste_class", "quality_parameter")) {
                    try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + SCHEMA + "." + table)) {
                        rs.next();
                        assertThat(rs.getInt(1))
                                .as("%s não pode vir com dados de exemplo no provisionamento", table)
                                .isZero();
                    }
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("toda entidade JPA bate com a tabela criada pelas migrations")
    void everyEntityMatchesTheDatabase() {
        Map<String, String> failures = new TreeMap<>();
        List<Class<?>> entities = scanEntities();
        assertThat(entities).as("entidades encontradas no classpath").hasSizeGreaterThan(20);

        for (Class<?> entity : entities) {
            // AuditLog vive em public (compartilhada); as demais, no schema do tenant.
            String schema = entity.equals(AuditLog.class) ? PUBLIC : SCHEMA;
            try {
                validate(entity, schema);
            } catch (Exception e) {
                failures.put(entity.getSimpleName(), rootMessage(e));
            }
        }

        StringBuilder report = new StringBuilder();
        failures.forEach((name, msg) -> report.append("\n  - ").append(name).append(": ").append(msg));
        assertThat(failures)
                .as("%d de %d entidades não batem com o banco:%s",
                        failures.size(), entities.size(), report)
                .isEmpty();
    }

    // ------------------------------------------------------------------------------------

    private static void validate(Class<?> entity, String schema) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.username", appUser)
                .applySetting("hibernate.connection.password", appPassword)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.default_schema", schema)
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .build();
        try {
            // As entidades alcançáveis por associação (@ManyToOne etc.) precisam entrar juntas,
            // senão o Hibernate recusa o mapeamento com "targets an unknown entity".
            MetadataSources sources = new MetadataSources(registry);
            associationClosure(entity).forEach(sources::addAnnotatedClass);
            sources.buildMetadata().buildSessionFactory().close();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static Set<Class<?>> associationClosure(Class<?> root) {
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Class<?> c = pending.poll();
            if (!seen.add(c)) continue;
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToOne.class)) {
                        pending.add(f.getType());
                    } else if (f.isAnnotationPresent(OneToMany.class) || f.isAnnotationPresent(ManyToMany.class)) {
                        if (f.getGenericType() instanceof ParameterizedType pt
                                && pt.getActualTypeArguments()[0] instanceof Class<?> target) {
                            pending.add(target);
                        }
                    }
                }
            }
        }
        return seen;
    }

    private static List<Class<?>> scanEntities() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> out = new ArrayList<>();
        scanner.findCandidateComponents("br.com.sigsan").forEach(bd -> {
            try {
                out.add(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        });
        return out;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            // Mantém a primeira causa que carrega uma mensagem de esquema legível.
            if (root.getMessage() != null && root.getMessage().contains("Schema-validation")) break;
            root = root.getCause();
        }
        String m = root.getMessage();
        return m == null ? root.getClass().getSimpleName() : m.replace('\n', ' ');
    }

    private static List<String> queryStrings(DataSource ds, String sql) {
        List<String> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(rs.getString(1));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }
}
