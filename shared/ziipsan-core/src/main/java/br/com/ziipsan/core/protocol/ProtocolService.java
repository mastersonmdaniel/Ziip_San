package br.com.ziipsan.core.protocol;

import br.com.ziipsan.core.api.ApiException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writer of service protocols (P1.36; ADR-0039 item 7; NR 11, art. 52,
 * §1º), for one tenant schema. Every method runs in one transaction,
 * mirroring ServiceOrderService: the number allocation, the deadline
 * computation and the protocol row commit or roll back together, so a
 * protocol is never half opened.
 *
 * <p>What this service is: the single writer of protocol state, of the
 * request-specification and close-reason catalogs, and of extension
 * events. What it is not: it performs no mutation on customer or
 * consumer-unit tables (those belong to the registry module), it sends
 * no notification itself (the extension event records the notice that
 * was given — "aviso" — and delivery is the notifier's job, P3.4 /
 * ED-35), and it creates no service order by itself: the controller
 * (or any caller) creates the order through ServiceOrderService with
 * origin EXTERNAL and this protocol's id, which the engine refuses
 * without and the database FK then binds 1:N.
 *
 * <p>Numbering: per tenant and year, from service_protocol_sequences
 * under a row lock (SELECT ... FOR UPDATE) inside the opening
 * transaction, so parallel openings serialize and never duplicate; the
 * UNIQUE (tenant_id, protocol_year, protocol_number) is the backstop.
 * The formatted number is AAAA-NNNNNN (year + zero-padded sequence).
 * It is deliberately distinct from the support ticket SUP-AAAA-NNNNNN
 * of P1.23 (COE-23): different entity, different sequence, different
 * term — always "protocolo de atendimento" here, never "chamado".
 *
 * <p>Deadline: the specification's deadline_days, counted by the
 * tenant's accepted sac.deadline_counting_rule over the holidays of
 * cal.holidays filtered by sac.holiday_scope (see
 * {@link DeadlineCalculator}). When the tenant has no accepted version
 * of a parameter, the factory default applies (CALENDAR / NATIONAL /
 * NONE). The state/municipality used for the scope filter come from
 * the opening input: the tenant catalog carries no UF/IBGE, so a
 * caller that needs state or municipal holidays supplies them (the
 * portal/backoffice knows the service locality); without them only
 * national holidays count, which is also the factory default.
 *
 * <p>Requester: a registered customer (requester_customer_id, FK to
 * customers in the full assembly) or, for a non-customer, a name and a
 * minimum contact — both required, never a document: CPF/CNPJ live
 * encrypted on customers and are never copied to a protocol.
 */
public class ProtocolService {

    private static final String ACCEPTED_PARAMETER_SQL = """
            SELECT CAST(v.value AS text) FROM %s.parameter_versions v
            WHERE v.tenant_id = ? AND v.definition_key = ?
            AND v.status = 'APPROVED'
            AND v.valid_from <= CURRENT_DATE
            AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE)
            AND EXISTS (SELECT 1 FROM %s.parameter_acceptances a
                        WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id
                        AND a.content_hash = v.content_hash)
            ORDER BY v.valid_from DESC LIMIT 1""";

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;

    public ProtocolService(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema and tenantId are required");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
    }

    // ------------------------------------------------------------------
    // Inputs and views
    // ------------------------------------------------------------------

    public record SpecInput(String code, String description, int deadlineDays,
                            String requiredDocumentsJson, boolean requiresConsumerUnit,
                            boolean requiresAddress) {
    }

    public record SpecView(UUID id, String code, String description, int deadlineDays,
                           String requiredDocumentsJson, boolean requiresConsumerUnit,
                           boolean requiresAddress, boolean active) {
    }

    public record CloseReasonInput(String code, String description) {
    }

    public record OpenInput(String specCode, ProtocolChannel channel,
                            UUID consumerUnitId, String locationText,
                            UUID requesterCustomerId, String requesterName, String requesterContact,
                            RequesterRole requesterRole, LocalDate openedDate, UUID createdBy,
                            UUID duplicateOfProtocolId, String agencyReference,
                            String stateCode, String municipalityIbge,
                            boolean includeOptionalPoints) {
    }

