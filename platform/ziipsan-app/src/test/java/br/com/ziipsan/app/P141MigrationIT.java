package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.platform.tenant.TenantMigrator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.41 (V3_8) no PostgreSQL real, nos DOIS caminhos de ordenação:
 *
 * <p>1. Fresh install (tenant provisionado): a V3_8 já está aplicada entre
 * a V3_7 e a V3_9; as tabelas, colunas, CHECKs, trigger, RLS forçada,
 * regras de redação e definições de parâmetro existem.
 *
 * <p>2. Upgrade a partir de 3b0badd: um schema novo recebe as migrations de
 * tenant SEM a V3_8 (cópia da árvore de origem, mesma base do commit
 * 3b0badd — este worktree está limpo nessa base mais só a V3_8), recebe
 * dado legado (unidade sem usuário, unidade com usuário, unidade só com
 * proprietário) e então é migrado pelo TenantMigrator atual, que aplica a
 * V3_8 via outOfOrder, DEPOIS da V3_13. A prova é o backfill de ocupação e
 * a ausência de referência a objetos da V3_12 (a migration não quebraria
 * se quebrasse a ordem — ela roda nas duas).
 *
 * <p>3. O tipo HOLDER_DECEASED declarado com aprovação exige outra pessoa
 * no motor (submit → PENDING_APPROVAL; criador não aprova; outro aprova).
 */
