package br.com.sigsan.app;

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
    @DisplayName("usuário admin do provisionamento: e-mail e nome não vão para a auditoria")
    void provisionedUserAuditHasNoPersonalData() throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection c = asTenant(); PreparedStatement ps = c.prepareStatement(
                "SELECT coalesce(old_values::text, '') || coalesce(new_values::text, '') || coalesce(changed_fields::text, '') "
                        + "FROM public.audit_log WHERE entity_type = 'users'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(rs.getString(1));
                }
            }
        }
        assertThat(rows).as("o provisionamento cria um usuário admin").isNotEmpty();
        assertThat(rows).allSatisfy(r -> assertThat(r)
                .doesNotContain("admin@teste.com.br").doesNotContain("Administrador"));
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
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + ProvisionedTenant.TENANT_ID + "', true)");
        }
        return c;
    }
}
