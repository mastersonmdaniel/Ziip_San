package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.migration.CoreMigrationRunner;
import br.com.ziipsan.platform.infra.crypto.JdbcTenantKeyStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * P4.6, item 8 do aceite: O MESMO teste de isolamento por RLS passa nos TRES
 * modos — SHARED (dois tenants no mesmo schema), SCHEMA (schema proprio) e
 * DEDICATED (schema em "outro cluster": neste ambiente de IT ha um unico
 * Postgres, logo o cluster dedicado e representado pelo seu schema proprio
 * e pela linha do catalogo com cluster_id proprio; a separacao fisica de
 * cluster prova-se pelo roteamento de pools do ClusterPoolRouterTest e fica
 * registada como ressalva no relatorio da P4.6).
 *
 * <p>No mesmo banco provam-se ainda: a resolucao real do catalogo devolvendo
 * cluster e modo (item 4), a eliminacao por tenant_id com crypto-shredding
 * no schema compartilhado (item 6, via JdbcTenantRowStore/JdbcTenantKeyStore
 * reais) e o provisionamento SHARED com reuso explicito de schema (item 3).
 *
 * <p>Correcção R-P46-01 (revisão do pacote): o setUp regista DOIS tenants
 * SHARED no mesmo (cluster 'default', schema partilhado) — contra a core
 * V7 isso violava o UNIQUE (cluster_id, schema_name) e o IT abortava
 * antes de qualquer teste. A core V15 (migration nova deste pacote)
 * substitui o UNIQUE pleno por índice único parcial que exclui as linhas
 * SHARED; o CoreMigrationRunner do setUp aplica-a como parte da série
 * core. O teste {@code catalogUniquenessStillGuardsNonSharedModes} prova
 * o outro sentido: SCHEMA/DEDICATED continuam únicos por (cluster,
 * schema). E {@code provisioningRefusesDedicatedOnDefaultCluster} prova
 * a correcção R-P46-05 em banco real.
 *
 * <p>Padrao dos ITs do modulo: credenciais por ambiente, sem valor padrao
 * versionado (scripts/ci-linux.sh gera senhas efemeras).
 */
class RlsIsolationThreeModesIT {

    private static final String RUN_ID = Long.toHexString(System.nanoTime() & 0xFFFFFFL);
    private static final String SHARED = "itest_p46_shared_" + RUN_ID;
    private static final String OWN = "itest_p46_own_" + RUN_ID;
    private static final String DED = "itest_p46_ded_" + RUN_ID;
    private static final String PROV_SHARED = "itest_p46_prov_" + RUN_ID;
    private static final String DED_CLUSTER = "dedicado-itest-" + RUN_ID;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID TENANT_C = UUID.randomUUID();
    private static final UUID TENANT_D = UUID.randomUUID();

