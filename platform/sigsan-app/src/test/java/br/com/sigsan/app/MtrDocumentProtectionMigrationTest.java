package br.com.sigsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
 * V2_8 do MTR: documentos pessoais saem das colunas em claro (achado C4, E4).
 *
 * <p>Duas coisas são provadas no PostgreSQL real: o schema final (colunas antigas fora, CHECK e
 * índice cego únicos valendo) e a proteção contra perda silenciosa. A migration não tem backfill (a
 * chave está na aplicação); se encontrar valor em claro, precisa recusar o tenant. Como a RLS é FORCE
 * também para o migrador, uma verificação sem {@code app.current_tenant} veria zero linhas e
 * apagaria as colunas com dado: o caso "sem GUC a tabela parece vazia" está provado abaixo.
 *
 * <p>A verificação roda o texto da própria migration (do classpath) contra um schema descartável com
 * as três tabelas no formato da V2_4, tudo numa transação desfeita no fim.
 */
class MtrDocumentProtectionMigrationTest {

    private static final String MIGRATION = "db/migration/tenant/V2_8__mtr_document_protection.sql";
    private static final String SCHEMA = ProvisionedTenant.SCHEMA;

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    // ------------------------------------------------------------------ schema final no tenant provisionado

    @Test
    @DisplayName("tenant provisionado: colunas em claro removidas, colunas protegidas presentes")
    void plainColumnsAreGone() throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT table_name || '.' || column_name FROM information_schema.columns "
                             + "WHERE table_schema = ? AND table_name IN ('waste_mtr', 'waste_generator', 'waste_transporter')")) {
            ps.setString(1, SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        assertThat(columns).doesNotContain("waste_mtr.driver_cpf", "waste_generator.cnpj_cpf", "waste_transporter.drivers");
        assertThat(columns).contains("waste_mtr.driver_cpf_ciphertext", "waste_mtr.driver_cpf_hash",
                "waste_mtr.driver_cpf_masked", "waste_generator.document_ciphertext", "waste_generator.document_hash",
                "waste_generator.document_masked", "waste_transporter.drivers_ciphertext");
    }

    @Test
    @DisplayName("dois geradores com o mesmo índice cego no tenant: o banco recusa (idx_waste_generator_document)")
    void duplicateDocumentHashIsRejectedByTheDatabase() throws SQLException {
        String hash = "a".repeat(64);
        try (Connection c = asTenant()) {
            insertGenerator(c, hash);
            assertThatThrownBy(() -> insertGenerator(c, hash))
                    .isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23505"))
                    .hasMessageContaining("idx_waste_generator_document");
            c.rollback();
        }
    }

    @Test
    @DisplayName("CPF do motorista: cifra sem índice cego (ou o contrário) viola o CHECK")
    void driverCpfCheckIsDeclared() throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                             + "WHERE conname = 'ck_waste_mtr_driver_cpf_protected' AND connamespace = ?::regnamespace")) {
            ps.setString(1, SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("constraint ck_waste_mtr_driver_cpf_protected").isTrue();
                assertThat(rs.getString(1)).contains("driver_cpf_ciphertext IS NULL").contains("driver_cpf_hash IS NULL");
            }
        }
    }

    // ------------------------------------------------------------------ proteção contra perda silenciosa

    @Test
    @DisplayName("sem a GUC de tenant, o migrador vê a tabela vazia mesmo com linha (RLS FORCE)")
    void withoutTenantGucTheMigratorSeesNothing() throws Exception {
        UUID tenant = UUID.randomUUID();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            String scratch = createLegacySchema(c);
            try (Statement st = c.createStatement()) {
                asTenant(st, tenant);
                st.execute("INSERT INTO waste_mtr (tenant_id, driver_cpf) VALUES ('" + tenant + "', '52998224725')");
                st.execute("SELECT set_config('app.current_tenant', '', true)");
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + scratch + ".waste_mtr")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isZero();
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("CPF do motorista em claro: a V2_8 recusa o tenant e nada é alterado")
    void plainDriverCpfAbortsTheMigration() throws Exception {
        assertMigrationRefuses("INSERT INTO waste_mtr (tenant_id, driver_cpf) VALUES ('%s', '52998224725')",
                "waste_mtr.driver_cpf: 1");
    }

    @Test
    @DisplayName("gerador cadastrado (documento em claro): a V2_8 recusa o tenant")
    void plainGeneratorAbortsTheMigration() throws Exception {
        assertMigrationRefuses("INSERT INTO waste_generator (tenant_id, cnpj_cpf) VALUES ('%s', '11222333000181')",
                "waste_generator.cnpj_cpf: 1");
    }

    @Test
    @DisplayName("lista de motoristas em claro: a V2_8 recusa o tenant")
    void plainDriversAbortTheMigration() throws Exception {
        assertMigrationRefuses("INSERT INTO waste_transporter (tenant_id, drivers) VALUES ('%s', "
                + "'[{\"name\":\"Motorista\",\"cpf\":\"52998224725\"}]'::jsonb)", "waste_transporter.drivers: 1");
    }

    @Test
    @DisplayName("sem documento em claro (CPF vazio, lista vazia), a V2_8 aplica e troca as colunas")
    void emptyLegacyColumnsMigrate() throws Exception {
        UUID tenant = UUID.randomUUID();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            String scratch = createLegacySchema(c);
            try (Statement st = c.createStatement()) {
                asTenant(st, tenant);
                st.execute("INSERT INTO waste_mtr (tenant_id, driver_cpf) VALUES ('" + tenant + "', NULL), ('" + tenant + "', ' ')");
                st.execute("INSERT INTO waste_transporter (tenant_id, drivers) VALUES ('" + tenant + "', '[]'::jsonb), ('"
                        + tenant + "', NULL)");
                st.execute("SELECT set_config('app.current_tenant', '', true)");

                st.execute(migrationSql(tenant));

                List<String> columns = new ArrayList<>();
                try (ResultSet rs = st.executeQuery("SELECT table_name || '.' || column_name FROM information_schema.columns "
                        + "WHERE table_schema = '" + scratch + "'")) {
                    while (rs.next()) {
                        columns.add(rs.getString(1));
                    }
                }
                assertThat(columns).doesNotContain("waste_mtr.driver_cpf", "waste_generator.cnpj_cpf", "waste_transporter.drivers")
                        .contains("waste_mtr.driver_cpf_ciphertext", "waste_generator.document_hash", "waste_transporter.drivers_ciphertext");
            } finally {
                c.rollback();
            }
        }
    }

    // ------------------------------------------------------------------

    private void assertMigrationRefuses(String insertTemplate, String expectedCount) throws Exception {
        UUID tenant = UUID.randomUUID();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            String scratch = createLegacySchema(c);
            try (Statement st = c.createStatement()) {
                asTenant(st, tenant);
                st.execute(String.format(insertTemplate, tenant));
                // A migration recebe a conexão sem contexto, como no provisionamento: ela mesma define a GUC.
                st.execute("SELECT set_config('app.current_tenant', '', true)");

                var beforeMigration = c.setSavepoint();
                assertThatThrownBy(() -> st.execute(migrationSql(tenant)))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("documento em claro no MTR")
                        .hasMessageContaining(expectedCount);
                c.rollback(beforeMigration);

                // O dado continua onde estava: as três colunas antigas existem e a linha é visível no tenant.
                asTenant(st, tenant);
                try (ResultSet rs = st.executeQuery("SELECT (SELECT count(*) FROM " + scratch + ".waste_mtr WHERE driver_cpf IS NOT NULL)"
                        + " + (SELECT count(*) FROM " + scratch + ".waste_generator WHERE cnpj_cpf IS NOT NULL)"
                        + " + (SELECT count(*) FROM " + scratch + ".waste_transporter WHERE drivers IS NOT NULL)")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            } finally {
                c.rollback();
            }
        }
    }

    /** Schema descartável com as três tabelas no formato da V2_4 (só as colunas que a V2_8 toca), com RLS FORCE. */
    private static String createLegacySchema(Connection c) throws SQLException {
        String scratch = "it_v28_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        try (Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA " + scratch);
            st.execute("SET LOCAL search_path = " + scratch + ", public");
            st.execute("CREATE TABLE waste_mtr (tenant_id UUID NOT NULL, driver_cpf VARCHAR(14))");
            st.execute("CREATE TABLE waste_generator (tenant_id UUID NOT NULL, cnpj_cpf VARCHAR(18) NOT NULL)");
            st.execute("CREATE UNIQUE INDEX idx_waste_generator_cnpj ON waste_generator(tenant_id, cnpj_cpf)");
            st.execute("CREATE TABLE waste_transporter (tenant_id UUID NOT NULL, drivers JSONB)");
            for (String table : List.of("waste_mtr", "waste_generator", "waste_transporter")) {
                st.execute("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
                st.execute("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");
                st.execute("CREATE POLICY tenant_isolation ON " + table
                        + " USING (tenant_id = public.current_tenant_id())");
            }
        }
        return scratch;
    }

    private static void asTenant(Statement st, UUID tenant) throws SQLException {
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
    }

    /** Texto da migration como o Flyway a aplicaria (o placeholder tenant_id substituído). */
    private static String migrationSql(UUID tenant) throws IOException {
        try (InputStream in = MtrDocumentProtectionMigrationTest.class.getClassLoader().getResourceAsStream(MIGRATION)) {
            assertThat(in).as("migration %s no classpath", MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("${tenant_id}", tenant.toString());
        }
    }

    private static void insertGenerator(Connection c, String hash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + SCHEMA + ".waste_generator "
                + "(tenant_id, document_ciphertext, document_hash, corporate_name) VALUES (?, 'cifra-de-teste', ?, 'Gerador')")) {
            ps.setObject(1, ProvisionedTenant.TENANT_ID);
            ps.setString(2, hash);
            ps.executeUpdate();
        }
    }

    /** Conexão da aplicação no contexto do tenant provisionado, sem autocommit. */
    private static Connection asTenant() throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + ProvisionedTenant.TENANT_ID + "', true)");
        }
        return c;
    }
}
