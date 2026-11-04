package br.com.ziipsan.app;

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
 * P1.5 (V4_2) e P1.16 (V4_6) no PostgreSQL real, sobre o tenant provisionado.
 *
 * <p>Prova ao vivo: as tabelas sociais existem com os CHECKs do plano; o indice unico
 * parcial recusa a segunda inscricao ativa da mesma familia; a conta recusa
 * {@code tax_treatment} diferente de WITHHOLDING_ONLY e total fora da formula com
 * tributo retido; o lote de auditoria existe e a conta pode apontar para ele. Tudo
 * numa transacao desfeita no fim de cada teste; nenhum dado de teste sobrevive.
 */
class BillingP1MigrationTest {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    @Test
    @DisplayName("V4_2 e V4_6: tabelas sociais e lote de auditoria existem no tenant provisionado")
    void newTablesExist() throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = ? "
                             + "AND table_name IN ('social_tariff_enrollments', 'social_base_imports', "
                             + "'social_base_records', 'social_tariff_end_reasons', 'bill_audit_batches')")) {
            ps.setString(1, SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<String> found = new java.util.ArrayList<>();
                while (rs.next()) {
                    found.add(rs.getString(1));
                }
                assertThat(found).containsExactlyInAnyOrder("social_tariff_enrollments", "social_base_imports",
                        "social_base_records", "social_tariff_end_reasons", "bill_audit_batches");
            }
        }
    }

    @Test
    @DisplayName("V4_2: segunda inscricao ativa da mesma familia e recusada pelo indice parcial")
    void secondActiveEnrollmentForSameFamilyIsRejected() throws SQLException {
        try (Connection c = asTenant()) {
            try {
                UUID unit1 = insertUnit(c, "IT-P1-01");
                UUID unit2 = insertUnit(c, "IT-P1-02");
                UUID holder = insertCustomer(c);
                insertEnrollment(c, unit1, holder, "hash-familia-it-01", "ACTIVE");
                assertThatThrownBy(() -> insertEnrollment(c, unit2, holder, "hash-familia-it-01", "ACTIVE"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23505"))
                        .hasMessageContaining("idx_social_enroll_family_active");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("V4_2: inscricao ENDED sem motivo e recusada; com motivo, aceita")
    void endedEnrollmentRequiresEndReason() throws SQLException {
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "IT-P1-03");
                UUID holder = insertCustomer(c);
                insertEndReason(c, "PERDA_ELEGIBILIDADE", "Perda de elegibilidade");
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertEnrollmentEnded(c, unit, holder, "hash-familia-it-02", null))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
                // A violacao aborta a transacao do Postgres: voltar ao savepoint antes de seguir.
                c.rollback(savepoint);
                insertEnrollmentEnded(c, unit, holder, "hash-familia-it-02", "PERDA_ELEGIBILIDADE");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("V4_6: conta com tax_treatment diferente de WITHHOLDING_ONLY e recusada")
    void billWithOtherTaxTreatmentIsRejected() throws SQLException {
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "IT-P1-04");
                assertThatThrownBy(() -> insertBill(c, unit, "IBS_CBS", "112.00", "0.00"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"))
                        .hasMessageContaining("chk_bills_tax_treatment");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("V4_6: conta com total fora da formula com tributo retido e recusada; dentro, aceita com lote")
    void billTotalWithWithholdingTaxIsCheckedAndBatchLinks() throws SQLException {
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "IT-P1-05");
                // agua 112,00 + esgoto 89,60 - tributo retido 12,00 = 189,60; 190,00 viola o CHECK.
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertBillWithTotal(c, unit, "190.00"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
                // A violacao aborta a transacao do Postgres: voltar ao savepoint antes de seguir.
                c.rollback(savepoint);
                UUID batch = insertBatch(c);
                insertBillWithBatch(c, unit, "189.60", batch);
            } finally {
                c.rollback();
            }
        }
    }

    // ------------------------------------------------------------------

    private static Connection asTenant() throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
        }
        return c;
    }

    private static UUID insertUnit(Connection c, String matricula) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".consumer_units (id, tenant_id, registration_number, check_digit) VALUES (?, ?, ?, '0')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setString(3, matricula);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertCustomer(Connection c) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".customers (id, tenant_id, person_type, name) VALUES (?, ?, 'PF', 'Titular de teste')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.executeUpdate();
        }
        return id;
    }

    // P1.31 (V9_1): a inscricao exige OS (FK + NOT NULL). O fixture cria um
    // tipo e uma OS em DRAFT e a usa como causa da inscricao.
    private static UUID insertServiceOrder(Connection c) throws SQLException {
        UUID typeId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".service_types (id, tenant_id, code, name, operation, effect_code, "
                        + "indicator, target_classes) VALUES (?, ?, ?, 'Tipo de teste', 'TEST_OP', "
                        + "'IT_EFFECT', 'INTERNAL', ARRAY['CONSUMER_UNIT'])")) {
            ps.setObject(1, typeId);
            ps.setObject(2, TENANT);
            ps.setString(3, "IT-TYPE-" + UUID.randomUUID().toString().substring(0, 8));
            ps.executeUpdate();
        }
        UUID orderId = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".service_orders (id, tenant_id, service_type_id, origin, "
                        + "execution_mode, created_by) VALUES (?, ?, ?, 'INTERNAL', 'INTERNAL', ?)")) {
            ps.setObject(1, orderId);
            ps.setObject(2, TENANT);
            ps.setObject(3, typeId);
            ps.setObject(4, creator);
            ps.executeUpdate();
        }
        return orderId;
    }

    private static void insertEnrollment(Connection c, UUID unit, UUID holder, String familyHash, String status) throws SQLException {
        insertEnrollment(c, unit, holder, familyHash, status, insertServiceOrder(c));
    }

    private static void insertEnrollment(Connection c, UUID unit, UUID holder, String familyHash, String status, UUID orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".social_tariff_enrollments (tenant_id, unit_id, holder_customer_id, "
                        + "family_code_hash, eligibility_basis, enrollment_source, cadunico_updated_on, valid_from, "
                        + "status, legal_basis, social_economies, service_order_id) VALUES (?, ?, ?, ?, 'CADUNICO', 'OFFICIAL_LIST', "
                        + "CURRENT_DATE, CURRENT_DATE, ?, 'Lei 14.898/2024', 1, ?)")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, unit);
            ps.setObject(3, holder);
            ps.setString(4, familyHash);
            ps.setString(5, status);
            ps.setObject(6, orderId);
            ps.executeUpdate();
        }
    }

    private static void insertEndReason(Connection c, String code, String description) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".social_tariff_end_reasons (tenant_id, code, description) VALUES (?, ?, ?)")) {
            ps.setObject(1, TENANT);
            ps.setString(2, code);
            ps.setString(3, description);
            ps.executeUpdate();
        }
    }

    private static void insertEnrollmentEnded(Connection c, UUID unit, UUID holder, String familyHash, String reason) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".social_tariff_enrollments (tenant_id, unit_id, holder_customer_id, "
                        + "family_code_hash, eligibility_basis, enrollment_source, cadunico_updated_on, valid_from, "
                        + "status, end_reason_code, legal_basis, social_economies, service_order_id) VALUES (?, ?, ?, ?, 'CADUNICO', "
                        + "'OFFICIAL_LIST', CURRENT_DATE, CURRENT_DATE, 'ENDED', ?, 'Lei 14.898/2024', 1, ?)")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, unit);
            ps.setObject(3, holder);
            ps.setString(4, familyHash);
            ps.setString(5, reason);
            ps.setObject(6, insertServiceOrder(c));
            ps.executeUpdate();
        }
    }

    private static void insertBill(Connection c, UUID unit, String treatment, String water, String taxes) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bills (tenant_id, unit_id, reference_month, status, water_value, "
                        + "sewer_value, debits_value, credits_value, taxes_value, total_value, tax_treatment) "
                        + "VALUES (?, ?, DATE '2026-11-01', 'NORMAL', ?::numeric, 0, 0, 0, ?::numeric, "
                        + "?::numeric, ?)")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, unit);
            ps.setString(3, water);
            ps.setString(4, taxes);
            // total coerente com a formula (agua - tributo) para isolar o CHECK de tratamento
            ps.setString(5, new java.math.BigDecimal(water).subtract(new java.math.BigDecimal(taxes)).toPlainString());
            ps.setString(6, treatment);
            ps.executeUpdate();
        }
    }

    private static void insertBillWithTotal(Connection c, UUID unit, String wrongTotal) throws SQLException {
        // agua 112,00 + esgoto 89,60 - tributo 12,00; o total informado e o errado, para violar o CHECK
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bills (tenant_id, unit_id, reference_month, status, water_value, "
                        + "sewer_value, debits_value, credits_value, taxes_value, total_value) "
                        + "VALUES (?, ?, DATE '2026-11-01', 'NORMAL', 112.00, 89.60, 0, 0, 12.00, ?::numeric)")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, unit);
            ps.setString(3, wrongTotal);
            ps.executeUpdate();
        }
    }

    private static UUID insertBatch(Connection c) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bill_audit_batches (id, tenant_id, run_id, reference_month, status) "
                        + "VALUES (?, ?, ?, DATE '2026-11-01', 'OPEN')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, UUID.randomUUID());
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertBillWithBatch(Connection c, UUID unit, String total, UUID batch) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bills (tenant_id, unit_id, reference_month, status, water_value, "
                        + "sewer_value, debits_value, credits_value, taxes_value, total_value, audit_batch_id) "
                        + "VALUES (?, ?, DATE '2026-11-01', 'NORMAL', 112.00, 89.60, 0, 0, 12.00, ?::numeric, ?)")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, unit);
            ps.setString(3, total);
            ps.setObject(4, batch);
            ps.executeUpdate();
        }
    }
}
