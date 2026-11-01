package br.com.ziipsan.app;

import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import org.junit.jupiter.api.AfterAll;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A trilha de auditoria não guarda dado pessoal (achado C4 do PDCA de 12/10/2026).
 *
 * <p>Antes, o trigger gravava a linha inteira em public.audit_log, tirando só as credenciais: nome,
 * nascimento, e-mail e telefone ficavam ali para sempre, inclusive depois da anonimização.
 */
class AuditRedactionTest {

    private static final String NOME = "Fulana Redigida da Silva";
    private static final String NOVO_NOME = "Beltrana Redigida Souza";
    private static final String NASCIMENTO = "1980-05-17";
    private static final String EMAIL = "fulana.redigida@exemplo.com.br";

    private static UUID customerId;
    private static UUID contactId;

    /**
     * Tenants descartáveis provisionados por este teste (tenant -&gt; schema), com schema
     * e linha do catálogo removidos no {@code @AfterAll}. Cada teste que precisa
     * isolar-se de outros testes da JVM usa o
     * próprio tenant: a trilha de auditoria é global em {@code public.audit_log}, mas a
     * RLS a isola por tenant, então um teste nunca deve afirmar sobre o tenant alheio.
     */
    private static final Map<UUID, String> DISPOSABLE_TENANTS = new LinkedHashMap<>();

