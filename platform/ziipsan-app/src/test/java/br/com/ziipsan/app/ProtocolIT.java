package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.protocol.ProtocolChannel;
import br.com.ziipsan.core.protocol.ProtocolService;
import br.com.ziipsan.core.protocol.ProtocolStatus;
import br.com.ziipsan.core.protocol.RequesterRole;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.36 (V8_1) on the real PostgreSQL, over the provisioned tenant.
 *
 * <p>Live proof: the V8_1 migration and the repeatable FK guard applied
 * on this fully migrated tenant (fresh install order is V8_1 before
 * V9_1, so the FK cannot come from V8_1 alone — see the V8_1 header);
 * the sac.* parameter definitions exist; the deadline engine counts
 * calendar days by default and business days with national, state and
 * municipal holidays by parameter; parallel openings never duplicate
 * a number per tenant and year; the specification's unit/point rules
 * and the non-customer name+contact rule are enforced; closure
 * requires a classified reason; an extension moves only the current
 * deadline and records reason, author and notice; a duplicate links
 * its original and carries the agency reference; an external order
 * without a protocol is refused, an internal order without one is
 * allowed, and an order pointing at a nonexistent protocol is refused
 * by the database FK (1:N protocol -> orders).
 *
 * <p>Tests share the provisioned tenant and commit their fixtures:
 * every spec code, holiday date and parameter row is unique per test
 * (distinct years for the deadline tests, so no holiday leaks into
 * another test), and parameter/holiday rows are deleted in a finally
 * block, so no test leaks into another.
 */