    private static DataSource appDs;
    private static DataSource migratorDs;

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel " + name + " nao definida (ver scripts/ci-linux.sh)");
        }
        return value;
    }

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv().getOrDefault(
                "ZIIPSAN_IT_JDBC_URL", "jdbc:postgresql://127.0.0.1:55433/ziipsan");
        appDs = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("ZIIPSAN_IT_DB_USER", "ziipsan_app"),
                requireEnv("ZIIPSAN_IT_DB_PASSWORD"));
        migratorDs = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator"),
                requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD"));
        new CoreMigrationRunner(migratorDs).run(null);

        for (String schema : List.of(SHARED, OWN, DED)) {
            createProbeSchema(schema);
        }
        // SHARED: A e B no MESMO schema. SCHEMA: C no seu. DEDICATED: D no seu.
        insertAs(SHARED, TENANT_A, "a1");
        insertAs(SHARED, TENANT_A, "a2");
        insertAs(SHARED, TENANT_B, "b1");
        insertAs(OWN, TENANT_C, "c1");
        insertAs(DED, TENANT_D, "d1");
        registerCatalog(TENANT_A, SHARED, "default", "SHARED");
        registerCatalog(TENANT_B, SHARED, "default", "SHARED");
        registerCatalog(TENANT_C, OWN, "default", "SCHEMA");
        registerCatalog(TENANT_D, DED, DED_CLUSTER, "DEDICATED");
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = migratorDs.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM platform.tenant_vertical WHERE tenant_id IN "
                    + "(SELECT id FROM public.tenant_catalog WHERE schema_name LIKE 'itest_p46_%')");
            st.execute("DELETE FROM public.tenant_catalog WHERE schema_name LIKE 'itest_p46_%'");
            st.execute("DELETE FROM public.tenant_key WHERE tenant_id = '" + TENANT_A + "'");
            for (String schema : List.of(SHARED, OWN, DED, PROV_SHARED)) {
                st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private static void createProbeSchema(String schema) throws Exception {
        try (Connection conn = migratorDs.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA " + schema + " AUTHORIZATION ziipsan_migrator");
            st.execute("CREATE TABLE " + schema + ".probe (tenant_id UUID NOT NULL, note TEXT NOT NULL)");
            st.execute("ALTER TABLE " + schema + ".probe ENABLE ROW LEVEL SECURITY");
            st.execute("ALTER TABLE " + schema + ".probe FORCE ROW LEVEL SECURITY");
            st.execute("CREATE POLICY probe_tenant ON " + schema + ".probe"
                    + " USING (tenant_id = current_tenant_id())"
                    + " WITH CHECK (tenant_id = current_tenant_id())");
            st.execute("GRANT USAGE ON SCHEMA " + schema + " TO ziipsan_app");
            st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + schema + ".probe TO ziipsan_app");
        }
    }

    private static void insertAs(String schema, UUID tenant, String note) throws Exception {
        try (Connection conn = migratorDs.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement guc = conn.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)");
                 PreparedStatement ins = conn.prepareStatement(
                         "INSERT INTO " + schema + ".probe (tenant_id, note) VALUES (?, ?)")) {
                guc.setString(1, tenant.toString());
                guc.execute();
                ins.setObject(1, tenant);
                ins.setString(2, note);
                ins.executeUpdate();
                conn.commit();
            }
        }
    }

    private static void registerCatalog(UUID id, String schema, String cluster, String mode) throws Exception {
        try (Connection conn = migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, status,"
                             + " cluster_id, isolation_mode, created_at, updated_at)"
                             + " VALUES (?, ?, 'Tenant IT P4.6', ?, 'ACTIVE', ?, ?, NOW(), NOW())")) {
            ps.setObject(1, id);
            ps.setString(2, schema);
            ps.setString(3, String.format("%014d", Math.floorMod(id.getMostSignificantBits(), 100_000_000_000_000L)));
            ps.setString(4, cluster);
            ps.setString(5, mode);
            ps.executeUpdate();
        }
    }

    /** Le, como ziipsan_app e com a GUC do tenant, as notas visiveis num schema. */
    private static List<String> visibleNotes(String schema, UUID tenant) throws Exception {
        try (Connection conn = appDs.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement guc = conn.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)");
                 PreparedStatement q = conn.prepareStatement(
                         "SELECT note FROM " + schema + ".probe ORDER BY note")) {
                guc.setString(1, tenant.toString());
                guc.execute();
                try (ResultSet rs = q.executeQuery()) {
                    java.util.List<String> notes = new java.util.ArrayList<>();
                    while (rs.next()) {
                        notes.add(rs.getString(1));
                    }
                    conn.rollback();
                    return notes;
                }
            }
        }
    }

    @Test
    @DisplayName("item 8: o mesmo isolamento por RLS vale em SHARED, SCHEMA e DEDICATED")
    void rlsIsolationHoldsInAllThreeModes() throws Exception {
        // SHARED: A ve so as suas duas; B ve so a sua — no MESMO schema fisico.
        assertThat(visibleNotes(SHARED, TENANT_A)).containsExactly("a1", "a2");
        assertThat(visibleNotes(SHARED, TENANT_B)).containsExactly("b1");
        // SCHEMA (proprio) e DEDICATED (idem, com cluster proprio no catalogo).
        assertThat(visibleNotes(OWN, TENANT_C)).containsExactly("c1");
        assertThat(visibleNotes(DED, TENANT_D)).containsExactly("d1");
        // E nenhum tenant ve linhas alheias cruzando schemas:
        assertThat(visibleNotes(SHARED, TENANT_C)).isEmpty();
        assertThat(visibleNotes(DED, TENANT_A)).isEmpty();
    }

    @Test
    @DisplayName("item 8 (escrita): WITH CHECK barra inserir linha com tenant_id alheio nos tres modos")
    void rlsWriteCheckHoldsInAllThreeModes() throws Exception {
        for (Map.Entry<String, UUID> mode : Map.of(
                SHARED, TENANT_A, OWN, TENANT_C, DED, TENANT_D).entrySet()) {
            UUID foreign = UUID.randomUUID();
            try (Connection conn = appDs.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement guc = conn.prepareStatement(
                        "SELECT set_config('app.current_tenant', ?, true)");
                     PreparedStatement ins = conn.prepareStatement(
                             "INSERT INTO " + mode.getKey() + ".probe (tenant_id, note) VALUES (?, 'fraude')")) {
                    guc.setString(1, mode.getValue().toString());
                    guc.execute();
                    ins.setObject(1, foreign);
                    assertThatThrownBy(ins::executeUpdate)
                            .hasMessageContaining("row-level security");
                } finally {
                    conn.rollback();
                }
            }
        }
    }

    @Test
    @DisplayName("item 4: a resolucao real do catalogo devolve cluster e modo de cada tenant")
    void catalogResolutionReturnsClusterAndMode() {
        var catalog = new TenantCatalog(appDs);
        assertThat(catalog.findResolution(TENANT_A)).get()
                .satisfies(r -> {
                    assertThat(r.schema()).isEqualTo(SHARED);
                    assertThat(r.clusterId()).isEqualTo("default");
                    assertThat(r.isolationMode()).isEqualTo(IsolationMode.SHARED);
                });
        assertThat(catalog.findResolution(TENANT_D)).get()
                .satisfies(r -> {
                    assertThat(r.clusterId()).isEqualTo(DED_CLUSTER);
                    assertThat(r.isolationMode()).isEqualTo(IsolationMode.DEDICATED);
                });
        assertThat(catalog.findResolution(TENANT_C)).get()
                .satisfies(r -> assertThat(r.isolationMode()).isEqualTo(IsolationMode.SCHEMA));
    }

    @Test
    @DisplayName("item 6: eliminacao por tenant_id no SHARED apaga so o alvo e destroi a chave dele")
    void sharedErasureByTenantIdWithCryptoShredding() throws Exception {
        // Chave activa do alvo em public.tenant_key (material sintetico de teste).
        try (Connection conn = migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.tenant_key (tenant_id, key_version,"
                             + " wrapped_encryption_key, wrapped_blind_index_key, status)"
                             + " VALUES (?, 1, ?, ?, 'ACTIVE')"
                             + " ON CONFLICT (tenant_id, key_version) DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setBytes(2, new byte[]{9, 9, 9});
            ps.setBytes(3, new byte[]{8, 8, 8});
            ps.executeUpdate();
        }
        var lifecycle = new SharedTenantLifecycle(
                new SharedTenantLifecycle.JdbcTenantRowStore(migratorDs),
                new JdbcTenantKeyStore(migratorDs));

        // Exportacao antes: o conteudo do alvo sai, o do vizinho nao.
        var export = lifecycle.exportTenant(SHARED, List.of("probe"), TENANT_A);
        assertThat(export.get("probe")).hasSize(2)
                .allSatisfy(row -> assertThat(row.get("note").toString()).startsWith("a"));

        var report = lifecycle.eraseTenant(SHARED, List.of("probe"), TENANT_A);
        assertThat(report.deletedRows()).containsEntry("probe", 2);
        assertThat(report.keyDestroyed()).isTrue();
        // O CONTEUDO exportado viaja no relatorio (correccao R-P46-04):
        // e ele que o chamador arquiva como artefacto da eliminacao.
        assertThat(report.exportedContent().get("probe")).hasSize(2)
                .allSatisfy(row -> assertThat(row.get("note").toString()).startsWith("a"));
        // O vizinho do schema compartilhado fica intacto; o alvo desapareceu.
        assertThat(visibleNotes(SHARED, TENANT_B)).containsExactly("b1");
        assertThat(visibleNotes(SHARED, TENANT_A)).isEmpty();
        // Independencia entre testes (correccao da execucao, 10/11/2026):
        // a ordem dos testes nao e garantida e o teste de isolamento (item
        // 8) espera as linhas do setUp — este teste repoe as linhas que
        // apagou, inserindo-as de novo como o setUp fez. (A chave destruida
        // nao e reposta: nenhum outro teste a usa, e o shredding e por
        // desenho irreversivel.)
        insertAs(SHARED, TENANT_A, "a1");
        insertAs(SHARED, TENANT_A, "a2");
    }

    @Test
    @DisplayName("item 3: provisionar o 2o tenant SHARED reutiliza o schema; um SCHEMA no mesmo schema e recusado")
    void provisioningSharedReuseAndSchemaRefusal() {
        var service = new TenantProvisioningService(appDs, new JdbcTemplate(appDs),
                Mockito.mock(AuditLogRepository.class), migratorDs);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();

        var r1 = service.provisionTenant(request(first, PROV_SHARED, "SHARED"));
        assertThat(r1.success()).as("1o SHARED: %s", r1.error()).isTrue();
        var r2 = service.provisionTenant(request(second, PROV_SHARED, "SHARED"));
        assertThat(r2.success()).as("2o SHARED (reuso explicito): %s", r2.error()).isTrue();
        var r3 = service.provisionTenant(request(intruder, PROV_SHARED, "SCHEMA"));
        assertThat(r3.success()).as("SCHEMA sobre schema alheio deve falhar").isFalse();
    }

    @Test
    @DisplayName("R-P46-01/V15: o indice parcial continua a barrar 2o SCHEMA no mesmo (cluster, schema); um 3o SHARED regista")
    void catalogUniquenessStillGuardsNonSharedModes() throws Exception {
        // SCHEMA: C ja ocupa (default, OWN) — um segundo SCHEMA no mesmo par viola o indice parcial.
        assertThatThrownBy(() -> registerCatalog(UUID.randomUUID(), OWN, "default", "SCHEMA"))
                .hasMessageContaining("uq_tenant_catalog_cluster_schema");
        // SHARED: um terceiro tenant no schema partilhado regista sem colisao (o desenho da V15).
        UUID third = UUID.randomUUID();
        registerCatalog(third, SHARED, "default", "SHARED");
        try (Connection conn = migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM public.tenant_catalog WHERE id = ?")) {
            ps.setObject(1, third);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("R-P46-05: provisionar DEDICATED no cluster 'default' e recusado pelo provisionamento")
    void provisioningRefusesDedicatedOnDefaultCluster() {
        var service = new TenantProvisioningService(appDs, new JdbcTemplate(appDs),
                Mockito.mock(AuditLogRepository.class), migratorDs);
        var request = TenantProvisioningService.TenantProvisioningRequest.builder()
                .tenantId(UUID.randomUUID())
                .schemaName("itest_p46_deddefault_" + RUN_ID)
                .displayName("Dedicado Indevido IT")
                .cnpj("00000000000000")
                .email("itest-p46@ziipsan.invalid")
                .phone("0000000000")
                .address(Map.of())
                .plan("TEST")
                .settings(Map.of())
                .clusterId("default")
                .isolationMode("DEDICATED")
                .build();
        assertThatThrownBy(() -> service.provisionTenant(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cluster 'default'");
    }

    private static TenantProvisioningService.TenantProvisioningRequest request(
            UUID tenantId, String schema, String mode) {
        return TenantProvisioningService.TenantProvisioningRequest.builder()
                .tenantId(tenantId)
                .schemaName(schema)
                .displayName("Autarquia Ficticia P4.6")
                .cnpj(String.format("%014d",
                        Math.floorMod(tenantId.getMostSignificantBits(), 100_000_000_000_000L)))
                .email("itest-p46@ziipsan.invalid")
                .phone("0000000000")
                .address(Map.of())
                .plan("TEST")
                .settings(Map.of())
                .clusterId("default")
                .isolationMode(mode)
                .build();
    }
}
