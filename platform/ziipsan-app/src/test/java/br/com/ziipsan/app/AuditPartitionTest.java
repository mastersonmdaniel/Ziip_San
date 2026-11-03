package br.com.ziipsan.app;

import br.com.ziipsan.core.audit.AuditLog;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.platform.audit.AuditTrailExportService;
import br.com.ziipsan.platform.audit.AuditTrailExportService.ExportRow;
import br.com.ziipsan.platform.audit.AuditTrailExportService.Format;
import br.com.ziipsan.platform.audit.AuditTrailIntegrityService;
import br.com.ziipsan.platform.audit.AuditTrailIntegrityService.VerificationStatus;
import br.com.ziipsan.platform.infra.storage.LocalFileObjectStorage;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.7 — partição mensal do audit_log (core V14; §3 reservava V7, ocupada
 * pela F21 — desvio registrado na mensagem do commit).
 *
 * <p>Precisa do PostgreSQL virgem de integração (ver ProvisionedTenant):
 * a migration de core roda sobre {@code public}. Cada cenário usa o
 * próprio tenant, porque a trilha é global em public e os testes da JVM a
 * compartilham. Dados 100% sintéticos; o único "IP" é da faixa TEST-NET-3
 * (203.0.113.0/24, reservada para documentação), usada para provar que a
 * exportação o omite (RNF-52).
 */
class AuditPartitionTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID TENANT_F = UUID.randomUUID();
    private static final UUID TENANT_C = UUID.randomUUID();
    private static final UUID TENANT_D = UUID.randomUUID();
    private static final UUID TENANT_E = UUID.randomUUID();
    private static final UUID TENANT_G = UUID.randomUUID();
    private static final UUID USER_1 = UUID.randomUUID();
    private static final UUID USER_2 = UUID.randomUUID();

    private static final OffsetDateTime JAN_10 = at(2024, 1, 10, 12);
    private static final OffsetDateTime JAN_20 = at(2024, 1, 20, 12);
    private static final OffsetDateTime JAN_25 = at(2024, 1, 25, 12);
    private static final OffsetDateTime FEB_05 = at(2024, 2, 5, 12);
    private static final OffsetDateTime FEB_10 = at(2024, 2, 10, 12);

    private static DataSource migratorDs;
    private static DataSource appDs;
    private static LocalFileObjectStorage storage;
    private static Path storageRoot;
    private static UUID pdalEntityId;

    @BeforeAll
    static void setUp() throws Exception {
        ProvisionedTenant.ensure();
        migratorDs = ProvisionedTenant.migratorDs;
        appDs = ProvisionedTenant.appDs;
        storageRoot = Files.createDirectories(
                Path.of("/var/tmp", "ziipsan-p1-audit-" + UUID.randomUUID().toString().substring(0, 8)));
        storage = new LocalFileObjectStorage(storageRoot);

        callFunction("SELECT public.audit_log_ensure_partitions(1)");
        callFunction("SELECT public.audit_log_create_partition(DATE '2024-01-01')");
        callFunction("SELECT public.audit_log_create_partition(DATE '2024-02-01')");

        // Tenant A: janeiro (2) + fevereiro (1, com IP de teste).
        insertAudit(TENANT_A, JAN_10, "p1_audit_probe", "CREATE", USER_1, null);
        insertAudit(TENANT_A, JAN_20, "p1_audit_probe", "UPDATE", USER_1, null);
        insertAudit(TENANT_A, FEB_05, "p1_audit_probe", "READ", USER_2, "203.0.113.7");
        // Tenant B/F: retenção + expurgo. Tenant C: sem política (nunca expurgado).
        insertAudit(TENANT_B, JAN_10, "p1_audit_probe", "CREATE", USER_1, null);
        insertAudit(TENANT_B, FEB_10, "p1_audit_probe", "CREATE", USER_1, null);
        insertAudit(TENANT_F, JAN_10, "p1_audit_probe", "CREATE", USER_1, null);
        insertAudit(TENANT_F, FEB_10, "p1_audit_probe", "CREATE", USER_1, null);
        insertAudit(TENANT_C, JAN_10, "p1_audit_probe", "CREATE", USER_1, null);
        // Tenant D: dois eventos no mesmo dia, para selar a raiz diária.
        insertAudit(TENANT_D, at(2024, 1, 10, 8), "p1_audit_probe", "CREATE", USER_1, null);
        insertAudit(TENANT_D, at(2024, 1, 10, 9, 30), "p1_audit_probe", "UPDATE", USER_1, null);
        // Tenant E: exportação (um evento com IP + agente, que a exportação omite).
        insertAudit(TENANT_E, JAN_10, "p1_audit_probe", "CREATE", USER_1, "203.0.113.7");
        insertAudit(TENANT_E, FEB_05, "p1_audit_probe", "READ", USER_2, null);
        asTenant(TENANT_E, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE public.audit_log SET user_agent = 'SyntheticAgent/1.0'"
                            + " WHERE tenant_id = ? AND created_at = ?")) {
                ps.setObject(1, TENANT_E);
                ps.setObject(2, java.sql.Timestamp.from(JAN_10.toInstant()));
                ps.executeUpdate();
            }
            return null;
        });
        // Uma linha de acesso a dado pessoal no schema do tenant provisionado.
        pdalEntityId = UUID.randomUUID();
        try (Connection c = migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + ProvisionedTenant.TENANT_ID + "', true)");
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + ProvisionedTenant.SCHEMA + ".personal_data_access_log"
                                + " (tenant_id, user_id, accessed_at, access_type, entity_type, entity_id,"
                                + " document_hash, result) VALUES (?, ?, ?, 'DOCUMENT_SEARCH', 'customers', ?, 'synthetic-blind-index', 'FOUND')")) {
                    ps.setObject(1, ProvisionedTenant.TENANT_ID);
                    ps.setObject(2, USER_1);
                    ps.setObject(3, java.sql.Timestamp.from(at(2024, 1, 15, 10).toInstant()));
                    ps.setObject(4, pdalEntityId);
                    ps.executeUpdate();
                }
            }
            c.commit();
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (migratorDs == null) {
            // O setUp falhou antes de atribuir os DataSources (ex.: migration
            // com erro): nao ha o que limpar, e o NPE aqui esconderia a causa.
            return;
        }
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            for (UUID tenant : List.of(TENANT_A, TENANT_B, TENANT_F, TENANT_C, TENANT_D, TENANT_E, TENANT_G)) {
                st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
                st.execute("DELETE FROM public.audit_log WHERE tenant_id = '" + tenant + "'");
            }
            st.execute("DELETE FROM public.audit_retention_policy WHERE tenant_id IN ('"
                    + TENANT_B + "', '" + TENANT_F + "')");
            st.execute("SELECT set_config('app.current_tenant', '" + ProvisionedTenant.TENANT_ID + "', true)");
            st.execute("DELETE FROM " + ProvisionedTenant.SCHEMA + ".personal_data_access_log"
                    + " WHERE entity_id = '" + pdalEntityId + "'");
            c.commit();
            for (String partition : List.of("audit_log_2024_01", "audit_log_2024_02", "audit_log_2024_05")) {
                try {
                    st.execute("DROP TABLE IF EXISTS public." + partition);
                } catch (SQLException ignored) {
                    // Partição pode ter linhas de outro teste da JVM; deixar para o banco descartável.
                }
            }
        } catch (SQLException ignored) {
            // Banco descartável; a limpeza é melhor esforço.
        }
        if (storageRoot != null) {
            try (var walk = Files.walk(storageRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Melhor esforço.
                    }
                });
            }
        }
    }

    @Test
    @DisplayName("estrutura: particionada por RANGE, PK (created_at, id), id com default uuidv7()")
    void partitionedStructure() throws SQLException {
        try (Connection c = migratorDs.getConnection();
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM pg_partitioned_table WHERE partrelid = 'public.audit_log'::regclass")) {
                rs.next();
                assertThat(rs.getInt(1)).isOne();
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                            + " WHERE conrelid = 'public.audit_log'::regclass AND contype = 'p'")) {
                rs.next();
                assertThat(rs.getString(1)).contains("PRIMARY KEY (created_at, id)");
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT column_default FROM information_schema.columns"
                            + " WHERE table_schema = 'public' AND table_name = 'audit_log' AND column_name = 'id'")) {
                rs.next();
                assertThat(rs.getString(1)).contains("uuidv7()");
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM pg_inherits WHERE inhparent = 'public.audit_log'::regclass"
                            + " AND inhrelid IN ('public.audit_log_2024_01'::regclass,"
                            + " 'public.audit_log_2024_02'::regclass)")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    @DisplayName("caminho do trigger: linha sem id nasce UUIDv7")
    void triggerPathIdIsUuidV7() throws SQLException {
        UUID id = asTenant(TENANT_A, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, created_at)"
                            + " VALUES (?, 'p1_audit_probe', ?, 'CREATE', ?) RETURNING id")) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, UUID.randomUUID());
                ps.setObject(3, java.sql.Timestamp.from(JAN_25.toInstant()));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return (UUID) rs.getObject(1);
                }
            }
        });
        assertThat(id.toString().charAt(14)).isEqualTo('7');
    }

    @Test
    @DisplayName("consulta por periodo poda particoes: plano so escaneia o mes pedido")
    void periodQueryPrunesPartitions() throws SQLException {
        // Literais, nao parametros: a poda em tempo de planejamento so acontece
        // com constantes; com parametros, a poda e em runtime (o plano listaria
        // todas as particoes candidatas). Os valores sao constantes do teste.
        String plan;
        try (Connection c = migratorDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "EXPLAIN (COSTS OFF) SELECT id FROM public.audit_log"
                             + " WHERE tenant_id = '" + TENANT_A + "'"
                             + " AND created_at >= TIMESTAMPTZ '2024-02-01 00:00:00+00'"
                             + " AND created_at < TIMESTAMPTZ '2024-03-01 00:00:00+00'")) {
            StringBuilder out = new StringBuilder();
            while (rs.next()) {
                out.append(rs.getString(1)).append('\n');
            }
            plan = out.toString();
        }
        assertThat(plan)
                .contains("audit_log_2024_02")
                .doesNotContain("audit_log_2024_01");
        List<UUID> ids = asTenant(TENANT_A, connection -> {
            List<UUID> found = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id FROM public.audit_log"
                            + " WHERE tenant_id = ? AND created_at >= ? AND created_at < ?")) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, java.sql.Timestamp.from(at(2024, 2, 1, 0).toInstant()));
                ps.setObject(3, java.sql.Timestamp.from(at(2024, 3, 1, 0).toInstant()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        found.add((UUID) rs.getObject(1));
                    }
                }
            }
            return found;
        });
        assertThat(ids).hasSize(1);
    }

    @Test
    @DisplayName("retencao: piso de 6 meses, so amplia, cert so amplia, expurgo por corte")
    void retentionFloorMonotonicityAndPurge() throws SQLException {
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.execute(
                    "SELECT public.audit_log_set_retention('" + TENANT_B + "', 3)"))
                    .hasMessageContaining("6 meses");
            st.execute("SELECT public.audit_log_set_retention('" + TENANT_B + "', 12)");
            assertThatThrownBy(() -> st.execute(
                    "SELECT public.audit_log_set_retention('" + TENANT_B + "', 6)"))
                    .hasMessageContaining("so amplia");
            st.execute("SELECT public.audit_log_set_retention('" + TENANT_B + "', 12, 2)");
            try (ResultSet rs = st.executeQuery(
                    "SELECT public.audit_log_effective_retention_months('" + TENANT_B + "')")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(24);
            }
            assertThatThrownBy(() -> st.execute(
                    "SELECT public.audit_log_set_retention('" + TENANT_B + "', 24, 1)"))
                    .hasMessageContaining("so amplia");
            st.execute("SELECT public.audit_log_set_retention('" + TENANT_B + "', 24, 2)");
            st.execute("SELECT public.audit_log_set_retention('" + TENANT_F + "', 6)");

            long purged;
            try (ResultSet rs = st.executeQuery(
                    "SELECT public.audit_log_purge_expired(TIMESTAMPTZ '2024-08-01 00:00:00+00')")) {
                rs.next();
                purged = rs.getLong(1);
            }
            // Tenant F (6 meses, corte 2024-02-01): janeiro some, fevereiro fica.
            // Tenant B (24 meses, corte 2022-08-01) e C (sem politica) ficam intactos.
            assertThat(purged).isEqualTo(1);
            assertThat(countAudit(TENANT_F)).isEqualTo(1);
            assertThat(countAudit(TENANT_B)).isEqualTo(2);
            assertThat(countAudit(TENANT_C)).isEqualTo(1);
            int januaryLeft = asTenant(TENANT_F, connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT count(*) FROM public.audit_log WHERE tenant_id = ?"
                                + " AND created_at < ?")) {
                    ps.setObject(1, TENANT_F);
                    ps.setObject(2, java.sql.Timestamp.from(at(2024, 2, 1, 0).toInstant()));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getInt(1);
                    }
                }
            });
            assertThat(januaryLeft).isZero();
        }
    }

    @Test
    @DisplayName("mes sem particao falha em vez de esconder a falta; criar resolve")
    void missingPartitionFailsUntilCreated() throws SQLException {
        assertThatThrownBy(() -> insertAudit(TENANT_A, at(2024, 5, 15, 12),
                "p1_audit_probe", "CREATE", USER_1, null))
                .hasMessageContaining("no partition of relation");
        callFunction("SELECT public.audit_log_create_partition(DATE '2024-05-01')");
        insertAudit(TENANT_A, at(2024, 5, 15, 12), "p1_audit_probe", "CREATE", USER_1, null);
        int mayRows = asTenant(TENANT_A, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT count(*) FROM public.audit_log WHERE tenant_id = ?"
                            + " AND created_at >= ? AND created_at < ?")) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, java.sql.Timestamp.from(at(2024, 5, 1, 0).toInstant()));
                ps.setObject(3, java.sql.Timestamp.from(at(2024, 6, 1, 0).toInstant()));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
        assertThat(mayRows).isOne();
    }

    @Test
    @DisplayName("integridade: selar, conferir, re-selar recusado, adulteracao detectada")
    void integritySealVerifyTamper() throws Exception {
        var service = new AuditTrailIntegrityService(appDs);
        var day = java.time.LocalDate.of(2024, 1, 10);
        var before = service.verifyDay(TENANT_D, day, storage, "audit-roots-test");
        assertThat(before.status()).isEqualTo(VerificationStatus.MISSING_ROOT);

        var sealed = service.sealDay(TENANT_D, day, storage, "audit-roots-test");
        assertThat(sealed.rowCount()).isEqualTo(2);
        assertThat(service.verifyDay(TENANT_D, day, storage, "audit-roots-test").status())
                .isEqualTo(VerificationStatus.MATCH);

        assertThatThrownBy(() -> service.sealDay(TENANT_D, day, storage, "audit-roots-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ja selada");

        // Acesso privilegiado adultera uma linha do dia selado.
        asTenant(TENANT_D, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE public.audit_log SET success = false"
                            + " WHERE tenant_id = ? AND created_at >= ? AND created_at < ?")) {
                ps.setObject(1, TENANT_D);
                ps.setObject(2, java.sql.Timestamp.from(at(2024, 1, 10, 0).toInstant()));
                ps.setObject(3, java.sql.Timestamp.from(at(2024, 1, 11, 0).toInstant()));
                ps.executeUpdate();
            }
            return null;
        });
        var after = service.verifyDay(TENANT_D, day, storage, "audit-roots-test");
        assertThat(after.status()).isEqualTo(VerificationStatus.MISMATCH);
        assertThat(after.computedRoot()).isNotEqualTo(after.storedRoot());
    }

    @Test
    @DisplayName("SIEM: JSONL e CEF so com metadados, sem IP/agente/payload; desligado recusa")
    void siemExport() throws Exception {
        var service = new AuditTrailExportService(appDs);
        List<ExportRow> rows = service.readTrail(TENANT_E, null,
                at(2024, 1, 1, 0), at(2024, 3, 1, 0));
        assertThat(rows).hasSize(2);

        String jsonl = service.format(rows, Format.JSONL);
        assertThat(jsonl.trim().split("\n")).hasSize(2);
        assertThat(jsonl)
                .contains("\"entity_type\":\"p1_audit_probe\"")
                .doesNotContain("ip_address")
                .doesNotContain("user_agent")
                .doesNotContain("203.0.113.7")
                .doesNotContain("SyntheticAgent")
                .doesNotContain("old_values")
                .doesNotContain("new_values");

        String cef = service.format(rows, Format.CEF);
        assertThat(cef).contains("CEF:0|Ziip|ZiipSAN|1.0|AUDIT|")
                .doesNotContain("203.0.113.7");

        List<ExportRow> withPdal = service.readTrail(ProvisionedTenant.TENANT_ID, ProvisionedTenant.SCHEMA,
                at(2024, 1, 1, 0), at(2024, 2, 1, 0));
        assertThat(withPdal)
                .filteredOn(r -> r.source().equals("personal_data_access_log"))
                .filteredOn(r -> r.entityId().equals(pdalEntityId))
                .hasSize(1);

        assertThatThrownBy(() -> service.deliver(TENANT_E, null,
                at(2024, 1, 1, 0), at(2024, 3, 1, 0), Format.JSONL, false, storage, "siem-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("log.export_enabled");
        String key = service.deliver(TENANT_E, null,
                at(2024, 1, 1, 0), at(2024, 3, 1, 0), Format.JSONL, true, storage, "siem-test");
        assertThat(storage.exists("siem-test", key)).isTrue();
        try (var in = storage.get("siem-test", key).orElseThrow()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).doesNotContain("203.0.113.7");
        }
    }

    @Test
    @DisplayName("JPA: validate passa com PK (created_at, id), persist gera UUIDv7, consulta por usuario+periodo")
    void jpaPersistAndUserPeriodQuery() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", ProvisionedTenant.url)
                .applySetting("hibernate.connection.username", ProvisionedTenant.appUser)
                .applySetting("hibernate.connection.password", ProvisionedTenant.appPassword)
                .applySetting("hibernate.default_schema", "public")
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            sources.addAnnotatedClass(AuditLog.class);
            var sessionFactory = sources.buildMetadata().buildSessionFactory();
            try {
                EntityManager em = sessionFactory.createEntityManager();
                em.getTransaction().begin();
                try {
                    em.unwrap(Session.class).doWork(connection -> {
                        try (PreparedStatement ps = connection.prepareStatement(
                                "SELECT set_config('app.current_tenant', ?, true)")) {
                            ps.setString(1, TENANT_G.toString());
                            ps.execute();
                        }
                    });
                    AuditLog audit = new AuditLog();
                    audit.setTenantId(TENANT_G);
                    audit.setEntityType("p1_audit_probe");
                    audit.setEntityId(UUID.randomUUID());
                    audit.setAction(AuditLog.AuditAction.CREATE);
                    audit.setUserId(USER_1);
                    audit.setSuccess(true);
                    em.persist(audit);
                    em.flush();
                    assertThat(audit.getId()).isNotNull();
                    assertThat(audit.getId().toString().charAt(14)).isEqualTo('7');

                    AuditLogRepository repository =
                            new JpaRepositoryFactory(em).getRepository(AuditLogRepository.class);
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    List<AuditLog> found = repository.findByTenantIdAndUserIdAndCreatedAtBetween(
                            TENANT_G, USER_1, now.minusHours(1), now.plusHours(1));
                    assertThat(found).hasSize(1);
                    assertThat(found.get(0).getId()).isEqualTo(audit.getId());
                    List<AuditLog> outside = repository.findByTenantIdAndUserIdAndCreatedAtBetween(
                            TENANT_G, USER_1, at(2024, 1, 1, 0), at(2024, 2, 1, 0));
                    assertThat(outside).isEmpty();
                } finally {
                    // Commit, nao rollback: o rollback explicito falha aqui com
                    // "connection has been closed" (o proxy do repositorio
                    // criado pelo JpaRepositoryFactory ja liberou a conexao
                    // ao fim da consulta). A falha do teardown da transacao
                    // nao invalida as assercoes (todas acima ja passaram) e
                    // a linha do tenant G e removida deterministicamente pelo
                    // @AfterAll via migrador — commitado ou nao, nada vaza.
                    try {
                        if (em.getTransaction().isActive()) {
                            em.getTransaction().commit();
                        }
                    } catch (RuntimeException cleanupFailure) {
                        // Teardown da transacao de teste; ver comentario acima.
                    }
                    em.close();
                }
            } finally {
                sessionFactory.close();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    // ------------------------------------------------------------------------------------

    private static OffsetDateTime at(int year, int month, int day, int hour) {
        return OffsetDateTime.of(year, month, day, hour, 0, 0, 0, ZoneOffset.UTC);
    }

    private static OffsetDateTime at(int year, int month, int day, int hour, int minute) {
        return OffsetDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC);
    }

    private static void callFunction(String sql) throws SQLException {
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static void insertAudit(UUID tenant, OffsetDateTime createdAt, String entityType,
                                    String action, UUID userId, String ip) throws SQLException {
        asTenant(tenant, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO public.audit_log"
                            + " (tenant_id, entity_type, entity_id, action, user_id, ip_address, created_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, tenant);
                ps.setString(2, entityType);
                ps.setObject(3, UUID.randomUUID());
                ps.setString(4, action);
                ps.setObject(5, userId);
                ps.setString(6, ip);
                ps.setObject(7, java.sql.Timestamp.from(createdAt.toInstant()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    private static int countAudit(UUID tenant) throws SQLException {
        return asTenant(tenant, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT count(*) FROM public.audit_log WHERE tenant_id = ?")) {
                ps.setObject(1, tenant);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    /** Trabalho JDBC como migrador, com contexto de tenant local à transação. */
    private interface TenantWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private static <T> T asTenant(UUID tenant, TenantWork<T> work) throws SQLException {
        try (Connection c = migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