class ProtocolIT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    private static ProtocolService protocols;
    private static ServiceOrderService orders;

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
        protocols = new ProtocolService(ProvisionedTenant.appDs, SCHEMA, TENANT);
        orders = new ServiceOrderService(ProvisionedTenant.appDs, SCHEMA, TENANT);
    }

    // ------------------------------------------------------------------
    // Fixtures (committed: the services read through their own connections)
    // ------------------------------------------------------------------

    private static Connection asTenant() throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
        }
        return c;
    }

    private static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static ProtocolService.SpecView spec(int deadlineDays, boolean requiresUnit, boolean requiresAddress) {
        return protocols.declareSpec(new ProtocolService.SpecInput(
                uniq("SPEC"), "Especificacao de teste", deadlineDays, "[]", requiresUnit, requiresAddress));
    }

    private static ProtocolService.OpenInput openInput(ProtocolService.SpecView spec, LocalDate opened) {
        return new ProtocolService.OpenInput(spec.code(), ProtocolChannel.COUNTER, null,
                "Ponto de teste, Rua das Aguas", null, "Solicitante Teste", "11999999999",
                RequesterRole.USER, opened, UUID.randomUUID(), null, null, null, null, false);
    }

    private static void insertParameter(Connection c, String key, String valueJson) throws SQLException {
        UUID versionId = UUID.randomUUID();
        String hash = "b".repeat(64);
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

    private static void insertHoliday(Connection c, LocalDate date, String scope,
                                      String stateCode, String municipalityIbge) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".holidays (id, tenant_id, holiday_date, scope, state_code,"
                        + " municipality_ibge, description, source, kind) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'HOLIDAY')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT);
            ps.setDate(3, java.sql.Date.valueOf(date));
            ps.setString(4, scope);
            ps.setString(5, stateCode);
            ps.setString(6, municipalityIbge);
            ps.setString(7, "Feriado de teste");
            ps.setString(8, "calendario de teste");
            ps.executeUpdate();
        }
    }

    private static void deleteHoliday(Connection c, LocalDate date) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM " + SCHEMA + ".holidays WHERE tenant_id = ? AND holiday_date = ?")) {
            ps.setObject(1, TENANT);
            ps.setDate(2, java.sql.Date.valueOf(date));
            ps.executeUpdate();
        }
    }

    private static UUID insertUnit(Connection c, String matricula) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".consumer_units (id, tenant_id, matricula, dv) VALUES (?, ?, ?, '0')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setString(3, matricula);
            ps.executeUpdate();
        }
        return id;
    }

    private static String declareOrderType() {
        String code = uniq("IT-ORD");
        orders.declareType(new ServiceOrderService.ServiceTypeInput(
                code, "Tipo de teste", "OP_TESTE", "IT_EFFECT", null,
                ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null,
                List.of(TargetType.LOCATION_ONLY), null, null, null,
                false, false, false, false, List.of()), UUID.randomUUID());
        return code;
    }

    private static boolean constraintExists(String constraintName) throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid"
                             + " JOIN pg_namespace n ON n.oid = t.relnamespace"
                             + " WHERE c.conname = ? AND n.nspname = ?")) {
            ps.setString(1, constraintName);
            ps.setString(2, SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int countMigrator(String sql, Object... params) throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ------------------------------------------------------------------
    // Migration proof
    // ------------------------------------------------------------------

    @Test
    @DisplayName("migracao: V8_1 e o repetivel aplicados, FK da OS presente, parametros sac.* definidos")
    void migrationAppliedWithOrderFkAndSacParameters() throws Exception {
        assertThat(countMigrator(
                "SELECT COUNT(*) FROM " + SCHEMA + ".flyway_schema_history WHERE installed_script = ?",
                "V8_1__service_protocol.sql")).isEqualTo(1);
        assertThat(countMigrator(
                "SELECT COUNT(*) FROM " + SCHEMA + ".flyway_schema_history WHERE installed_script = ?",
                "R__service_protocol_order_fk.sql")).isEqualTo(1);
        assertThat(constraintExists("fk_orders_protocol")).isTrue();
        // V9_9 grants SELECT on the catalog to the app role, so this read uses appDs.
        try (Connection c = ProvisionedTenant.appDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM public.parameter_definitions WHERE definition_key IN"
                             + " ('sac.deadline_counting_rule', 'sac.holiday_scope', 'sac.extension_policy')")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
        }
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM " + SCHEMA + ".service_protocols")) {
            assertThat(rs.next()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // Deadlines: one test per counting rule, plus scopes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("prazo padrao CALENDAR: dias corridos a partir da abertura")
    void calendarDeadlineByDefault() {
        ProtocolService.SpecView spec = spec(5, false, false);
        ProtocolService.ProtocolView view = protocols.open(openInput(spec, LocalDate.of(2026, 11, 2)));
        assertThat(view.status()).isEqualTo(ProtocolStatus.OPEN);
        assertThat(view.protocolYear()).isEqualTo(2026);
        assertThat(view.protocolNumber()).isPositive();
        assertThat(view.formattedNumber()).matches("2026-\\d{6}");
        // Monday 2026-11-02 + 5 calendar days = Saturday 2026-11-07.
        assertThat(view.originalDueDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(view.currentDueDate()).isEqualTo(view.originalDueDate());
    }

    @Test
    @DisplayName("regra BUSINESS: fim de semana e feriado nacional nao contam")
    void businessDeadlineSkipsWeekendsAndNationalHoliday() throws Exception {
        Connection c = asTenant();
        try {
            insertParameter(c, "sac.deadline_counting_rule", "\"BUSINESS\"");
            insertHoliday(c, LocalDate.of(2026, 11, 4), "NATIONAL", null, null);
            c.commit();
            ProtocolService.SpecView spec = spec(5, false, false);
            ProtocolService.ProtocolView view = protocols.open(openInput(spec, LocalDate.of(2026, 11, 2)));
            // Tue 3 (1), Wed 4 holiday, Thu 5 (2), Fri 6 (3), Mon 9 (4), Tue 10 (5).
            assertThat(view.originalDueDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        } finally {
            deleteParameter(c, "sac.deadline_counting_rule");
            deleteHoliday(c, LocalDate.of(2026, 11, 4));
            c.commit();
            c.close();
        }
    }

    @Test
    @DisplayName("escopo NATIONAL_STATE: estadual da UF conta, de outra UF nao")
    void stateScopeCountsOnlyMatchingState() throws Exception {
        Connection c = asTenant();
        try {
            insertParameter(c, "sac.deadline_counting_rule", "\"BUSINESS\"");
            insertParameter(c, "sac.holiday_scope", "\"NATIONAL_STATE\"");
            insertHoliday(c, LocalDate.of(2027, 11, 2), "STATE", "SC", null);
            insertHoliday(c, LocalDate.of(2027, 11, 3), "STATE", "PR", null);
            insertHoliday(c, LocalDate.of(2027, 11, 4), "NATIONAL", null, null);
            c.commit();
            ProtocolService.SpecView spec = spec(3, false, false);
            ProtocolService.OpenInput input = new ProtocolService.OpenInput(
                    spec.code(), ProtocolChannel.COUNTER, null, "Ponto de teste", null,
                    "Solicitante Teste", "11999999999", RequesterRole.USER,
                    LocalDate.of(2027, 11, 1), UUID.randomUUID(), null, null, "SC", null, false);
            ProtocolService.ProtocolView view = protocols.open(input);
            // Tue 2 SC holiday, Wed 3 PR ignored (1), Thu 4 national holiday, Fri 5 (2), Mon 8 (3).
            assertThat(view.originalDueDate()).isEqualTo(LocalDate.of(2027, 11, 8));
        } finally {
            deleteParameter(c, "sac.deadline_counting_rule");
            deleteParameter(c, "sac.holiday_scope");
            deleteHoliday(c, LocalDate.of(2027, 11, 2));
            deleteHoliday(c, LocalDate.of(2027, 11, 3));
            deleteHoliday(c, LocalDate.of(2027, 11, 4));
            c.commit();
            c.close();
        }
    }

    @Test
    @DisplayName("escopo NATIONAL_STATE_MUNICIPAL: municipal do municipio conta, de outro nao")
    void municipalScopeCountsOnlyMatchingMunicipality() throws Exception {
        Connection c = asTenant();
        try {
            insertParameter(c, "sac.deadline_counting_rule", "\"BUSINESS\"");
            insertParameter(c, "sac.holiday_scope", "\"NATIONAL_STATE_MUNICIPAL\"");
            insertHoliday(c, LocalDate.of(2028, 11, 7), "MUNICIPAL", null, "4205407");
            insertHoliday(c, LocalDate.of(2028, 11, 9), "MUNICIPAL", null, "4106902");
            c.commit();
            ProtocolService.SpecView spec = spec(2, false, false);
            ProtocolService.OpenInput input = new ProtocolService.OpenInput(
                    spec.code(), ProtocolChannel.COUNTER, null, "Ponto de teste", null,
                    "Solicitante Teste", "11999999999", RequesterRole.USER,
                    LocalDate.of(2028, 11, 6), UUID.randomUUID(), null, null, "SC", "4205407", false);
            ProtocolService.ProtocolView view = protocols.open(input);
            // Tue 7 municipal match skipped, Wed 8 (1), Thu 9 other city counts (2).
            assertThat(view.originalDueDate()).isEqualTo(LocalDate.of(2028, 11, 9));
        } finally {
            deleteParameter(c, "sac.deadline_counting_rule");
            deleteParameter(c, "sac.holiday_scope");
            deleteHoliday(c, LocalDate.of(2028, 11, 7));
            deleteHoliday(c, LocalDate.of(2028, 11, 9));
            c.commit();
            c.close();
        }
    }

    // ------------------------------------------------------------------
    // Numbering, requester, target rules
    // ------------------------------------------------------------------

    @Test
    @DisplayName("aberturas paralelas nunca duplicam o numero por tenant e ano")
    void parallelOpeningsNeverDuplicateNumbers() throws Exception {
        ProtocolService.SpecView spec = spec(5, false, false);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ProtocolService.ProtocolView>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return protocols.open(openInput(spec, LocalDate.of(2026, 11, 2)));
            }));
        }
        ready.await();
        go.countDown();
        Set<String> numbers = new HashSet<>();
        for (Future<ProtocolService.ProtocolView> future : futures) {
            numbers.add(future.get().formattedNumber());
        }
        pool.shutdown();
        assertThat(numbers).hasSize(threads);
    }

    @Test
    @DisplayName("especificacao que exige matricula recusa protocolo sem unidade")
    void specRequiringUnitRefusesProtocolWithoutUnit() throws Exception {
        ProtocolService.SpecView spec = spec(5, true, false);
        assertThatThrownBy(() -> protocols.open(openInput(spec, LocalDate.of(2026, 11, 2))))
                .isInstanceOf(ApiException.Validation.class);
        Connection c = asTenant();
        UUID unitId;
        try {
            unitId = insertUnit(c, uniq("MAT"));
            c.commit();
        } finally {
            c.close();
        }
        ProtocolService.OpenInput withUnit = new ProtocolService.OpenInput(
                spec.code(), ProtocolChannel.PORTAL, unitId, null, null,
                "Solicitante Teste", "11999999999", RequesterRole.HOLDER,
                LocalDate.of(2026, 11, 2), UUID.randomUUID(), null, null, null, null, false);
        assertThat(protocols.open(withUnit).consumerUnitId()).isEqualTo(unitId);
    }

    @Test
    @DisplayName("especificacao que exige endereco recusa protocolo sem ponto")
    void specRequiringAddressRefusesProtocolWithoutPoint() {
        ProtocolService.SpecView spec = spec(5, false, true);
        ProtocolService.OpenInput noPoint = new ProtocolService.OpenInput(
                spec.code(), ProtocolChannel.COUNTER, null, null, null,
                "Solicitante Teste", "11999999999", RequesterRole.USER,
                LocalDate.of(2026, 11, 2), UUID.randomUUID(), null, null, null, null, false);
        assertThatThrownBy(() -> protocols.open(noPoint))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(protocols.open(openInput(spec, LocalDate.of(2026, 11, 2))).locationText())
                .isEqualTo("Ponto de teste, Rua das Aguas");
    }

    @Test
    @DisplayName("sem unidade, o ponto e obrigatorio; solicitante sem cadastro exige nome e contato")
    void pointRequiredWithoutUnitAndNonCustomerRequiresNameAndContact() {
        ProtocolService.SpecView spec = spec(5, false, false);
        ProtocolService.OpenInput noPoint = new ProtocolService.OpenInput(
                spec.code(), ProtocolChannel.PHONE, null, null, null,
                "Solicitante Teste", "11999999999", RequesterRole.USER,
                LocalDate.of(2026, 11, 2), UUID.randomUUID(), null, null, null, null, false);
        assertThatThrownBy(() -> protocols.open(noPoint))
                .isInstanceOf(ApiException.Validation.class);
        ProtocolService.OpenInput noContact = new ProtocolService.OpenInput(
                spec.code(), ProtocolChannel.PHONE, null, "Ponto de teste", null,
                null, null, RequesterRole.USER,
                LocalDate.of(2026, 11, 2), UUID.randomUUID(), null, null, null, null, false);
        assertThatThrownBy(() -> protocols.open(noContact))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(protocols.open(openInput(spec, LocalDate.of(2026, 11, 2))).locationText())
                .isEqualTo("Ponto de teste, Rua das Aguas");
    }

    // ------------------------------------------------------------------
    // Closure, extension, duplicity
    // ------------------------------------------------------------------

    @Test
    @DisplayName("encerramento exige motivo classificado; fechar de OPEN e recusado")
    void closureRequiresClassifiedReason() {
        ProtocolService.SpecView spec = spec(5, false, false);
        ProtocolService.ProtocolView view = protocols.open(openInput(spec, LocalDate.of(2026, 11, 2)));
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> protocols.close(view.id(), "ANY", actor))
                .isInstanceOf(ApiException.Conflict.class);
        protocols.startProgress(view.id(), actor);
        protocols.markAnswerPending(view.id(), actor);
        protocols.markAnswered(view.id(), actor);
        assertThatThrownBy(() -> protocols.close(view.id(), null, actor))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> protocols.close(view.id(), "NOPE", actor))
                .isInstanceOf(ApiException.NotFound.class);
        protocols.declareCloseReason(new ProtocolService.CloseReasonInput("ATENDIDO", "Solicitacao atendida"));
        ProtocolService.ProtocolView closed = protocols.close(view.id(), "ATENDIDO", actor);
        assertThat(closed.status()).isEqualTo(ProtocolStatus.CLOSED);
        assertThat(closed.closeReasonCode()).isEqualTo("ATENDIDO");
    }

    @Test
    @DisplayName("prorrogacao move so o prazo corrente, registra motivo/autor/aviso e obedece a politica")
    void extensionPreservesOriginalDeadline() throws Exception {
        Connection c = asTenant();
        try {
            insertParameter(c, "sac.extension_policy", "\"ONCE_JUSTIFIED\"");
            insertParameter(c, "sac.deadline_counting_rule", "\"CALENDAR\"");
            c.commit();
            ProtocolService.SpecView spec = spec(10, false, false);
            ProtocolService.ProtocolView view = protocols.open(openInput(spec, LocalDate.of(2029, 11, 5)));
            assertThat(view.originalDueDate()).isEqualTo(LocalDate.of(2029, 11, 15));
            UUID author = UUID.randomUUID();
            ProtocolService.ExtensionView extension = protocols.extend(
                    view.id(), 5, "Aguardando documento do solicitante", author, "SMS enviado em 2029-11-06");
            assertThat(extension.previousDueDate()).isEqualTo(LocalDate.of(2029, 11, 15));
            assertThat(extension.newDueDate()).isEqualTo(LocalDate.of(2029, 11, 20));
            ProtocolService.ProtocolView after = protocols.get(view.id());
            assertThat(after.originalDueDate()).isEqualTo(LocalDate.of(2029, 11, 15));
            assertThat(after.currentDueDate()).isEqualTo(LocalDate.of(2029, 11, 20));
            assertThatThrownBy(() -> protocols.extend(
                    view.id(), 5, "Segunda prorrogacao", author, "SMS enviado"))
                    .isInstanceOf(ApiException.Forbidden.class);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT previous_due_date, new_due_date, reason, notice FROM " + SCHEMA
                            + ".service_protocol_extensions WHERE tenant_id = ? AND protocol_id = ?")) {
                ps.setObject(1, TENANT);
                ps.setObject(2, view.id());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getDate(1).toLocalDate()).isEqualTo(LocalDate.of(2029, 11, 15));
                    assertThat(rs.getDate(2).toLocalDate()).isEqualTo(LocalDate.of(2029, 11, 20));
                    assertThat(rs.getString(3)).isEqualTo("Aguardando documento do solicitante");
                    assertThat(rs.getString(4)).isEqualTo("SMS enviado em 2029-11-06");
                }
            }
        } finally {
            deleteParameter(c, "sac.extension_policy");
            deleteParameter(c, "sac.deadline_counting_rule");
            c.commit();
            c.close();
        }
    }

    @Test
    @DisplayName("politica padrao NONE recusa prorrogacao")
    void defaultExtensionPolicyRefusesExtension() {
        ProtocolService.SpecView spec = spec(5, false, false);
        ProtocolService.ProtocolView view = protocols.open(openInput(spec, LocalDate.of(2026, 11, 2)));
        assertThatThrownBy(() -> protocols.extend(
                view.id(), 5, "Motivo", UUID.randomUUID(), "Aviso"))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    @DisplayName("duplicidade vincula o original e carrega a referencia da agencia; sem original, recusa")
    void duplicateLinksOriginalAndCarriesAgencyReference() {
        ProtocolService.SpecView spec = spec(5, false, false);
        ProtocolService.ProtocolView original = protocols.open(openInput(spec, LocalDate.of(2026, 11, 2)));
        ProtocolService.OpenInput duplicate = new ProtocolService.OpenInput(
                spec.code(), ProtocolChannel.REGULATOR, null, "Ponto de teste", null,
                "Solicitante Teste", "11999999999", RequesterRole.USER,
                LocalDate.of(2026, 11, 2), UUID.randomUUID(), original.id(), "AG-2026-991", null, null, false);
        ProtocolService.ProtocolView linked = protocols.open(duplicate);
        assertThat(linked.duplicateOfProtocolId()).isEqualTo(original.id());
        assertThat(linked.agencyReference()).isEqualTo("AG-2026-991");
        ProtocolService.OpenInput orphan = new ProtocolService.OpenInput(
                spec.code(), ProtocolChannel.REGULATOR, null, "Ponto de teste", null,
                "Solicitante Teste", "11999999999", RequesterRole.USER,
                LocalDate.of(2026, 11, 2), UUID.randomUUID(), UUID.randomUUID(), null, null, null, false);
        assertThatThrownBy(() -> protocols.open(orphan))
                .isInstanceOf(ApiException.NotFound.class);
    }

    // ------------------------------------------------------------------
    // Protocol -> OS link (ADR-0039 item 7)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("OS externa sem protocolo e recusada, interna sem protocolo passa, 1:N amarrado pela FK")
    void externalOrderRequiresProtocolAndOneProtocolBindsManyOrders() throws Exception {
        String typeCode = declareOrderType();
        ProtocolService.SpecView spec = spec(5, false, false);
        ProtocolService.ProtocolView protocol = protocols.open(openInput(spec, LocalDate.of(2026, 11, 2)));
        UUID actor = UUID.randomUUID();

        assertThatThrownBy(() -> orders.createOrder(new ServiceOrderService.OrderInput(
                typeCode, ServiceOrderEnums.Origin.EXTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, actor, null, null, null, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> orders.createOrder(new ServiceOrderService.OrderInput(
                typeCode, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, actor, protocol.id(), null, null, null)))
                .isInstanceOf(ApiException.Validation.class);

        ServiceOrderService.OrderView first = orders.createOrder(new ServiceOrderService.OrderInput(
                typeCode, ServiceOrderEnums.Origin.EXTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, actor, protocol.id(), null, null, null));
        ServiceOrderService.OrderView second = orders.createOrder(new ServiceOrderService.OrderInput(
                typeCode, ServiceOrderEnums.Origin.EXTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, actor, protocol.id(), null, null, null));
        ServiceOrderService.OrderView internal = orders.createOrder(new ServiceOrderService.OrderInput(
                typeCode, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, actor, null, null, null, null));
        assertThat(first.protocolId()).isEqualTo(protocol.id());
        assertThat(second.protocolId()).isEqualTo(protocol.id());
        assertThat(internal.protocolId()).isNull();
        assertThat(protocols.ordersOf(protocol.id())).extracting(ProtocolService.OrderRef::id)
                .containsExactlyInAnyOrder(first.id(), second.id());

        // The database FK itself refuses an order pointing at a protocol that does not exist.
        Connection c = asTenant();
        try {
            UUID typeId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM " + SCHEMA + ".service_types WHERE tenant_id = ? AND code = ?")) {
                ps.setObject(1, TENANT);
                ps.setString(2, typeCode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    typeId = (UUID) rs.getObject(1);
                }
            }
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + SCHEMA + ".service_orders (id, tenant_id, service_type_id,"
                                + " protocol_id, origin, execution_mode, priority, status, created_by)"
                                + " VALUES (?, ?, ?, ?, 'EXTERNAL', 'INTERNAL', 'NORMAL', 'DRAFT', ?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, TENANT);
                    ps.setObject(3, typeId);
                    ps.setObject(4, UUID.randomUUID());
                    ps.setObject(5, actor);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23503"));
            c.rollback();
        } finally {
            c.close();
        }
    }
}