class P141MigrationIT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private static Connection asTenant() throws SQLException {
        Connection c = ProvisionedTenant.migratorDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
            // O trigger de ocupação resolve unit_customers sem qualificar
            // (mesmo padrão da V3_12): sem o search_path do tenant, a
            // execução falha com 42P01. A app sempre o define assim
            // (MultiTenantConnectionProvider); o teste espelha o runtime.
            st.execute("SET LOCAL search_path TO " + SCHEMA + ", public");
        }
        return c;
    }

    private static boolean tableExists(Connection c, String schema, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection c, String schema, String table, String column) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND table_name = ?"
                        + " AND column_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean rlsForced(Connection c, String schema, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT relforcerowsecurity FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace"
                        + " WHERE n.nspname = ? AND cl.relname = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static UUID insertCustomer(Connection c, String schema, String personType, String name) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + schema + ".customers (id, tenant_id, person_type, name)"
                        + " VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setString(3, personType);
            ps.setString(4, name);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertUnit(Connection c, String schema, String registration) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + schema + ".consumer_units (id, tenant_id, registration_number, check_digit)"
                        + " VALUES (?, ?, ?, '0')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setString(3, registration);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertLinkWithRole(Connection c, String schema, UUID unitId, UUID customerId, String role)
            throws SQLException {
        UUID roleId = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM " + schema + ".relation_roles WHERE tenant_id = ? AND system_role = ? LIMIT 1")) {
            ps.setObject(1, TENANT);
            ps.setString(2, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    roleId = (UUID) rs.getObject(1);
                }
            }
        }
        if (roleId == null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + schema + ".relation_roles (id, tenant_id, code, name, system_role)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                roleId = UUID.randomUUID();
                ps.setObject(1, roleId);
                ps.setObject(2, TENANT);
                ps.setString(3, role);
                ps.setString(4, role);
                ps.setString(5, role);
                ps.executeUpdate();
            }
        }
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + schema + ".unit_customers (id, tenant_id, unit_id, customer_id, role,"
                        + " start_date, role_id, show_on_bill) VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, unitId);
            ps.setObject(4, customerId);
            ps.setString(5, role);
            ps.setObject(6, roleId);
            ps.setBoolean(7, "USER".equals(role));
            ps.executeUpdate();
        }
        return id;
    }

    @Test
    @DisplayName("fresh install: V3_8 aplicada com tabelas, colunas, RLS forçada e parâmetros")
    void freshInstallHasV38Objects() throws SQLException {
        try (Connection c = asTenant()) {
            for (String table : List.of("customer_representations", "holder_death_alerts",
                    "customer_document_validations", "holder_change_requests", "customer_merges",
                    "customer_splits")) {
                assertThat(tableExists(c, SCHEMA, table)).as("tabela " + table).isTrue();
                assertThat(rlsForced(c, SCHEMA, table)).as("RLS FORCE em " + table).isTrue();
            }
            assertThat(columnExists(c, SCHEMA, "customers", "social_name")).isTrue();
            assertThat(columnExists(c, SCHEMA, "consumer_units", "occupancy_status")).isTrue();
            assertThat(columnExists(c, SCHEMA, "unit_customers", "provisional")).isTrue();
            assertThat(columnExists(c, SCHEMA, "unit_customers", "provisional_kind")).isTrue();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM " + SCHEMA + ".flyway_schema_history"
                            + " WHERE installed_rank IS NOT NULL AND script LIKE 'V3_8%' AND success")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("V3_8 no histórico").isEqualTo(1);
                }
            }
            for (String key : List.of("registry.deceased_holder_policy", "registry.vacant_unit_holder",
                    "registry.representation_proof", "registry.social_name_display",
                    "registry.validate_document_online", "reg.cpf_validation_provider", "reg.deceased_check",
                    "portal.delegated_access", "bill.holder_name_display")) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM public.parameter_definitions WHERE definition_key = ?")) {
                    ps.setString(1, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).as("parâmetro " + key).isEqualTo(1);
                    }
                }
            }
            for (String[] rule : List.of(
                    new String[]{"customers", "social_name"},
                    new String[]{"customer_representations", "proof_document_id"},
                    new String[]{"holder_change_requests", "old_value"},
                    new String[]{"holder_change_requests", "new_value"})) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM public.audit_redaction_rule WHERE table_name = ? AND column_name = ?")) {
                    ps.setString(1, rule[0]);
                    ps.setString(2, rule[1]);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).as("redação " + rule[0] + "." + rule[1])
                                .isEqualTo(1);
                    }
                }
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("fresh install: CHECKs da V3_8 recusam PJ com nome social e VACANT com usuário")
    void freshInstallChecksRefuse() throws SQLException {
        try (Connection c = asTenant()) {
            UUID pj = insertCustomer(c, SCHEMA, "PJ", "Empresa de teste P141");
            var savepoint = c.setSavepoint();
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + SCHEMA + ".customers SET social_name = 'Social' WHERE id = ?")) {
                    ps.setObject(1, pj);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
            c.rollback(savepoint);
            UUID pf = insertCustomer(c, SCHEMA, "PF", "Titular de teste P141");
            UUID unit = insertUnit(c, SCHEMA, "P141-VACANT");
            insertLinkWithRole(c, SCHEMA, unit, pf, "USER");
            savepoint = c.setSavepoint();
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + SCHEMA + ".consumer_units SET occupancy_status = 'VACANT' WHERE id = ?")) {
                    ps.setObject(1, unit);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
            c.rollback(savepoint);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + SCHEMA + ".customers SET social_name = 'Social PF' WHERE id = ?")) {
                ps.setObject(1, pf);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("upgrade a partir de 3b0badd: V3_8 via outOfOrder + backfill de ocupação")
    void upgradeFrom3b0baddAppliesV38() throws Exception {
        String upgSchema = "it_upg" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID upgTenant = UUID.randomUUID();
        Path baseDir = Files.createTempDirectory("ziipsan-p141-base");
        try {
            List<String> locations = copyBaseMigrations(baseDir);
            try (Connection admin = ProvisionedTenant.migratorDs.getConnection();
                 Statement st = admin.createStatement()) {
                st.execute("CREATE SCHEMA " + upgSchema);
            }
            Flyway base = Flyway.configure()
                    .dataSource(ProvisionedTenant.migratorDs)
                    .schemas(upgSchema, "public")
                    .defaultSchema(upgSchema)
                    .createSchemas(false)
                    .placeholders(Map.of("tenant_id", upgTenant.toString()))
                    .locations(locations.toArray(new String[0]))
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .outOfOrder(true)
                    .table("flyway_schema_history")
                    .load();
            base.migrate();
            try (Connection check = ProvisionedTenant.migratorDs.getConnection();
                 PreparedStatement ps = check.prepareStatement(
                         "SELECT COUNT(*) FROM " + upgSchema + ".flyway_schema_history WHERE success");
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("migrations da base aplicadas").isPositive();
            }
            try (Connection check = ProvisionedTenant.migratorDs.getConnection()) {
                assertThat(columnExists(check, upgSchema, "consumer_units", "occupancy_status"))
                        .as("base NÃO tem occupancy_status (é da V3_8)").isFalse();
                assertThat(tableExists(check, upgSchema, "customer_representations"))
                        .as("base NÃO tem customer_representations (é da V3_8)").isFalse();
            }
            // Dado legado na base: unidade sem usuário, com usuário e só com proprietário.
            UUID pf;
            UUID owner;
            UUID unitNoUser;
            UUID unitWithUser;
            UUID unitOwnerOnly;
            try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    st.execute("SELECT set_config('app.current_tenant', '" + upgTenant + "', true)");
                    st.execute("SET LOCAL search_path TO " + upgSchema + ", public");
                }
                pf = insertCustomerFor(c, upgSchema, upgTenant, "PF", "Titular legado P141");
                owner = insertCustomerFor(c, upgSchema, upgTenant, "PF", "Proprietário legado P141");
                unitNoUser = insertUnitFor(c, upgSchema, upgTenant, "P141-UPG-NOUS");
                unitWithUser = insertUnitFor(c, upgSchema, upgTenant, "P141-UPG-USER");
                unitOwnerOnly = insertUnitFor(c, upgSchema, upgTenant, "P141-UPG-OWN");
                insertLinkWithRoleFor(c, upgSchema, upgTenant, unitWithUser, pf, "USER");
                insertLinkWithRoleFor(c, upgSchema, upgTenant, unitOwnerOnly, owner, "OWNER");
                c.commit();
            }
            // Upgrade: o migrador atual aplica só a V3_8 (outOfOrder, depois da V3_13).
            new TenantMigrator(ProvisionedTenant.migratorDs).migrate(upgSchema, upgTenant);
            try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
                // RLS FORÇADA vale também para o dono: sem o GUC do tenant,
                // as leituras por id voltariam vazias (não é dado ausente).
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    st.execute("SELECT set_config('app.current_tenant', '" + upgTenant + "', true)");
                    st.execute("SET LOCAL search_path TO " + upgSchema + ", public");
                }
                assertThat(columnExists(c, upgSchema, "consumer_units", "occupancy_status")).isTrue();
                assertThat(tableExists(c, upgSchema, "customer_representations")).isTrue();
                assertThat(occupancyOf(c, upgSchema, unitNoUser)).isEqualTo("VACANT");
                assertThat(occupancyOf(c, upgSchema, unitWithUser)).isEqualTo("OCCUPIED");
                assertThat(occupancyOf(c, upgSchema, unitOwnerOnly)).isEqualTo("VACANT");
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM " + upgSchema + ".flyway_schema_history"
                                + " WHERE script LIKE 'V3_8%' AND success")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    }
                }
            }
        } finally {
            try (Connection admin = ProvisionedTenant.migratorDs.getConnection();
                 Statement st = admin.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS " + upgSchema + " CASCADE");
            } catch (SQLException ignored) {
                // Schema descartável de teste; a JVM descarta o banco ao fim.
            }
        }
    }

    @Test
    @DisplayName("motor: HOLDER_DECEASED com aprovação exige outra pessoa")
    void holderDeceasedRequiresAnotherPerson() {
        ServiceOrderService engine = new ServiceOrderService(ProvisionedTenant.appDs, SCHEMA, TENANT);
        UUID creator = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        try {
            engine.findTypeByCode("HOLDER_DECEASED");
        } catch (ApiException.NotFound e) {
            engine.declareType(new ServiceOrderService.ServiceTypeInput("HOLDER_DECEASED",
                    "OS interna do cadastro: HOLDER_DECEASED", "HOLDER_DECEASED", "HOLDER_DECEASED", null,
                    ServiceOrderEnums.TypeIndicator.INTERNAL, true, false, null,
                    List.of(TargetType.CONSUMER_UNIT), null, null, null, false, false, false, false,
                    List.of()), creator);
        }
        UUID orderId = engine.createOrder(new ServiceOrderService.OrderInput("HOLDER_DECEASED",
                ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, creator, null, null, null, null)).id();
        assertThat(engine.submit(orderId, creator).status()).isEqualTo(ServiceOrderStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> engine.approve(orderId, creator))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(engine.approve(orderId, approver).status()).isEqualTo(ServiceOrderStatus.OPEN);
        assertThat(engine.execute(orderId, approver).status()).isEqualTo(ServiceOrderStatus.EXECUTED);
        assertThat(engine.closeExecuted(orderId, approver).status())
                .isEqualTo(ServiceOrderStatus.CLOSED_EXECUTED);
    }

    // ---------------------------------------------------------- apoio do upgrade

    private static List<String> copyBaseMigrations(Path baseDir) throws IOException {
        Path repoRoot = Path.of("..", "..").toAbsolutePath().normalize();
        List<String> locations = new ArrayList<>();
        List<Path> moduleDirs = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream.filter(p -> p.endsWith("db/migration/tenant") && Files.isDirectory(p)
                            && !p.toString().contains("/target/")
                            && !p.toString().contains("/node_modules/")
                            && !p.toString().contains("/.git/"))
                    .forEach(moduleDirs::add);
        }
        assertThat(moduleDirs).as("diretórios db/migration/tenant na árvore de origem").isNotEmpty();
        for (Path moduleDir : moduleDirs) {
            Path dest = baseDir.resolve("m" + moduleDirs.indexOf(moduleDir));
            Files.createDirectories(dest);
            try (Stream<Path> files = Files.list(moduleDir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                        .filter(p -> !p.getFileName().toString().startsWith("V3_8__"))
                        .forEach(p -> {
                            try {
                                Files.copy(p, dest.resolve(p.getFileName().toString()));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            locations.add("filesystem:" + dest);
        }
        return locations;
    }

    private static UUID insertCustomerFor(Connection c, String schema, UUID tenant, String personType, String name)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + schema + ".customers (id, tenant_id, person_type, name)"
                        + " VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setString(3, personType);
            ps.setString(4, name);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertUnitFor(Connection c, String schema, UUID tenant, String registration)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + schema + ".consumer_units (id, tenant_id, registration_number, check_digit)"
                        + " VALUES (?, ?, ?, '0')")) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setString(3, registration);
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertLinkWithRoleFor(Connection c, String schema, UUID tenant, UUID unitId,
                                              UUID customerId, String role) throws SQLException {
        UUID roleId = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM " + schema + ".relation_roles WHERE tenant_id = ? AND system_role = ? LIMIT 1")) {
            ps.setObject(1, tenant);
            ps.setString(2, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    roleId = (UUID) rs.getObject(1);
                }
            }
        }
        if (roleId == null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + schema + ".relation_roles (id, tenant_id, code, name, system_role)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                roleId = UUID.randomUUID();
                ps.setObject(1, roleId);
                ps.setObject(2, tenant);
                ps.setString(3, role);
                ps.setString(4, role);
                ps.setString(5, role);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + schema + ".unit_customers (id, tenant_id, unit_id, customer_id, role,"
                        + " start_date, role_id, show_on_bill) VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setObject(3, unitId);
            ps.setObject(4, customerId);
            ps.setString(5, role);
            ps.setObject(6, roleId);
            ps.setBoolean(7, "USER".equals(role));
            ps.executeUpdate();
        }
    }

    private static String occupancyOf(Connection c, String schema, UUID unitId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT occupancy_status FROM " + schema + ".consumer_units WHERE id = ?")) {
            ps.setObject(1, unitId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }
}
