package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.3 (camada C), correção R1 do gate da camada B, no PostgreSQL real sobre o tenant provisionado.
 *
 * <p>A R1 pedia "migration V3_6 com SET NOT NULL nas 2 colunas", mas a V3_6 JÁ EXISTIA
 * (registry_territory_fields, P0.16) e não podia ser reutilizada. Execução decidida: o NOT NULL
 * do histórico chegou na V3_12 (registry, P1.3) e o da tarifa social na V4_13 (billing,
 * primeira livre após a V4_12 reservada); as FKs das duas colunas já existiam desde a V9_1
 * (fk_ush_service_order, fk_social_enroll_order) — a V9_3 só guarda a do histórico, sem duplicar.
 * Este teste prova, contra um tenant TOTALMENTE migrado, que NULL é recusado com 23502 nas
 * DUAS colunas, que uma OS válida é aceita nas duas e que uma OS inexistente é recusada
 * com 23503 nas duas.
 *
 * <p>Fixtures rolados de volta ao fim de cada teste (savepoint + rollback), no padrão do
 * ServiceOrderEngineIT e do BillingP1MigrationTest.
 */
class RegistryR2IT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private static Connection asTenant() throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
        }
        return c;
    }

    private static UUID insertUnit(Connection c, String registration) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".consumer_units (id, tenant_id, registration_number, check_digit)"
                        + " VALUES (?, ?, ?, '0')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setString(3, registration);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertCustomer(Connection c) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".customers (id, tenant_id, person_type, name)"
                        + " VALUES (?, ?, 'PF', 'Titular de teste')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertServiceOrder(Connection c) throws SQLException {
        UUID typeId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".service_types (id, tenant_id, code, name, operation, effect_code,"
                        + " indicator, target_classes) VALUES (?, ?, ?, 'Tipo de teste', 'TEST_OP',"
                        + " 'IT_EFFECT', 'INTERNAL', ARRAY['CONSUMER_UNIT'])")) {
            ps.setObject(1, typeId);
            ps.setObject(2, TENANT);
            ps.setString(3, "IT-R2-TYPE-" + UUID.randomUUID().toString().substring(0, 8));
            ps.executeUpdate();
        }
        UUID orderId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".service_orders (id, tenant_id, service_type_id, origin,"
                        + " execution_mode, created_by) VALUES (?, ?, ?, 'INTERNAL', 'INTERNAL', ?)")) {
            ps.setObject(1, orderId);
            ps.setObject(2, TENANT);
            ps.setObject(3, typeId);
            ps.setObject(4, UUID.randomUUID());
            ps.executeUpdate();
        }
        return orderId;
    }

    private static void insertHistory(Connection c, UUID unit, UUID orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".unit_status_history (tenant_id, unit_id, service_kind,"
                        + " to_status_id, service_order_id) VALUES (?, ?, 'WATER', ?, ?)")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, unit);
            ps.setObject(3, UUID.randomUUID());
            ps.setObject(4, orderId);
            ps.executeUpdate();
        }
    }

    private static void insertEnrollment(Connection c, UUID unit, UUID holder, String familyHash, UUID orderId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".social_tariff_enrollments (tenant_id, unit_id, holder_customer_id,"
                        + " family_code_hash, eligibility_basis, enrollment_source, cadunico_updated_on, valid_from,"
                        + " status, legal_basis, social_economies, service_order_id) VALUES (?, ?, ?, ?, 'CADUNICO',"
                        + " 'OFFICIAL_LIST', CURRENT_DATE, CURRENT_DATE, 'ACTIVE', 'Lei 14.898/2024', 1, ?)")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, unit);
            ps.setObject(3, holder);
            ps.setString(4, familyHash);
            ps.setObject(5, orderId);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("R1 histórico (V3_12): NULL recusado (23502); OS válida aceita; OS inexistente recusada (23503)")
    void historyServiceOrderRequired() throws SQLException {
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "IT-R2-HIST");
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertHistory(c, unit, null))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23502"));
                c.rollback(savepoint);
                UUID order = insertServiceOrder(c);
                insertHistory(c, unit, order);
                savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertHistory(c, unit, UUID.randomUUID()))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23503"));
                c.rollback(savepoint);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("R1 tarifa social (V4_13): NULL recusado (23502); OS válida aceita; OS inexistente recusada (23503)")
    void enrollmentServiceOrderRequired() throws SQLException {
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "IT-R2-TAR");
                UUID holder = insertCustomer(c);
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertEnrollment(c, unit, holder, "hash-familia-r2-01", null))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23502"));
                c.rollback(savepoint);
                UUID order = insertServiceOrder(c);
                insertEnrollment(c, unit, holder, "hash-familia-r2-01", order);
                savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertEnrollment(c, unit, holder, "hash-familia-r2-02", UUID.randomUUID()))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23503"));
                c.rollback(savepoint);
            } finally {
                c.rollback();
            }
        }
    }
}