    @BeforeAll
    static void setUp() throws SQLException {
        ProvisionedTenant.ensure();
        try (Connection c = asTenant()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + ProvisionedTenant.SCHEMA + ".customers (tenant_id, person_type, name, birth_date) "
                            + "VALUES (?, 'PF', ?, ?::date) RETURNING id")) {
                ps.setObject(1, ProvisionedTenant.TENANT_ID);
                ps.setString(2, NOME);
                ps.setString(3, NASCIMENTO);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    customerId = rs.getObject(1, UUID.class);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + ProvisionedTenant.SCHEMA + ".customers SET name = ? WHERE id = ?")) {
                ps.setString(1, NOVO_NOME);
                ps.setObject(2, customerId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + ProvisionedTenant.SCHEMA + ".customer_contacts (tenant_id, customer_id, kind, value) "
                            + "VALUES (?, ?, 'EMAIL', ?) RETURNING id")) {
                ps.setObject(1, ProvisionedTenant.TENANT_ID);
                ps.setObject(2, customerId);
                ps.setString(3, EMAIL);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    contactId = rs.getObject(1, UUID.class);
                }
            }
            c.commit();
        }
    }

    @Test
    @DisplayName("criação e alteração de cliente: nome e nascimento não vão para a auditoria")
    void customerAuditHasNoPersonalData() throws SQLException {
        List<String> rows = auditRows(customerId);
        assertThat(rows).as("CREATE e UPDATE do cliente").hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r)
                .doesNotContain(NOME).doesNotContain(NOVO_NOME).doesNotContain(NASCIMENTO));
        assertThat(rows).as("campos não pessoais continuam na trilha").allSatisfy(r -> assertThat(r).contains("\"PF\""));
    }

    @Test
    @DisplayName("a alteração de nome continua registrada em changed_fields, sem o valor")
    void nameChangeIsRecordedWithoutValue() throws SQLException {
        try (Connection c = asTenant(); PreparedStatement ps = c.prepareStatement(
                "SELECT changed_fields->>'name' FROM public.audit_log WHERE entity_id = ? AND action = 'UPDATE'")) {
            ps.setObject(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("[redacted]");
            }
        }
    }

    @Test
    @DisplayName("contato do cliente: o e-mail não vai para a auditoria")
    void contactAuditHasNoEmail() throws SQLException {
        assertThat(auditRows(contactId)).isNotEmpty().allSatisfy(r -> assertThat(r).doesNotContain(EMAIL));
    }

    @Test
    @DisplayName("P0.25: o provisionamento não cria usuário, então não há trilha de users do tenant novo")
    void provisionedUserAuditHasNoPersonalData() throws SQLException {
        // Antes, este teste fazia SELECT global em audit_log e exigia isEmpty(): falhava
        // sempre que outro teste da mesma JVM (ex.: UserApiTest, P0.13) criasse usuários
        // no tenant compartilhado antes dele, porque a API de usuários gera legitimamente
        // trilhas de users. O escopo correto é o tenant recém-provisionado: o
        // provisionamento em si grava entity_type='Tenant', nunca 'users'.
        UUID tenantId = provisionDisposableTenant("44555666000181", "Tenant descartavel da auditoria de users");
        List<String> rows = userAuditRows(tenantId);
        assertThat(rows).as("provisionar um tenant novo não cria trilha de users dele").isEmpty();
    }

    @Test
    @DisplayName("V1_10: display_name e email de users nunca aparecem em claro na trilha")
    void userAuditValuesAreRedacted() throws SQLException {
        // Garantia LGPD separada do teste acima: aqui o usuário EXISTE de propósito, e o
        // que se prova é a redação (V1_10) — o que o isEmpty() antigo não provava. Os
        // valores conferidos são lidos de volta da tabela users, não repetidos do
        // INSERT: se a redação quebrar, o valor em claro aparece na trilha e o teste falha.
        UUID tenantId = provisionDisposableTenant("55666777000181", "Tenant descartavel da redacao de users");
        String schema = DISPOSABLE_TENANTS.get(tenantId);
        UUID userId;
        try (Connection c = asTenant(tenantId);
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + schema + ".users (tenant_id, keycloak_sub, display_name, email) "
                             + "VALUES (?, 'sub-auditoria-redacao', 'Usuaria Redigida de Auditoria', "
                             + "'usuaria.redigida.auditoria@exemplo.com.br') RETURNING id")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                userId = rs.getObject(1, UUID.class);
            }
            c.commit();
        }
        try (Connection c = asTenant(tenantId);
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE " + schema + ".users SET display_name = ?, email = ? WHERE id = ?")) {
            ps.setString(1, "Usuaria Redigida Atualizada");
            ps.setString(2, "usuaria.redigida.atualizada@exemplo.com.br");
            ps.setObject(3, userId);
            ps.executeUpdate();
            c.commit();
        }

        List<String> secrets = new ArrayList<>();
        try (Connection c = asTenant(tenantId);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT display_name, email FROM " + schema + ".users WHERE id = ?")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                secrets.add(rs.getString(1));
                secrets.add(rs.getString(2));
            }
        }

        List<String> rows = userAuditRows(tenantId);
        assertThat(rows).as("CREATE e UPDATE do usuário geram trilha").hasSize(2);
        assertThat(rows).as("valores pessoais redigidos, não em claro")
                .allSatisfy(r -> {
                    assertThat(r).contains("[redacted]");
                    secrets.forEach(s -> assertThat(r).doesNotContain(s));
                });
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (ProvisionedTenant.migratorDs == null) {
            return;
        }
        // Schema E linha do catálogo (mesmo padrão de MigrateRunsMigrationTest): o
        // DatabaseMigrator do perfil migrate percorre todo tenant ACTIVE do catálogo e
        // roda Flyway no schema dele — deixar a linha sem o schema quebra qualquer
        // teste migrate posterior da mesma JVM com "schema does not exist".
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement();
             PreparedStatement del = c.prepareStatement("DELETE FROM public.tenant_catalog WHERE id = ?")) {
            for (Map.Entry<UUID, String> e : DISPOSABLE_TENANTS.entrySet()) {
                if (e.getValue().startsWith("it_a_")) {
                    st.execute("DROP SCHEMA IF EXISTS " + e.getValue() + " CASCADE");
                }
                del.setObject(1, e.getKey());
                del.executeUpdate();
            }
        }
    }

    /**
     * Provisiona um tenant descartável novo (mesmo padrão de UserApiTest.setUp()) e
     * devolve o id dele; o schema fica registrado para o {@code @AfterAll}. O CNPJ é
     * distinto dos demais testes da JVM (tenant de integração, tenant B do UserApiTest
     * e tenant B do RegistryApiE2ETest).
     */
    private static UUID provisionDisposableTenant(String cnpj, String displayName) {
        ProvisionedTenant.ensure();
        UUID tenantId = UUID.randomUUID();
        String schema = "it_a_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var result = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(AuditLogRepository.class),
                ProvisionedTenant.migratorDs)
                .provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                        .tenantId(tenantId)
                        .schemaName(schema)
                        .displayName(displayName)
                        .cnpj(cnpj)
                        .email("descartavel@teste.com.br")
                        .plan("PRO")
                        .build());
        assertThat(result.success()).as("provisionamento do tenant descartavel: %s", result.error()).isTrue();
        DISPOSABLE_TENANTS.put(tenantId, schema);
        return tenantId;
    }

    /** Trilhas de users de um tenant, lidas com o contexto desse tenant (a RLS exige). */
    private static List<String> userAuditRows(UUID tenantId) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection c = asTenant(tenantId); PreparedStatement ps = c.prepareStatement(
                "SELECT coalesce(old_values::text, '') || coalesce(new_values::text, '') || coalesce(changed_fields::text, '') "
                        + "FROM public.audit_log WHERE entity_type = 'users' AND tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString(1));
                }
            }
        }
        return rows;
    }

    @Test
    @DisplayName("audit_redact: REDACT troca o valor, OMIT remove a chave (ou marca [changed] em changed_fields)")
    void redactFunctionModes() throws SQLException {
        String row = "{\"ip_address\":\"10.1.2.3\",\"geolocation\":{\"lat\":-15.7},\"status\":\"GRANTED\",\"consent_text\":null}";
        assertThat(redact("lgpd_consent", row, false))
                .isEqualTo("{\"status\": \"GRANTED\", \"ip_address\": \"[redacted]\", \"consent_text\": null}");
        assertThat(redact("lgpd_consent", row, true))
                .contains("\"geolocation\": \"[changed]\"").contains("\"ip_address\": \"[redacted]\"");
        assertThat(redact("tabela_sem_regra", "{\"a\":1}", false)).isEqualTo("{\"a\": 1}");
    }

    @Test
    @DisplayName("a aplicação não lê nem altera as regras de redação")
    void appCannotTouchRedactionRules() {
        assertThatThrownBy(() -> {
            try (Connection c = ProvisionedTenant.appDs.getConnection(); Statement st = c.createStatement()) {
                st.executeQuery("SELECT count(*) FROM public.audit_redaction_rule");
            }
        }).isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("42501");
    }

    private static List<String> auditRows(UUID entityId) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection c = asTenant(); PreparedStatement ps = c.prepareStatement(
                "SELECT coalesce(old_values::text, '') || coalesce(new_values::text, '') || coalesce(changed_fields::text, '') "
                        + "FROM public.audit_log WHERE entity_id = ? ORDER BY created_at")) {
            ps.setObject(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString(1));
                }
            }
        }
        return rows;
    }

    private static String redact(String table, String json, boolean changed) throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT public.audit_redact(?, ?::jsonb, ?)::text")) {
            ps.setString(1, table);
            ps.setString(2, json);
            ps.setBoolean(3, changed);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** Conexão da aplicação, numa transação, com o contexto do tenant de integração. */
    private static Connection asTenant() throws SQLException {
        return asTenant(ProvisionedTenant.TENANT_ID);
    }

    /** Conexão da aplicação, numa transação, com o contexto do tenant informado. */
    private static Connection asTenant(UUID tenantId) throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + tenantId + "', true)");
        }
        return c;
    }
}