    public record ProtocolView(UUID id, int protocolYear, int protocolNumber,
                               ProtocolChannel channel, String specCode,
                               UUID consumerUnitId, String locationText,
                               UUID requesterCustomerId, String requesterName, String requesterContact,
                               RequesterRole requesterRole, LocalDate openedDate,
                               LocalDate originalDueDate, LocalDate currentDueDate,
                               ProtocolStatus status, String closeReasonCode,
                               UUID duplicateOfProtocolId, String agencyReference) {
        /** Display number: year + zero-padded sequence (ex.: 2026-000123). */
        public String formattedNumber() {
            return String.format("%04d-%06d", protocolYear, protocolNumber);
        }
    }

    public record ExtensionView(UUID id, UUID protocolId, LocalDate previousDueDate,
                                LocalDate newDueDate, String reason, UUID createdBy, String notice) {
    }

    public record OrderRef(UUID id, String status) {
    }

    // ------------------------------------------------------------------
    // Catalog: specifications and close reasons (tenant policy; start empty)
    // ------------------------------------------------------------------

    public SpecView declareSpec(SpecInput input) {
        return inTx(connection -> {
            if (input.code() == null || input.code().isBlank()) {
                throw ApiException.Validation.of(new ApiException.FieldError("code", "REQUIRED",
                        "O codigo da especificacao e obrigatorio."));
            }
            if (input.description() == null || input.description().isBlank()) {
                throw ApiException.Validation.of(new ApiException.FieldError("description", "REQUIRED",
                        "A descricao da especificacao e obrigatoria."));
            }
            if (input.deadlineDays() <= 0) {
                throw ApiException.Validation.of(new ApiException.FieldError("deadlineDays", "INVALID_VALUE",
                        "O prazo da especificacao deve ser maior que zero."));
            }
            if (loadSpecByCode(connection, input.code()) != null) {
                throw ApiException.Validation.of(new ApiException.FieldError("code", "DUPLICATE",
                        "Ja existe uma especificacao com este codigo."));
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_protocol_specs")
                            + " (id, tenant_id, code, description, deadline_days, required_documents,"
                            + " requires_consumer_unit, requires_address) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)")) {
                setUuid(ps, 1, id);
                setUuid(ps, 2, tenantId);
                ps.setString(3, input.code());
                ps.setString(4, input.description());
                ps.setInt(5, input.deadlineDays());
                ps.setString(6, input.requiredDocumentsJson() == null ? "[]" : input.requiredDocumentsJson());
                ps.setBoolean(7, input.requiresConsumerUnit());
                ps.setBoolean(8, input.requiresAddress());
                ps.executeUpdate();
            }
            return new SpecView(id, input.code(), input.description(), input.deadlineDays(),
                    input.requiredDocumentsJson() == null ? "[]" : input.requiredDocumentsJson(),
                    input.requiresConsumerUnit(), input.requiresAddress(), true);
        });
    }

    public void declareCloseReason(CloseReasonInput input) {
        inTx(connection -> {
            if (input.code() == null || input.code().isBlank()) {
                throw ApiException.Validation.of(new ApiException.FieldError("code", "REQUIRED",
                        "O codigo do motivo e obrigatorio."));
            }
            if (input.description() == null || input.description().isBlank()) {
                throw ApiException.Validation.of(new ApiException.FieldError("description", "REQUIRED",
                        "A descricao do motivo e obrigatoria."));
            }
            if (loadCloseReason(connection, input.code()) != null) {
                throw ApiException.Validation.of(new ApiException.FieldError("code", "DUPLICATE",
                        "Ja existe um motivo de encerramento com este codigo."));
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_protocol_close_reasons")
                            + " (id, tenant_id, code, description) VALUES (?, ?, ?, ?)")) {
                setUuid(ps, 1, UUID.randomUUID());
                setUuid(ps, 2, tenantId);
                ps.setString(3, input.code());
                ps.setString(4, input.description());
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Protocols
    // ------------------------------------------------------------------

    public ProtocolView open(OpenInput input) {
        return inTx(connection -> {
            if (input.createdBy() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("createdBy", "REQUIRED",
                        "Quem registra o protocolo e obrigatorio (created_by)."));
            }
            if (input.channel() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("channel", "REQUIRED",
                        "O canal do protocolo e obrigatorio."));
            }
            if (input.requesterRole() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("requesterRole", "REQUIRED",
                        "O papel do solicitante e obrigatorio."));
            }
            SpecView spec = input.specCode() == null ? null : loadSpecByCode(connection, input.specCode());
            if (spec == null) {
                throw new ApiException.NotFound("A especificacao " + input.specCode() + " nao existe para este tenant.");
            }
            if (!spec.active()) {
                throw ApiException.Validation.of(new ApiException.FieldError("specCode", "INACTIVE",
                        "A especificacao " + spec.code() + " esta inativa."));
            }
            // Requester: registered customer, or name + minimum contact for a non-customer.
            if (input.requesterCustomerId() == null
                    && (input.requesterName() == null || input.requesterName().isBlank()
                    || input.requesterContact() == null || input.requesterContact().isBlank())) {
                throw ApiException.Validation.of(new ApiException.FieldError("requesterName", "REQUIRED",
                        "Solicitante sem cadastro exige nome e contato minimos."));
            }
            // Target: the unit when the specification requires a matricula (or an
            // address); a point (location) when there is no unit.
            if (spec.requiresConsumerUnit() && input.consumerUnitId() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("consumerUnitId", "REQUIRED",
                        "A especificacao exige matricula: a unidade consumidora e obrigatoria."));
            }
            if (spec.requiresAddress()
                    && (input.locationText() == null || input.locationText().isBlank())) {
                throw ApiException.Validation.of(new ApiException.FieldError("locationText", "REQUIRED",
                        "A especificacao exige endereco: o local e obrigatorio."));
            }
            if (input.consumerUnitId() == null
                    && (input.locationText() == null || input.locationText().isBlank())) {
                throw ApiException.Validation.of(new ApiException.FieldError("locationText", "REQUIRED",
                        "Sem unidade consumidora, o ponto (local) e obrigatorio."));
            }
            // Duplicity is linked, never refused (sac.duplicate_handling, ED-35):
            // a duplicate without its original is the only refusal here.
            if (input.duplicateOfProtocolId() != null
                    && loadById(connection, input.duplicateOfProtocolId()) == null) {
                throw new ApiException.NotFound("O protocolo original da duplicidade nao existe para este tenant.");
            }

            LocalDate opened = input.openedDate() == null ? LocalDate.now() : input.openedDate();
            Settings settings = readSettings(connection);
            Set<LocalDate> holidays = loadHolidayDates(connection, settings.scope(),
                    input.stateCode(), input.municipalityIbge(), input.includeOptionalPoints());
            LocalDate due = DeadlineCalculator.computeDueDate(opened, spec.deadlineDays(),
                    settings.countingRule(), holidays);

            int year = opened.getYear();
            int number = allocateNumber(connection, year);
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_protocols")
                            + " (id, tenant_id, protocol_year, protocol_number, channel, spec_id,"
                            + " consumer_unit_id, location_text, requester_customer_id, requester_name,"
                            + " requester_contact, requester_role, opened_date, original_due_date,"
                            + " current_due_date, status, duplicate_of_protocol_id, agency_reference, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?)")) {
                int i = 0;
                setUuid(ps, ++i, id);
                setUuid(ps, ++i, tenantId);
                ps.setInt(++i, year);
                ps.setInt(++i, number);
                ps.setString(++i, input.channel().name());
                setUuid(ps, ++i, spec.id());
                setUuid(ps, ++i, input.consumerUnitId());
                setText(ps, ++i, blankToNull(input.locationText()));
                setUuid(ps, ++i, input.requesterCustomerId());
                setText(ps, ++i, blankToNull(input.requesterName()));
                setText(ps, ++i, blankToNull(input.requesterContact()));
                ps.setString(++i, input.requesterRole().name());
                ps.setDate(++i, Date.valueOf(opened));
                ps.setDate(++i, Date.valueOf(due));
                ps.setDate(++i, Date.valueOf(due));
                setUuid(ps, ++i, input.duplicateOfProtocolId());
                setText(ps, ++i, blankToNull(input.agencyReference()));
                setUuid(ps, ++i, input.createdBy());
                ps.executeUpdate();
            }
            return new ProtocolView(id, year, number, input.channel(), spec.code(),
                    input.consumerUnitId(), blankToNull(input.locationText()),
                    input.requesterCustomerId(), blankToNull(input.requesterName()),
                    blankToNull(input.requesterContact()), input.requesterRole(), opened, due, due,
                    ProtocolStatus.OPEN, null, input.duplicateOfProtocolId(),
                    blankToNull(input.agencyReference()));
        });
    }

    public ProtocolView get(UUID protocolId) {
        return inTx(connection -> {
            ProtocolView view = loadById(connection, protocolId);
            if (view == null) {
                throw new ApiException.NotFound("O protocolo nao existe para este tenant.");
            }
            return view;
        });
    }

    public List<ProtocolView> list(ProtocolStatus status) {
        return inTx(connection -> {
            String sql = "SELECT p.*, s.code AS spec_code FROM " + t("service_protocols") + " p"
                    + " JOIN " + t("service_protocol_specs") + " s"
                    + " ON s.tenant_id = p.tenant_id AND s.id = p.spec_id"
                    + " WHERE p.tenant_id = ?"
                    + (status == null ? "" : " AND p.status = ?")
                    + " ORDER BY opened_date DESC, protocol_year DESC, protocol_number DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                setUuid(ps, 1, tenantId);
                if (status != null) {
                    ps.setString(2, status.name());
                }
                List<ProtocolView> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readProtocol(rs));
                    }
                }
                return out;
            }
        });
    }

    /** Orders generated from this protocol (1:N): the FK target of the OS link. */
    public List<OrderRef> ordersOf(UUID protocolId) {
        return inTx(connection -> {
            if (loadById(connection, protocolId) == null) {
                throw new ApiException.NotFound("O protocolo nao existe para este tenant.");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, status FROM " + t("service_orders")
                            + " WHERE tenant_id = ? AND protocol_id = ? ORDER BY created_at")) {
                setUuid(ps, 1, tenantId);
                setUuid(ps, 2, protocolId);
                List<OrderRef> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new OrderRef(uuid(rs, "id"), rs.getString("status")));
                    }
                }
                return out;
            }
        });
    }

    public ProtocolView startProgress(UUID protocolId, UUID actorId) {
        return moveTo(protocolId, ProtocolStatus.IN_PROGRESS, actorId, null);
    }

    public ProtocolView markAnswerPending(UUID protocolId, UUID actorId) {
        return moveTo(protocolId, ProtocolStatus.ANSWER_PENDING, actorId, null);
    }

    public ProtocolView markAnswered(UUID protocolId, UUID actorId) {
        return moveTo(protocolId, ProtocolStatus.ANSWERED, actorId, null);
    }

    /**
     * Closure: requires a classified reason from the tenant's catalog
     * (the basis of the art. 54 report in P3.22). The reason is stored
     * by code; the catalog row must exist and be active.
     */
    public ProtocolView close(UUID protocolId, String closeReasonCode, UUID actorId) {
        return inTx(connection -> {
            ProtocolView current = requireExisting(connection, protocolId);
            if (!current.status().canMoveTo(ProtocolStatus.CLOSED)) {
                throw new ApiException.Conflict("invalid-transition",
                        "O protocolo em " + current.status() + " nao pode ser encerrado.");
            }
            if (closeReasonCode == null || closeReasonCode.isBlank()) {
                throw ApiException.Validation.of(new ApiException.FieldError("closeReasonCode", "REQUIRED",
                        "O encerramento exige um motivo classificado."));
            }
            CloseReasonView reason = loadCloseReason(connection, closeReasonCode);
            if (reason == null) {
                throw new ApiException.NotFound("O motivo de encerramento nao existe para este tenant.");
            }
            if (!reason.active()) {
                throw ApiException.Validation.of(new ApiException.FieldError("closeReasonCode", "INACTIVE",
                        "O motivo de encerramento esta inativo."));
            }
            updateStatus(connection, protocolId, ProtocolStatus.CLOSED, actorId, closeReasonCode);
            return reload(connection, protocolId);
        });
    }

    public ProtocolView cancel(UUID protocolId, String closeReasonCode, UUID actorId) {
        return inTx(connection -> {
            ProtocolView current = requireExisting(connection, protocolId);
            if (!current.status().canMoveTo(ProtocolStatus.CANCELLED)) {
                throw new ApiException.Conflict("invalid-transition",
                        "O protocolo em " + current.status() + " nao pode ser cancelado.");
            }
            if (closeReasonCode != null && !closeReasonCode.isBlank()
                    && loadCloseReason(connection, closeReasonCode) == null) {
                throw new ApiException.NotFound("O motivo de encerramento nao existe para este tenant.");
            }
            updateStatus(connection, protocolId, ProtocolStatus.CANCELLED, actorId,
                    blankToNull(closeReasonCode));
            return reload(connection, protocolId);
        });
    }

    /**
     * Extension (prorrogação) as an event: reason, author and notice
     * are recorded on service_protocol_extensions, current_due_date
     * moves, original_due_date never does. Policy enforcement follows
     * sac.extension_policy: NONE refuses, ONCE_JUSTIFIED allows one
     * extension per protocol, PER_ERI_RULE allows under the regulator's
     * rule (see ExtensionPolicy).
     */
    public ExtensionView extend(UUID protocolId, int additionalDays, String reason,
                                UUID authorId, String notice) {
        return inTx(connection -> {
            ProtocolView current = requireExisting(connection, protocolId);
            if (current.status().isTerminal()) {
                throw new ApiException.Conflict("invalid-transition",
                        "O protocolo encerrado ou cancelado nao pode ser prorrogado.");
            }
            if (additionalDays <= 0) {
                throw ApiException.Validation.of(new ApiException.FieldError("additionalDays", "INVALID_VALUE",
                        "A prorrogacao deve ser maior que zero dias."));
            }
            if (reason == null || reason.isBlank()) {
                throw ApiException.Validation.of(new ApiException.FieldError("reason", "REQUIRED",
                        "A prorrogacao exige um motivo."));
            }
            if (authorId == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("authorId", "REQUIRED",
                        "A prorrogacao exige um autor."));
            }
            if (notice == null || notice.isBlank()) {
                throw ApiException.Validation.of(new ApiException.FieldError("notice", "REQUIRED",
                        "A prorrogacao exige o aviso dado ao solicitante."));
            }
            Settings settings = readSettings(connection);
            if (settings.extensionPolicy() == ExtensionPolicy.NONE) {
                throw new ApiException.Forbidden("A politica de prorrogacao do tenant nao permite prorrogacao.");
            }
            if (settings.extensionPolicy() == ExtensionPolicy.ONCE_JUSTIFIED
                    && extensionCount(connection, protocolId) >= 1) {
                throw new ApiException.Forbidden("A politica de prorrogacao do tenant permite uma unica prorrogacao.");
            }
            // The locality of the original opening is not stored on the
            // protocol (the scope filter needs it only for state/municipal
            // holidays); an extension under a non-NATIONAL scope without
            // locality falls back to national holidays, as on opening.
            Set<LocalDate> holidays = loadHolidayDates(connection, settings.scope(), null, null, false);
            LocalDate newDue = DeadlineCalculator.extendDueDate(current.currentDueDate(), additionalDays,
                    settings.countingRule(), holidays);
            UUID extensionId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_protocol_extensions")
                            + " (id, tenant_id, protocol_id, previous_due_date, new_due_date,"
                            + " reason, created_by, notice) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                setUuid(ps, 1, extensionId);
                setUuid(ps, 2, tenantId);
                setUuid(ps, 3, protocolId);
                ps.setDate(4, Date.valueOf(current.currentDueDate()));
                ps.setDate(5, Date.valueOf(newDue));
                ps.setString(6, reason);
                setUuid(ps, 7, authorId);
                ps.setString(8, notice);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("service_protocols")
                            + " SET current_due_date = ?, updated_at = NOW(), updated_by = ?"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setDate(1, Date.valueOf(newDue));
                setUuid(ps, 2, authorId);
                setUuid(ps, 3, tenantId);
                setUuid(ps, 4, protocolId);
                ps.executeUpdate();
            }
            return new ExtensionView(extensionId, protocolId, current.currentDueDate(), newDue,
                    reason, authorId, notice);
        });
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private record Settings(DeadlineCountingRule countingRule, HolidayScope scope,
                            ExtensionPolicy extensionPolicy) {
    }

    private record CloseReasonView(String code, boolean active) {
    }

    private Settings readSettings(Connection connection) throws SQLException {
        Map<String, String> raw = new HashMap<>();
        for (String key : List.of(ProtocolParameters.DEADLINE_COUNTING_RULE,
                ProtocolParameters.HOLIDAY_SCOPE, ProtocolParameters.EXTENSION_POLICY)) {
            String sql = String.format(ACCEPTED_PARAMETER_SQL, schema, schema);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                setUuid(ps, 1, tenantId);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        raw.put(key, rs.getString(1));
                    }
                }
            }
        }
        return new Settings(
                ProtocolParameters.parseCountingRule(raw.get(ProtocolParameters.DEADLINE_COUNTING_RULE)),
                ProtocolParameters.parseHolidayScope(raw.get(ProtocolParameters.HOLIDAY_SCOPE)),
                ProtocolParameters.parseExtensionPolicy(raw.get(ProtocolParameters.EXTENSION_POLICY)));
    }

    private Set<LocalDate> loadHolidayDates(Connection connection, HolidayScope scope,
                                            String stateCode, String municipalityIbge,
                                            boolean includeOptionalPoints) throws SQLException {
        List<DeadlineCalculator.HolidayEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT holiday_date, scope, state_code, municipality_ibge, kind FROM " + t("holidays")
                        + " WHERE tenant_id = ?")) {
            setUuid(ps, 1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DeadlineCalculator.HolidaySphere sphere = switch (rs.getString("scope")) {
                        case "STATE" -> DeadlineCalculator.HolidaySphere.STATE;
                        case "MUNICIPAL" -> DeadlineCalculator.HolidaySphere.MUNICIPAL;
                        default -> DeadlineCalculator.HolidaySphere.NATIONAL;
                    };
                    entries.add(new DeadlineCalculator.HolidayEntry(
                            rs.getDate("holiday_date").toLocalDate(), sphere,
                            rs.getString("state_code"), rs.getString("municipality_ibge"),
                            "OPTIONAL_POINT".equals(rs.getString("kind"))));
                }
            }
        }
        return DeadlineCalculator.filterHolidayDates(entries, scope, stateCode, municipalityIbge,
                includeOptionalPoints);
    }

    private int allocateNumber(Connection connection, int year) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + t("service_protocol_sequences")
                        + " (tenant_id, protocol_year, last_number) VALUES (?, ?, 0) ON CONFLICT DO NOTHING")) {
            setUuid(ps, 1, tenantId);
            ps.setInt(2, year);
            ps.executeUpdate();
        }
        int last;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_number FROM " + t("service_protocol_sequences")
                        + " WHERE tenant_id = ? AND protocol_year = ? FOR UPDATE")) {
            setUuid(ps, 1, tenantId);
            ps.setInt(2, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Sequencia de protocolo nao encontrada apos o insert.");
                }
                last = rs.getInt(1);
            }
        }
        int next = last + 1;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE " + t("service_protocol_sequences")
                        + " SET last_number = ? WHERE tenant_id = ? AND protocol_year = ?")) {
            ps.setInt(1, next);
            setUuid(ps, 2, tenantId);
            ps.setInt(3, year);
            ps.executeUpdate();
        }
        return next;
    }

    private int extensionCount(Connection connection, UUID protocolId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + t("service_protocol_extensions")
                        + " WHERE tenant_id = ? AND protocol_id = ?")) {
            setUuid(ps, 1, tenantId);
            setUuid(ps, 2, protocolId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private ProtocolView requireExisting(Connection connection, UUID protocolId) throws SQLException {
        ProtocolView view = loadById(connection, protocolId);
        if (view == null) {
            throw new ApiException.NotFound("O protocolo nao existe para este tenant.");
        }
        return view;
    }

    private ProtocolView moveTo(UUID protocolId, ProtocolStatus target, UUID actorId,
                                String closeReasonCode) {
        return inTx(connection -> {
            ProtocolView current = requireExisting(connection, protocolId);
            if (!current.status().canMoveTo(target)) {
                throw new ApiException.Conflict("invalid-transition",
                        "O protocolo em " + current.status() + " nao pode ir para " + target + ".");
            }
            updateStatus(connection, protocolId, target, actorId, closeReasonCode);
            return reload(connection, protocolId);
        });
    }

    private void updateStatus(Connection connection, UUID protocolId, ProtocolStatus target,
                              UUID actorId, String closeReasonCode) throws SQLException {
        boolean terminal = target.isTerminal();
        String sql = "UPDATE " + t("service_protocols")
                + " SET status = ?, updated_at = NOW(), updated_by = ?"
                + (terminal ? ", closed_at = NOW()" : "")
                + (closeReasonCode != null ? ", close_reason_code = ?" : "")
                + " WHERE tenant_id = ? AND id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, target.name());
            setUuid(ps, ++i, actorId);
            if (closeReasonCode != null) {
                ps.setString(++i, closeReasonCode);
            }
            setUuid(ps, ++i, tenantId);
            setUuid(ps, ++i, protocolId);
            ps.executeUpdate();
        }
    }

    private ProtocolView reload(Connection connection, UUID protocolId) throws SQLException {
        ProtocolView view = loadById(connection, protocolId);
        if (view == null) {
            throw new IllegalStateException("Protocolo nao encontrado apos a atualizacao.");
        }
        return view;
    }

    private SpecView loadSpecByCode(Connection connection, String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + t("service_protocol_specs") + " WHERE tenant_id = ? AND code = ?")) {
            setUuid(ps, 1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new SpecView(uuid(rs, "id"), rs.getString("code"), rs.getString("description"),
                        rs.getInt("deadline_days"), rs.getString("required_documents"),
                        rs.getBoolean("requires_consumer_unit"), rs.getBoolean("requires_address"),
                        rs.getBoolean("active"));
            }
        }
    }

    private CloseReasonView loadCloseReason(Connection connection, String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT code, active FROM " + t("service_protocol_close_reasons")
                        + " WHERE tenant_id = ? AND code = ?")) {
            setUuid(ps, 1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new CloseReasonView(rs.getString("code"), rs.getBoolean("active"));
            }
        }
    }

    private ProtocolView loadById(Connection connection, UUID protocolId) throws SQLException {
        if (protocolId == null) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT p.*, s.code AS spec_code FROM " + t("service_protocols") + " p"
                        + " JOIN " + t("service_protocol_specs") + " s"
                        + " ON s.tenant_id = p.tenant_id AND s.id = p.spec_id"
                        + " WHERE p.tenant_id = ? AND p.id = ?")) {
            setUuid(ps, 1, tenantId);
            setUuid(ps, 2, protocolId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return readProtocol(rs);
            }
        }
    }

    private ProtocolView readProtocol(ResultSet rs) throws SQLException {
        return new ProtocolView(uuid(rs, "id"), rs.getInt("protocol_year"), rs.getInt("protocol_number"),
                ProtocolChannel.valueOf(rs.getString("channel")), rs.getString("spec_code"),
                uuid(rs, "consumer_unit_id"), rs.getString("location_text"),
                uuid(rs, "requester_customer_id"), rs.getString("requester_name"),
                rs.getString("requester_contact"), RequesterRole.valueOf(rs.getString("requester_role")),
                rs.getDate("opened_date").toLocalDate(), rs.getDate("original_due_date").toLocalDate(),
                rs.getDate("current_due_date").toLocalDate(), ProtocolStatus.valueOf(rs.getString("status")),
                rs.getString("close_reason_code"), uuid(rs, "duplicate_of_protocol_id"),
                rs.getString("agency_reference"));
    }

    private String t(String table) {
        return schema + "." + table;
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return (UUID) rs.getObject(column);
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setText(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Service protocol operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Service protocol operation failed: " + e.getMessage(), e);
        }
    }
}
