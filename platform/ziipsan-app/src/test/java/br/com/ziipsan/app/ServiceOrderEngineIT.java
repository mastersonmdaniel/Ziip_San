package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.serviceorder.EffectHandlerRegistry;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.serviceorder.TargetVerifierRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.31 (V9_1) on the real PostgreSQL, over the provisioned tenant.
 *
 * <p>Live proof: the engine tables and the V9_1 history entry exist; the
 * history FK refuses a nonexistent order and NULL is refused since P1.3
 * (V3_12 NOT NULL; FK since V9_1; the plan's V3_6 already existed, P0.16);
 * the full lifecycle runs
 * (create, submit, approve, schedule, execute, close) with transactional
 * and idempotent effect application; invalid transitions are refused by
 * the service and by the database trigger; a class without a registered
 * verifier cannot be declared, while the GIS classes become declarable
 * through the SQL adapter over the real P1.26 tables; the work-safety
 * gates block schedule and close per hazard flag; batch and inverse
 * orders behave; notes raise the personal-data alert; the unit history
 * tab source returns orders, status changes and effects with who, when,
 * before and after; internal and field orders separate in reports.
 *
 * <p>Tests share the provisioned tenant (dropped at JVM shutdown) and
 * commit their fixtures: every code, matricula and parameter key is
 * unique per test, and tenant-global parameter rows are deleted in a
 * finally block, so no test leaks into another.
 */
class ServiceOrderEngineIT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    private static ServiceOrderService service;
    private static final List<String> appliedEffects = new ArrayList<>();

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
        service = new ServiceOrderService(ProvisionedTenant.appDs, SCHEMA, TENANT);
        service.effects().register("IT_EFFECT", ctx -> appliedEffects.add(
                ctx.orderId() + ":" + ctx.targetId() + ":" + ctx.effectCode()));
        service.effects().register("IT_BATCH_EFFECT", ctx -> appliedEffects.add(
                ctx.orderId() + ":" + ctx.targetId() + ":" + ctx.effectCode()));
    }

    // ------------------------------------------------------------------
    // Fixtures (committed: the service reads through its own connections)
    // ------------------------------------------------------------------

    private static Connection asTenant() throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
        }
        return c;
    }

    private static void commit(Connection c) throws SQLException {
        c.commit();
        c.close();
    }

    private static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
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

    private static UUID insertGisNode(Connection c) throws SQLException {
        // GIS trigger functions (e.g. gis_check_class_package) reference catalog
        // tables unqualified, so the fixture session needs the tenant schema on
        // search_path — the same convention Gis01MigrationTest uses. The class
        // trigger also requires the domain's package to be ACTIVE for the
        // tenant: the app role cannot write platform.tenant_vertical, so the
        // fixture activates the sanitation vertical via the migrator role
        // (again the Gis01MigrationTest convention) before inserting.
        try (Connection m = ProvisionedTenant.migratorDs.getConnection();
             Statement st = m.createStatement()) {
            m.setAutoCommit(false);
            st.execute("INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)"
                    + " VALUES ('" + TENANT + "', 'sanitation', 'ACTIVE', NOW()) ON CONFLICT DO NOTHING");
            m.commit();
        }
        try (Statement st = c.createStatement()) {
            st.execute("SET LOCAL search_path = " + SCHEMA + ", public");
        }
        UUID domainId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".gis_domain (id, tenant_id, key, package, label, map_color_token, sort_order)"
                        + " VALUES (?, ?, ?, 'sanitation', 'Agua', 'map-water', 1)")) {
            ps.setObject(1, domainId);
            ps.setObject(2, TENANT);
            ps.setString(3, uniq("IT-DOM"));
            ps.executeUpdate();
        }
        UUID classId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".gis_feature_class (id, tenant_id, code, domain, topology_role, geometry_type, origin)"
                        + " VALUES (?, ?, ?, ?, 'NODE', 'POINT', 'TENANT')")) {
            ps.setObject(1, classId);
            ps.setObject(2, TENANT);
            ps.setString(3, uniq("IT-CLS"));
            ps.setString(4, lastDomainKey(c));
            ps.executeUpdate();
        }
        UUID typeId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".gis_feature_type (id, tenant_id, class_id, code, label, origin)"
                        + " VALUES (?, ?, ?, ?, 'Tipo de teste', 'TENANT')")) {
            ps.setObject(1, typeId);
            ps.setObject(2, TENANT);
            ps.setObject(3, classId);
            ps.setString(4, uniq("IT-FT"));
            ps.executeUpdate();
        }
        UUID nodeId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".gis_node (id, tenant_id, code, feature_type_id, capture_method, geom)"
                        + " VALUES (?, ?, ?, ?, 'DIGITIZED', ST_GeomFromText('POINTZ(-49 -16 0)', 4674))")) {
            ps.setObject(1, nodeId);
            ps.setObject(2, TENANT);
            ps.setString(3, uniq("IT-NODE"));
            ps.setObject(4, typeId);
            ps.executeUpdate();
        }
        return nodeId;
    }

    private static String lastDomainKey(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT key FROM " + SCHEMA + ".gis_domain WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 1")) {
            ps.setObject(1, TENANT);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static void insertParameter(Connection c, String key, String valueJson) throws SQLException {
        UUID versionId = UUID.randomUUID();
        String hash = "a".repeat(64);
        UUID proposer = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".parameter_versions (id, tenant_id, definition_key, value, valid_from,"
                        + " proposed_by, approved_by, approved_at, status, content_hash)"
                        + " VALUES (?, ?, ?, ?::jsonb, CURRENT_DATE, ?, ?, NOW(), 'APPROVED', ?)")) {
            ps.setObject(1, versionId);
            ps.setObject(2, TENANT);
            ps.setString(3, key);
            ps.setString(4, valueJson);
            ps.setObject(5, proposer);
            ps.setObject(6, approver);
            ps.setString(7, hash);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".parameter_acceptances (id, tenant_id, version_id, accepted_by,"
                        + " accepter_name, accepter_role, content_hash) VALUES (?, ?, ?, ?, 'Aceite de teste', 'Teste', ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT);
            ps.setObject(3, versionId);
            ps.setObject(4, UUID.randomUUID());
            ps.setString(5, hash);
            ps.executeUpdate();
        }
    }

    private static void deleteParameter(Connection c, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM " + SCHEMA + ".parameter_acceptances WHERE tenant_id = ? AND version_id IN"
                        + " (SELECT id FROM " + SCHEMA + ".parameter_versions WHERE tenant_id = ? AND definition_key = ?)")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, TENANT);
            ps.setString(3, key);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM " + SCHEMA + ".parameter_versions WHERE tenant_id = ? AND definition_key = ?")) {
            ps.setObject(1, TENANT);
            ps.setString(2, key);
            ps.executeUpdate();
        }
    }

    private ServiceOrderService.ServiceTypeInput internalType(String code, boolean approval, String effectCode) {
        return new ServiceOrderService.ServiceTypeInput(code, "Tipo " + code, "TEST_OP", effectCode,
                "{\"required\": [\"newValue\"], \"properties\": {\"newValue\": \"string\"}}",
                ServiceOrderEnums.TypeIndicator.INTERNAL, approval, false, null,
                List.of(TargetType.CONSUMER_UNIT), null, null, null,
                false, false, false, false, List.of());
    }

    private ServiceOrderService.ServiceTypeInput fieldType(String code, boolean confined, Integer minCrew) {
        return new ServiceOrderService.ServiceTypeInput(code, "Tipo " + code, "TEST_OP", null, null,
                ServiceOrderEnums.TypeIndicator.FIELD, false, true,
                ServiceOrderEnums.OperationalOrigin.INSPECTION,
                List.of(TargetType.CONSUMER_UNIT, TargetType.LOCATION_ONLY), null, null, minCrew,
                confined, false, false, false, List.of());
    }

    private ServiceOrderService.OrderInput orderInput(String typeCode, UUID creator) {
        return new ServiceOrderService.OrderInput(typeCode, ServiceOrderEnums.Origin.INTERNAL,
                ServiceOrderEnums.ExecutionMode.INTERNAL, ServiceOrderEnums.Priority.NORMAL, creator,
                null, null, null, null);
    }

    private UUID unitFixture() throws SQLException {
        Connection c = asTenant();
        try {
            UUID unit = insertUnit(c, uniq("IT-OS"));
            commit(c);
            return unit;
        } catch (SQLException e) {
            c.rollback();
            c.close();
            throw e;
        }
    }

    /**
     * P1-C (integracao): protocolo real para a OS externa — a FK
     * fk_orders_protocol (V8_1 + repetivel) recusa protocolo inexistente, e
     * o teste passava UUID aleatorio (na camada B nao havia FK). Numero
     * fixo alto para nao colidir com a sequencia do ProtocolIT no mesmo
     * tenant (que abre a partir de 1).
     */
    private UUID protocolFixture() throws SQLException {
        Connection c = asTenant();
        try {
            UUID spec = UUID.randomUUID();
            UUID protocol = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".service_protocol_specs (id, tenant_id, code, description, deadline_days, required_documents) VALUES (?, ?, ?, ?, ?, '[]'::jsonb)")) {
                ps.setObject(1, spec);
                ps.setObject(2, TENANT);
                ps.setString(3, uniq("IT-SPEC"));
                ps.setString(4, "Especificação de fixture");
                ps.setInt(5, 10);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".service_protocols (id, tenant_id, protocol_year, protocol_number, channel, spec_id, location_text, requester_name, requester_contact, requester_role, opened_date, original_due_date, current_due_date, status, created_by) VALUES (?, ?, 2026, 987654321, 'COUNTER', ?, 'Rua Fixture', 'Solicitante', 'solicitante@teste', 'THIRD_PARTY', CURRENT_DATE, CURRENT_DATE + 30, CURRENT_DATE + 30, 'OPEN', ?)")) {
                ps.setObject(1, protocol);
                ps.setObject(2, TENANT);
                ps.setObject(3, spec);
                ps.setObject(4, UUID.randomUUID());
                ps.executeUpdate();
            }
            commit(c);
            return protocol;
        } catch (SQLException e) {
            c.rollback();
            c.close();
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // Migration and history constraints
    // ------------------------------------------------------------------

    @Test
    @DisplayName("V9_1: tabelas do motor existem e a versao esta no historico do tenant")
    void migrationApplied() throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT installed_rank FROM " + SCHEMA + ".flyway_schema_history"
                             + " WHERE installed_rank IS NOT NULL AND version = '9.1'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("V9_1 no historico do tenant").isTrue();
            }
        }
        try (Connection c = asTenant()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = ?"
                            + " AND table_name IN ('service_types', 'service_orders', 'service_order_targets',"
                            + " 'service_order_effects', 'service_order_batches', 'service_order_history',"
                            + " 'service_order_notes', 'service_order_safety', 'service_order_crew',"
                            + " 'service_order_entry_permits', 'service_order_close_reasons')")) {
                ps.setString(1, SCHEMA);
                List<String> found = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        found.add(rs.getString(1));
                    }
                }
                assertThat(found).hasSize(11);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("historico (P1.3/R1): NULL recusado (23502); FK recusa OS inexistente (23503)")
    void historyOrderRequiredAndFkEnforced() throws SQLException {
        // P1.3 (V3_12): a V3_6 reservada no plano já existia (P0.16), então o
        // NOT NULL chegou na V3_12; a FK (fk_ush_service_order) já existia
        // desde a V9_1 (a V9_2 do tenant é da P3.4).
        // Este teste substitui o da P1-B, que afirmava "NULL admitido até a P1.3".
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, uniq("IT-HIST"));
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO " + SCHEMA + ".unit_status_history (tenant_id, unit_id, service_kind,"
                                    + " to_status_id, service_order_id) VALUES (?, ?, 'WATER', ?, NULL)")) {
                        ps.setObject(1, TENANT);
                        ps.setObject(2, unit);
                        ps.setObject(3, UUID.randomUUID());
                        ps.executeUpdate();
                    }
                }).isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23502"));
                c.rollback(savepoint);
                UUID order = service.createOrder(orderInput(declareInternal("IT-HIST-TYPE"), UUID.randomUUID())).id();
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + SCHEMA + ".unit_status_history (tenant_id, unit_id, service_kind,"
                                + " to_status_id, service_order_id) VALUES (?, ?, 'WATER', ?, ?)")) {
                    ps.setObject(1, TENANT);
                    ps.setObject(2, unit);
                    ps.setObject(3, UUID.randomUUID());
                    ps.setObject(4, order);
                    ps.executeUpdate();
                }
                savepoint = c.setSavepoint();
                assertThatThrownBy(() -> {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO " + SCHEMA + ".unit_status_history (tenant_id, unit_id, service_kind,"
                                    + " to_status_id, service_order_id) VALUES (?, ?, 'WATER', ?, ?)")) {
                        ps.setObject(1, TENANT);
                        ps.setObject(2, unit);
                        ps.setObject(3, UUID.randomUUID());
                        ps.setObject(4, UUID.randomUUID());
                        ps.executeUpdate();
                    }
                }).isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23503"));
                c.rollback(savepoint);
            } finally {
                c.rollback();
            }
        }
    }

    private String declareInternal(String code) {
        service.declareType(internalType(code, false, "IT_EFFECT"), UUID.randomUUID());
        return code;
    }

    // ------------------------------------------------------------------
    // Full lifecycle, approval, transitions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ciclo interno completo: efeito aplicado uma vez, idempotente, e encerrado")
    void internalLifecycleWithIdempotentEffect() throws SQLException {
        String code = uniq("IT-LIFE");
        service.declareType(internalType(code, false, "IT_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        UUID unit = unitFixture();
        var order = service.createOrder(orderInput(code, creator));
        final UUID orderId = order.id();
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.DRAFT);
        UUID target = service.addTarget(orderId, new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unit, null, null, null, "{\"newValue\": \"x\"}"), creator);
        assertThat(target).isNotNull();
        int before = appliedEffects.size();
        order = service.submit(orderId, creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.OPEN);
        order = service.execute(orderId, creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.EXECUTED);
        assertThat(appliedEffects.size()).isEqualTo(before + 1);
        order = service.execute(orderId, creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.EXECUTED);
        assertThat(appliedEffects.size()).isEqualTo(before + 1);
        order = service.closeExecuted(orderId, creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.CLOSED_EXECUTED);
    }

    @Test
    @DisplayName("efeito sem manipulador nao e marcado: execucao recusada e transacao desfeita")
    void effectWithoutHandlerIsRefusedTransactionally() throws SQLException {
        String code = uniq("IT-NOHANDLER");
        service.declareType(internalType(code, false, "IT_MISSING_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        UUID unit = unitFixture();
        var order = service.createOrder(orderInput(code, creator));
        UUID unitTarget = service.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unit, null, null, null, "{\"newValue\": \"x\"}"), creator);
        service.submit(order.id(), creator);
        assertThatThrownBy(() -> service.execute(order.id(), creator))
                .isInstanceOf(ApiException.Conflict.class);
        assertThat(service.getOrder(order.id()).status()).isEqualTo(ServiceOrderStatus.OPEN);
        try (Connection c = asTenant()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM " + SCHEMA + ".service_order_effects WHERE tenant_id = ? AND order_id = ?")) {
                ps.setObject(1, TENANT);
                ps.setObject(2, order.id());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).isEqualTo(0);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT effect_applied FROM " + SCHEMA + ".service_order_targets WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, TENANT);
                ps.setObject(2, unitTarget);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getBoolean(1)).isFalse();
                }
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("aprovacao: criador nao aprova a propria OS; outro aprova; aprovacao gravada")
    void approvalFlow() throws SQLException {
        String code = uniq("IT-APPR");
        service.declareType(internalType(code, true, "IT_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        UUID unit = unitFixture();
        var order = service.createOrder(orderInput(code, creator));
        final UUID orderId = order.id();
        service.addTarget(orderId, new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unit, null, null, null, "{\"newValue\": \"x\"}"), creator);
        order = service.submit(orderId, creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> service.approve(orderId, creator))
                .isInstanceOf(ApiException.Validation.class);
        order = service.approve(orderId, approver);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.OPEN);
        assertThat(order.approvedBy()).isEqualTo(approver);
        order = service.execute(orderId, creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.EXECUTED);
    }

    @Test
    @DisplayName("transicao invalida recusada pelo servico e pelo trigger do banco")
    void invalidTransitionRefused() throws SQLException {
        String code = uniq("IT-TRANS");
        service.declareType(internalType(code, false, "IT_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        var order = service.createOrder(orderInput(code, creator));
        UUID orderId = order.id();
        assertThatThrownBy(() -> service.closeExecuted(orderId, creator))
                .isInstanceOf(ApiException.Conflict.class);
        try (Connection c = asTenant()) {
            try {
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE " + SCHEMA + ".service_orders SET status = 'CLOSED_EXECUTED'"
                                    + " WHERE tenant_id = ? AND id = ?")) {
                        ps.setObject(1, TENANT);
                        ps.setObject(2, orderId);
                        ps.executeUpdate();
                    }
                }).isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
                c.rollback(savepoint);
                assertThatThrownBy(() -> {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO " + SCHEMA + ".service_orders (id, tenant_id, service_type_id, origin,"
                                    + " execution_mode, status, created_by)"
                                    + " SELECT ?, ?, service_type_id, 'INTERNAL', 'INTERNAL', 'OPEN', ?"
                                    + " FROM " + SCHEMA + ".service_orders WHERE tenant_id = ? AND id = ?")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setObject(2, TENANT);
                        ps.setObject(3, creator);
                        ps.setObject(4, TENANT);
                        ps.setObject(5, orderId);
                        ps.executeUpdate();
                    }
                }).isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
                c.rollback(savepoint);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("origem e modo: externa exige protocolo, interna proibe, modo segue o indicador do tipo")
    void originAndModeRules() throws SQLException {
        String internal = uniq("IT-OM-I");
        String field = uniq("IT-OM-F");
        service.declareType(internalType(internal, false, "IT_EFFECT"), UUID.randomUUID());
        service.declareType(fieldType(field, false, null), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        assertThatThrownBy(() -> service.createOrder(new ServiceOrderService.OrderInput(
                internal, ServiceOrderEnums.Origin.EXTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, creator, null, null, null, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.createOrder(new ServiceOrderService.OrderInput(
                internal, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, creator, UUID.randomUUID(), null, null, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.createOrder(new ServiceOrderService.OrderInput(
                internal, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.FIELD,
                ServiceOrderEnums.Priority.NORMAL, creator, null, null, null, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.createOrder(new ServiceOrderService.OrderInput(
                field, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, creator, null, null, null, null)))
                .isInstanceOf(ApiException.Validation.class);
        var external = service.createOrder(new ServiceOrderService.OrderInput(
                internal, ServiceOrderEnums.Origin.EXTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, creator, protocolFixture(), null, null, null));
        assertThat(external.protocolId()).isNotNull();
        var contractor = service.createOrder(new ServiceOrderService.OrderInput(
                field, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.CONTRACTOR,
                ServiceOrderEnums.Priority.NORMAL, creator, null, null, null, null));
        assertThat(contractor.executionMode()).isEqualTo(ServiceOrderEnums.ExecutionMode.CONTRACTOR);
    }

    @Test
    @DisplayName("carga do efeito validada pelo esquema do tipo; alvo fora do tipo recusado")
    void effectPayloadAndTargetClassValidation() throws SQLException {
        String code = uniq("IT-PAYLOAD");
        service.declareType(internalType(code, false, "IT_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        UUID unit = unitFixture();
        var order = service.createOrder(orderInput(code, creator));
        assertThatThrownBy(() -> service.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unit, null, null, null, "{}"), creator))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.LOCATION_ONLY, null, "Rua de teste", null, null, null), creator))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, UUID.randomUUID(), null, null, null, "{\"newValue\": \"x\"}"), creator))
                .isInstanceOf(ApiException.NotFound.class);
    }

    // ------------------------------------------------------------------
    // Target verifiers
    // ------------------------------------------------------------------

    @Test
    @DisplayName("classe sem verificador nao pode ser declarada; GIS vira declaravel com o adaptador SQL")
    void unregisteredClassRefusedAndGisBecomesDeclarable() throws SQLException {
        String equipmentCode = uniq("IT-EQ");
        assertThatThrownBy(() -> service.declareType(new ServiceOrderService.ServiceTypeInput(
                equipmentCode, "Tipo " + equipmentCode, "TEST_OP", "IT_EFFECT", null,
                ServiceOrderEnums.TypeIndicator.FIELD, false, false, null,
                List.of(TargetType.EQUIPMENT), null, null, null, false, false, false, false, List.of()),
                UUID.randomUUID()))
                .isInstanceOf(ApiException.Validation.class);
        String gisCode = uniq("IT-GIS");
        assertThatThrownBy(() -> service.declareType(new ServiceOrderService.ServiceTypeInput(
                gisCode, "Tipo " + gisCode, "TEST_OP", "IT_EFFECT", null,
                ServiceOrderEnums.TypeIndicator.FIELD, false, false, null,
                List.of(TargetType.GIS_POINT), null, null, null, false, false, false, false, List.of()),
                UUID.randomUUID()))
                .isInstanceOf(ApiException.Validation.class);
        service.verifiers().register(TargetType.GIS_POINT,
                TargetVerifierRegistry.sqlVerifier(SCHEMA, "gis_node"));
        service.declareType(new ServiceOrderService.ServiceTypeInput(
                gisCode, "Tipo " + gisCode, "TEST_OP", "IT_EFFECT", null,
                ServiceOrderEnums.TypeIndicator.FIELD, false, false, null,
                List.of(TargetType.GIS_POINT), null, null, null, false, false, false, false, List.of()),
                UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        var order = service.createOrder(new ServiceOrderService.OrderInput(
                gisCode, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.FIELD,
                ServiceOrderEnums.Priority.NORMAL, creator, null, null, null, null));
        Connection c = asTenant();
        UUID node;
        try {
            node = insertGisNode(c);
            commit(c);
        } catch (SQLException e) {
            c.rollback();
            c.close();
            throw e;
        }
        UUID target = service.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.GIS_POINT, node, null, null, null, null), creator);
        assertThat(target).isNotNull();
        assertThatThrownBy(() -> service.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.GIS_POINT, UUID.randomUUID(), null, null, null, null), creator))
                .isInstanceOf(ApiException.NotFound.class);
    }

    // ------------------------------------------------------------------
    // Work safety
    // ------------------------------------------------------------------

    private ServiceOrderService.OrderInput fieldOrderInput(String typeCode, UUID creator) {
        return new ServiceOrderService.OrderInput(typeCode, ServiceOrderEnums.Origin.INTERNAL,
                ServiceOrderEnums.ExecutionMode.FIELD, ServiceOrderEnums.Priority.NORMAL, creator,
                null, null, null, null);
    }

    private void addQualifiedCrew(UUID orderId, UUID creator, int count, LocalDate validUntil) {
        for (int i = 0; i < count; i++) {
            service.addCrewMember(orderId, new ServiceOrderService.CrewInput(
                    UUID.randomUUID(), "EXECUTOR", "NR33-AUTORIZADO", validUntil), creator);
        }
    }

    @Test
    @DisplayName("SST confinado: sem analise, permissao e equipe minima nao programa nem encerra")
    void confinedSpaceGate() throws SQLException {
        String code = uniq("IT-SST");
        service.declareType(fieldType(code, true, 2), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        var order = service.createOrder(fieldOrderInput(code, creator));
        final UUID orderId = order.id();
        service.submit(orderId, creator);
        assertThatThrownBy(() -> service.schedule(orderId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), creator))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("analise de risco");
        service.recordSafety(orderId, new ServiceOrderService.SafetyInput(
                "AR-TESTE", "PE-TESTE", null, null, null), creator);
        assertThatThrownBy(() -> service.schedule(orderId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), creator))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("equipe minima");
        addQualifiedCrew(orderId, creator, 1, LocalDate.now(ZoneOffset.UTC).plusMonths(6));
        assertThatThrownBy(() -> service.schedule(orderId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), creator))
                .isInstanceOf(ApiException.Conflict.class);
        addQualifiedCrew(orderId, creator, 1, LocalDate.now(ZoneOffset.UTC).plusMonths(6));
        order = service.schedule(orderId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.SCHEDULED);
        order = service.startExecution(orderId, creator);
        order = service.execute(orderId, creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.EXECUTED);
        assertThatThrownBy(() -> service.closeExecuted(orderId, creator))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("PET");
        service.issueEntryPermit(orderId, new ServiceOrderService.PermitInput(
                "PET-TESTE-01", OffsetDateTime.now(ZoneOffset.UTC), creator, creator, null), creator);
        order = service.closeExecuted(orderId, creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.CLOSED_EXECUTED);
    }

    @Test
    @DisplayName("SST vala: acima de 1,25 m sem liberacao nao programa; com liberacao programa")
    void trenchGate() throws SQLException {
        String code = uniq("IT-TRENCH");
        service.declareType(new ServiceOrderService.ServiceTypeInput(
                code, "Tipo " + code, "TEST_OP", null, null,
                ServiceOrderEnums.TypeIndicator.FIELD, false, true,
                ServiceOrderEnums.OperationalOrigin.INSPECTION,
                List.of(TargetType.LOCATION_ONLY), null, null, null,
                false, false, true, false, List.of()), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        var order = service.createOrder(fieldOrderInput(code, creator));
        final UUID orderId = order.id();
        service.addTarget(orderId, new ServiceOrderService.TargetInput(
                TargetType.LOCATION_ONLY, null, "Rua de teste", null, null, null), creator);
        service.submit(orderId, creator);
        service.recordSafety(orderId, new ServiceOrderService.SafetyInput(
                "AR-TESTE", null, null, new BigDecimal("1.30"), null), creator);
        assertThatThrownBy(() -> service.schedule(orderId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), creator))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("1,25");
        addQualifiedCrew(orderId, creator, 1, LocalDate.now(ZoneOffset.UTC).plusMonths(6));
        service.recordSafety(orderId, new ServiceOrderService.SafetyInput(
                "AR-TESTE", null, null, new BigDecimal("1.30"), UUID.randomUUID()), creator);
        order = service.schedule(orderId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.SCHEDULED);
        // CHECKs diretos no banco, numa OS fresca (a OS do fluxo ja tem safety row).
        var fresh = service.createOrder(fieldOrderInput(code, creator));
        try (Connection c = asTenant()) {
            try {
                var savepoint = c.setSavepoint();
                // Depth above 1.25 m without a release is RECORDABLE (the row
                // documents the blocking condition); the schedule gate refuses
                // the start — proven by the flow above. Only the PET retention
                // rule is a table CHECK.
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + SCHEMA + ".service_order_safety (tenant_id, order_id, trench_depth_m)"
                                + " VALUES (?, ?, 2.00)")) {
                    ps.setObject(1, TENANT);
                    ps.setObject(2, fresh.id());
                    assertThat(ps.executeUpdate()).isEqualTo(1);
                }
                c.rollback(savepoint);
                savepoint = c.setSavepoint();
                assertThatThrownBy(() -> {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO " + SCHEMA + ".service_order_entry_permits (tenant_id, order_id, permit_code,"
                                    + " entry_at, retained_until) VALUES (?, ?, 'PET-CURTA', NOW(), NOW() + INTERVAL '1 year')")) {
                        ps.setObject(1, TENANT);
                        ps.setObject(2, fresh.id());
                        ps.executeUpdate();
                    }
                }).isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
                c.rollback(savepoint);
            } finally {
                c.rollback();
            }
        }
    }

    // ------------------------------------------------------------------
    // Batch, inverse, notes, history, reports, parameters
    // ------------------------------------------------------------------

    @Test
    @DisplayName("OS de lote: um pedido interno aplica o efeito por alvo, com marca por alvo")
    void batchOrderAppliesPerTarget() throws SQLException {
        String code = uniq("IT-BATCH");
        service.declareType(internalType(code, false, "IT_BATCH_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        UUID batch = service.createBatch(uniq("IT-BATCH"), "Lote de teste", creator);
        var order = service.createOrder(new ServiceOrderService.OrderInput(
                code, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, creator, null, batch, null, null));
        final UUID orderId = order.id();
        UUID unit1 = unitFixture();
        UUID unit2 = unitFixture();
        service.addTarget(orderId, new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unit1, null, null, null, "{\"newValue\": \"a\"}"), creator);
        service.addTarget(orderId, new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unit2, null, null, null, "{\"newValue\": \"b\"}"), creator);
        service.submit(orderId, creator);
        int before = (int) appliedEffects.stream().filter(s -> s.startsWith(orderId.toString())).count();
        order = service.execute(orderId, creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.EXECUTED);
        int after = (int) appliedEffects.stream().filter(s -> s.startsWith(orderId.toString())).count();
        assertThat(after - before).isEqualTo(2);
        try (Connection c = asTenant()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM " + SCHEMA + ".service_order_effects WHERE tenant_id = ? AND order_id = ?")) {
                ps.setObject(1, TENANT);
                ps.setObject(2, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).isEqualTo(2);
                }
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("OS inversa ligada: correcao referencia a OS original, sem reversao do efeito")
    void inverseOrderLinksOriginal() throws SQLException {
        String code = uniq("IT-INV");
        service.declareType(internalType(code, false, "IT_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        UUID unit = unitFixture();
        var original = service.createOrder(orderInput(code, creator));
        service.addTarget(original.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unit, null, null, null, "{\"newValue\": \"x\"}"), creator);
        service.submit(original.id(), creator);
        original = service.execute(original.id(), creator);
        var inverse = service.createOrder(new ServiceOrderService.OrderInput(
                code, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, creator, null, null, null, original.id()));
        assertThat(inverse.reversesOrderId()).isEqualTo(original.id());
        service.addTarget(inverse.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unit, null, null, null, "{\"newValue\": \"y\"}"), creator);
        service.submit(inverse.id(), creator);
        inverse = service.execute(inverse.id(), creator);
        assertThat(inverse.status()).isEqualTo(ServiceOrderStatus.EXECUTED);
        assertThat(service.getOrder(original.id()).status()).isEqualTo(ServiceOrderStatus.EXECUTED);
    }

    @Test
    @DisplayName("observacoes: dado com formato de documento levanta o alerta, texto simples nao")
    void notesRaisePersonalDataAlert() throws SQLException {
        String code = uniq("IT-NOTE");
        service.declareType(internalType(code, false, "IT_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        var order = service.createOrder(orderInput(code, creator));
        UUID alertNote = service.addNote(order.id(), creator, ServiceOrderEnums.NoteVisibility.INTERNAL,
                "Ligar para o titular do documento 000.000.000-00 antes da visita.");
        assertThat(service.noteHasPersonalDataAlert(alertNote)).isTrue();
        UUID plainNote = service.addNote(order.id(), creator, ServiceOrderEnums.NoteVisibility.USER_VISIBLE,
                "Visita reagendada para a proxima semana.");
        assertThat(service.noteHasPersonalDataAlert(plainNote)).isFalse();
    }

    @Test
    @DisplayName("aba Historico da unidade: OS aplicada, mudanca de situacao e efeito, com quem e quando")
    void unitHistoryTabSource() throws SQLException {
        String code = uniq("IT-HIST2");
        service.declareType(internalType(code, false, "IT_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        UUID unit = unitFixture();
        var order = service.createOrder(orderInput(code, creator));
        service.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unit, null, null, null, "{\"newValue\": \"x\"}"), creator);
        service.submit(order.id(), creator);
        service.execute(order.id(), creator);
        Connection c = asTenant();
        try {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".unit_status_history (tenant_id, unit_id, service_kind,"
                            + " to_status_id, service_order_id, created_by) VALUES (?, ?, 'WATER', ?, ?, ?)")) {
                ps.setObject(1, TENANT);
                ps.setObject(2, unit);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, order.id());
                ps.setObject(5, creator);
                ps.executeUpdate();
            }
            commit(c);
        } catch (SQLException e) {
            c.rollback();
            c.close();
            throw e;
        }
        var rows = service.unitHistory(unit);
        assertThat(rows.stream().map(ServiceOrderService.HistoryRow::source))
                .contains("ORDER_EVENT", "UNIT_STATUS", "EFFECT");
        assertThat(rows.stream().map(ServiceOrderService.HistoryRow::serviceOrderId))
                .contains(order.id());
        assertThat(rows.stream().map(ServiceOrderService.HistoryRow::actorId))
                .contains(creator);
        assertThat(rows.stream().map(ServiceOrderService.HistoryRow::occurredAt))
                .allSatisfy(t -> assertThat(t).isNotNull());
    }

    @Test
    @DisplayName("relatorios separam OS interna de OS de campo; campo exige inicio antes de executar")
    void reportsSeparateInternalAndField() throws SQLException {
        String internal = uniq("IT-REP-I");
        String field = uniq("IT-REP-F");
        service.declareType(internalType(internal, false, "IT_EFFECT"), UUID.randomUUID());
        service.declareType(fieldType(field, false, null), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        service.createOrder(orderInput(internal, creator));
        var fieldOrder = service.createOrder(fieldOrderInput(field, creator));
        service.submit(fieldOrder.id(), creator);
        assertThatThrownBy(() -> service.execute(fieldOrder.id(), creator))
                .isInstanceOf(ApiException.Conflict.class);
        var counts = service.countByMode();
        assertThat(counts.stream().map(ServiceOrderService.ModeCount::executionMode))
                .contains(ServiceOrderEnums.ExecutionMode.INTERNAL, ServiceOrderEnums.ExecutionMode.FIELD);
    }

    @Test
    @DisplayName("encerrar sem execucao exige motivo do catalogo; com motivo encerra")
    void closeNotExecutedRequiresReason() throws SQLException {
        String code = uniq("IT-CLOSE");
        service.declareType(internalType(code, false, "IT_EFFECT"), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        var order = service.createOrder(orderInput(code, creator));
        final UUID orderId = order.id();
        assertThatThrownBy(() -> service.closeNotExecuted(orderId, null, creator))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.closeNotExecuted(orderId, "NOPE", creator))
                .isInstanceOf(ApiException.Validation.class);
        service.createCloseReason("CANCELADO_SOLICITANTE", "Cancelado pelo solicitante", creator);
        order = service.closeNotExecuted(orderId, "CANCELADO_SOLICITANTE", creator);
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.CLOSED_NOT_EXECUTED);
        assertThatThrownBy(() -> service.closeNotExecuted(orderId, "CANCELADO_SOLICITANTE", creator))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("prazo do tenant so reduz: override abaixo encurta o vencimento, acima e recusado")
    void deadlineOverrideOnlyReducesLive() throws SQLException {
        String code = uniq("IT-DL");
        service.declareType(new ServiceOrderService.ServiceTypeInput(
                code, "Tipo " + code, "TEST_OP", null, null,
                ServiceOrderEnums.TypeIndicator.FIELD, false, true,
                ServiceOrderEnums.OperationalOrigin.PREVENTIVE_PLAN,
                List.of(TargetType.LOCATION_ONLY), null, 72, null,
                false, false, false, false, List.of()), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        Connection c = asTenant();
        try {
            insertParameter(c, "so.deadline_hours_by_type", "{\"" + code + "\": 24}");
            commit(c);
        } catch (SQLException e) {
            c.rollback();
            c.close();
            throw e;
        }
        try {
            var order = service.createOrder(fieldOrderInput(code, creator));
            assertThat(order.dueAt()).isNotNull();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            assertThat(order.dueAt()).isAfter(now.plusHours(23)).isBefore(now.plusHours(25));
        } finally {
            Connection cleanup = asTenant();
            try {
                deleteParameter(cleanup, "so.deadline_hours_by_type");
                commit(cleanup);
            } catch (SQLException e) {
                cleanup.rollback();
                cleanup.close();
                throw e;
            }
        }
        c = asTenant();
        try {
            insertParameter(c, "so.deadline_hours_by_type", "{\"" + code + "\": 100}");
            commit(c);
        } catch (SQLException e) {
            c.rollback();
            c.close();
            throw e;
        }
        try {
            assertThatThrownBy(() -> service.createOrder(fieldOrderInput(code, creator)))
                    .isInstanceOf(ApiException.Validation.class);
        } finally {
            Connection cleanup = asTenant();
            try {
                deleteParameter(cleanup, "so.deadline_hours_by_type");
                commit(cleanup);
            } catch (SQLException e) {
                cleanup.rollback();
                cleanup.close();
                throw e;
            }
        }
    }

    @Test
    @DisplayName("tipos operacionais desligados pelo parametro nao geram OS; religados geram")
    void operationalTypesSwitch() throws SQLException {
        String code = uniq("IT-OPSW");
        service.declareType(fieldType(code, false, null), UUID.randomUUID());
        UUID creator = UUID.randomUUID();
        Connection c = asTenant();
        try {
            insertParameter(c, "so.operational_types_enabled", "false");
            commit(c);
        } catch (SQLException e) {
            c.rollback();
            c.close();
            throw e;
        }
        try {
            assertThatThrownBy(() -> service.createOrder(fieldOrderInput(code, creator)))
                    .isInstanceOf(ApiException.Conflict.class);
        } finally {
            Connection cleanup = asTenant();
            try {
                deleteParameter(cleanup, "so.operational_types_enabled");
                commit(cleanup);
            } catch (SQLException e) {
                cleanup.rollback();
                cleanup.close();
                throw e;
            }
        }
        var order = service.createOrder(fieldOrderInput(code, creator));
        assertThat(order.status()).isEqualTo(ServiceOrderStatus.DRAFT);
    }
}
