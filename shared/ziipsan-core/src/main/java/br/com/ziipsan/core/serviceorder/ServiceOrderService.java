package br.com.ziipsan.core.serviceorder;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Work-order engine (P1.31; ADR-0039, ADR-0045).
 *
 * <p>What this engine is: the single writer of work-order state, effect
 * markers and order history, for one tenant schema. Every method runs in
 * one transaction: a handler failure rolls the effect marker back with
 * the handler's own writes, so an effect is never half applied.
 *
 * <p>What it is not: it performs no mutation on customer or consumer-unit
 * tables itself. Those mutations belong to the owning modules' effect
 * handlers (registered through {@link EffectHandlerRegistry}); until a
 * handler exists for an effect code, execution of an order carrying that
 * code is refused instead of silently marking it applied. The
 * architecture test of the plan ("no write outside the applier") is
 * enforced on this side by the history FKs (a history row without an
 * order is refused by the database) and by the single-writer rule; the
 * migration of the remaining direct writes in the registry module is
 * P1.3's V3_6, and is recorded as a pendency, not silently claimed here.
 *
 * <p>Scope notes (each is a deliberate boundary, not an omission):
 * <ul>
 *   <li>Price, charge validity and billing posting are ED-34: the type
 *       only carries {@code price_item_ref}.</li>
 *   <li>Protocol rows and the protocol FK are P1.36: the engine enforces
 *       the origin/protocol rule and stores the reserved id.</li>
 *   <li>Field scheduling screens are ED-36, offline execution ED-20, the
 *       confined-space registry P1.35, the competency registry P3.31 /
 *       ED-59 (see {@link CompetencyRegistry}).</li>
 *   <li>The unit link of an order is exclusively its targets: there is
 *       no {@code consumer_unit_id} column on the order, so a batch order
 *       references each unit it changes (ADR-0039 item 9).</li>
 *   <li>The safety close gate does not apply to
 *       {@code CLOSED_NOT_EXECUTED}: cancelling is the safe action, and
 *       blocking a cancellation for missing safety artifacts would trap
 *       the order. The schedule gate and the executed-close gate apply
 *       in full.</li>
 * </ul>
 *
 * <p>Normative anchors for the safety gates live in V9_1's header and in
 * {@link ServiceOrderParameters}; the gate itself is
 * {@link #checkSafetyGate}.
 */
public class ServiceOrderService {

    private static final Pattern CPF_LIKE = Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    private static final Pattern CNPJ_LIKE = Pattern.compile("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
    private static final Pattern EMAIL_LIKE = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

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
    private final TargetVerifierRegistry verifiers = new TargetVerifierRegistry();
    private final EffectHandlerRegistry effects = new EffectHandlerRegistry();
    private CompetencyRegistry competencyRegistry;

    public ServiceOrderService(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema and tenantId are required");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
        // The engine's own root-entity verifier: the consumer-unit table is
        // the ADR-0039 root and the table the engine's history FKs already
        // reference. Every other class is registered by its owning module.
        verifiers.register(TargetType.CONSUMER_UNIT,
                TargetVerifierRegistry.sqlVerifier(schema, "consumer_units"));
    }

    public TargetVerifierRegistry verifiers() {
        return verifiers;
    }

    public EffectHandlerRegistry effects() {
        return effects;
    }

    /** Binds the competency registry when it exists (P3.31 / ED-59); null unbinds it. */
    public void setCompetencyRegistry(CompetencyRegistry registry) {
        this.competencyRegistry = registry;
    }

    // ------------------------------------------------------------------
    // Inputs and views
    // ------------------------------------------------------------------

    public record ServiceTypeInput(
            String code, String name, String operation, String effectCode, String effectSchemaJson,
            ServiceOrderEnums.TypeIndicator indicator, boolean requiresApproval, boolean operational,
            ServiceOrderEnums.OperationalOrigin origin, List<TargetType> targetClasses,
            String priceItemRef, Integer deadlineHours, Integer minCrew,
            boolean confinedSpace, boolean heightWork, boolean trenchWork, boolean electricalWork,
            List<String> requiredNormCodes) {
    }

    public record ServiceTypeView(
            UUID id, String code, String name, String operation, String effectCode, String effectSchemaJson,
            ServiceOrderEnums.TypeIndicator indicator, boolean requiresApproval, boolean operational,
            ServiceOrderEnums.OperationalOrigin origin, List<TargetType> targetClasses,
            String priceItemRef, Integer deadlineHours, Integer minCrew,
            boolean confinedSpace, boolean heightWork, boolean trenchWork, boolean electricalWork,
            boolean requiresRiskAnalysis, boolean requiresEntryPermit, boolean requiresWorkPermit,
            List<String> requiredNormCodes, boolean active) {
    }

    public record OrderInput(
            String serviceTypeCode, ServiceOrderEnums.Origin origin, ServiceOrderEnums.ExecutionMode executionMode,
            ServiceOrderEnums.Priority priority, UUID createdBy, UUID protocolId,
            UUID batchId, UUID parentOrderId, UUID reversesOrderId) {
    }

    public record OrderView(
            UUID id, String serviceTypeCode, ServiceOrderStatus status, ServiceOrderEnums.ExecutionMode executionMode,
            ServiceOrderEnums.Origin origin, ServiceOrderEnums.Priority priority, UUID createdBy, UUID approvedBy,
            OffsetDateTime dueAt, OffsetDateTime executedAt, OffsetDateTime closedAt,
            UUID batchId, UUID protocolId, UUID reversesOrderId, UUID parentOrderId) {
    }

    public record TargetInput(
            TargetType targetType, UUID targetId, String locationText, String coordinatesWkt,
            String effectCode, String effectPayloadJson) {
    }

    public record SafetyInput(
            String riskAnalysisRef, String entryPermitRef, String workPermitRef,
            java.math.BigDecimal trenchDepthM, UUID qualifiedReleaseBy) {
    }

    public record CrewInput(
            UUID userId, String role, String qualificationCode, LocalDate qualificationValidUntil) {
    }

    public record PermitInput(
            String permitCode, OffsetDateTime entryAt, UUID enteredBy, UUID supervisorId,
            OffsetDateTime retainedUntil) {
    }

    public record HistoryRow(
            OffsetDateTime occurredAt, UUID actorId, String source, String kind,
            String beforeJson, String afterJson, UUID serviceOrderId) {
    }

    public record ModeCount(
            ServiceOrderEnums.ExecutionMode executionMode, ServiceOrderStatus status, long total) {
    }

    // ------------------------------------------------------------------
    // Transaction plumbing
    // ------------------------------------------------------------------

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
                throw new IllegalStateException("Service order operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Service order operation failed: " + e.getMessage(), e);
        }
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

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(field, "REQUIRED",
                    "O campo " + field + " e obrigatorio."));
        }
        return value;
    }

    private static OffsetDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------------
    // Tenant parameters (accepted + vigente, P0.19 pattern)
    // ------------------------------------------------------------------

    private record Parameters(
            boolean operationalTypesEnabled,
            Map<String, Integer> deadlineOverrides,
            Map<String, Set<ServiceOrderEnums.ControlCode>> requiredControls,
            int minCrewConfinedSpace,
            int trainingValidityMonths) {
    }

    private Parameters readParameters(Connection connection) throws SQLException {
        String sql = String.format(ACCEPTED_PARAMETER_SQL, schema, schema);
        Map<String, String> raw = new HashMap<>();
        for (String key : List.of(ServiceOrderParameters.OPERATIONAL_TYPES_ENABLED,
                ServiceOrderParameters.DEADLINE_HOURS_BY_TYPE,
                ServiceOrderParameters.REQUIRED_CONTROLS_BY_SERVICE_TYPE,
                ServiceOrderParameters.MIN_CREW_CONFINED_SPACE,
                ServiceOrderParameters.TRAINING_VALIDITY_MONTHS)) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        raw.put(key, rs.getString(1));
                    }
                }
            }
        }
        boolean operationalEnabled = ServiceOrderParameters.OPERATIONAL_TYPES_ENABLED_DEFAULT;
        String enabledJson = raw.get(ServiceOrderParameters.OPERATIONAL_TYPES_ENABLED);
        if (enabledJson != null) {
            JsonNode node = Json.readTree(enabledJson);
            if (!node.isBoolean()) {
                throw new IllegalStateException("Parametro aceito '" + ServiceOrderParameters.OPERATIONAL_TYPES_ENABLED
                        + "' com valor nao booleano: " + enabledJson);
            }
            operationalEnabled = node.booleanValue();
        }
        int minCrew = ServiceOrderParameters.MIN_CREW_CONFINED_SPACE_DEFAULT;
        String minCrewJson = raw.get(ServiceOrderParameters.MIN_CREW_CONFINED_SPACE);
        if (minCrewJson != null) {
            JsonNode node = Json.readTree(minCrewJson);
            if (!node.isIntegralNumber()) {
                throw new IllegalStateException("Parametro aceito '" + ServiceOrderParameters.MIN_CREW_CONFINED_SPACE
                        + "' com valor nao inteiro: " + minCrewJson);
            }
            ServiceOrderParameters.validateMinCrewConfinedSpace(node.intValue());
            minCrew = node.intValue();
        }
        int trainingMonths = ServiceOrderParameters.TRAINING_VALIDITY_MONTHS_DEFAULT;
        String trainingJson = raw.get(ServiceOrderParameters.TRAINING_VALIDITY_MONTHS);
        if (trainingJson != null) {
            JsonNode node = Json.readTree(trainingJson);
            if (!node.isIntegralNumber()) {
                throw new IllegalStateException("Parametro aceito '" + ServiceOrderParameters.TRAINING_VALIDITY_MONTHS
                        + "' com valor nao inteiro: " + trainingJson);
            }
            ServiceOrderParameters.validateTrainingValidityMonths(node.intValue());
            trainingMonths = node.intValue();
        }
        return new Parameters(operationalEnabled,
                ServiceOrderParameters.parseDeadlineOverrides(raw.get(ServiceOrderParameters.DEADLINE_HOURS_BY_TYPE)),
                ServiceOrderParameters.parseRequiredControls(raw.get(ServiceOrderParameters.REQUIRED_CONTROLS_BY_SERVICE_TYPE)),
                minCrew, trainingMonths);
    }

    /** Factory controls implied by a type's hazard flags. */
    static Set<ServiceOrderEnums.ControlCode> factoryControls(ServiceTypeView type) {
        var controls = new HashSet<ServiceOrderEnums.ControlCode>();
        if (type.confinedSpace() || type.heightWork() || type.trenchWork()) {
            controls.add(ServiceOrderEnums.ControlCode.RISK_ANALYSIS);
        }
        if (type.confinedSpace()) {
            controls.add(ServiceOrderEnums.ControlCode.ENTRY_PERMIT);
            controls.add(ServiceOrderEnums.ControlCode.MIN_CREW);
            controls.add(ServiceOrderEnums.ControlCode.TRAINING_VALID);
        }
        if (type.heightWork() || type.electricalWork()) {
            controls.add(ServiceOrderEnums.ControlCode.WORK_PERMIT);
        }
        if (type.heightWork() || type.confinedSpace() || type.trenchWork() || type.electricalWork()) {
            controls.add(ServiceOrderEnums.ControlCode.TRAINING_VALID);
        }
        if (type.trenchWork()) {
            controls.add(ServiceOrderEnums.ControlCode.QUALIFIED_RELEASE);
        }
        return controls;
    }

    /** Norm codes a flagged type must cite (NR-1 umbrella + the specific norm). */
    static Set<String> requiredNorms(ServiceTypeView type) {
        var norms = new HashSet<String>();
        if (type.confinedSpace() || type.heightWork() || type.trenchWork() || type.electricalWork()) {
            norms.add("NR-1");
        }
        if (type.confinedSpace()) {
            norms.add("NR-33");
        }
        if (type.heightWork()) {
            norms.add("NR-35");
        }
        if (type.trenchWork()) {
            norms.add("NR-18");
        }
        if (type.electricalWork()) {
            // Wording in force from 01/06/2027 (Portaria MTE 737/2026); the
            // code is the same, the effective wording is recorded by the
            // caller in the type's documentation, not invented here.
            norms.add("NR-10");
        }
        return norms;
    }

    // ------------------------------------------------------------------
    // Service types
    // ------------------------------------------------------------------

    public UUID declareType(ServiceTypeInput input, UUID actorId) {
        return inTx(connection -> {
            nonBlank(input.code(), "code");
            nonBlank(input.name(), "name");
            nonBlank(input.operation(), "operation");
            if (input.targetClasses() == null || input.targetClasses().isEmpty()) {
                throw ApiException.Validation.of(new ApiException.FieldError("targetClasses", "REQUIRED",
                        "O tipo precisa aceitar ao menos uma classe de alvo."));
            }
            for (TargetType targetClass : input.targetClasses()) {
                if (!verifiers.hasVerifier(targetClass)) {
                    throw ApiException.Validation.of(new ApiException.FieldError("targetClasses", "NO_VERIFIER",
                            "A classe " + targetClass + " nao tem verificador de existencia registrado: "
                                    + "o modulo que cria a entidade precisa registra-lo antes de o tipo declara-la."));
                }
            }
            if (input.operational()) {
                if (input.effectCode() != null) {
                    throw ApiException.Validation.of(new ApiException.FieldError("effectCode", "OPERATIONAL_NO_EFFECT",
                            "Tipo operacional nao tem efeito comercial: effectCode precisa ser nulo."));
                }
                if (input.origin() == null) {
                    throw ApiException.Validation.of(new ApiException.FieldError("origin", "REQUIRED",
                            "Tipo operacional precisa de origem (ocorrencia, plano preventivo, inspecao ou reclamacao)."));
                }
            } else if (input.effectCode() == null || input.effectCode().isBlank()) {
                throw ApiException.Validation.of(new ApiException.FieldError("effectCode", "REQUIRED",
                        "Tipo nao operacional precisa de um codigo de efeito."));
            }
            if (input.confinedSpace() && input.minCrew() != null && input.minCrew() < 2) {
                throw ApiException.Validation.of(new ApiException.FieldError("minCrew", "BELOW_NORM_FLOOR",
                        "Tipo em espaco confinado exige equipe minima de 2 ou mais (NR-33, 33.3.4.4)."));
            }
            boolean requiresRisk = input.confinedSpace() || input.heightWork() || input.trenchWork();
            boolean requiresEntry = input.confinedSpace();
            boolean requiresWork = input.heightWork() || input.electricalWork();
            var norms = new HashSet<>(input.requiredNormCodes() == null ? List.of() : input.requiredNormCodes());
            if (input.confinedSpace() || input.heightWork() || input.trenchWork() || input.electricalWork()) {
                norms.add("NR-1");
                if (input.confinedSpace()) {
                    norms.add("NR-33");
                }
                if (input.heightWork()) {
                    norms.add("NR-35");
                }
                if (input.trenchWork()) {
                    norms.add("NR-18");
                }
                if (input.electricalWork()) {
                    norms.add("NR-10");
                }
            }
            UUID id = UUID.randomUUID();
            String[] classes = input.targetClasses().stream().map(Enum::name).toArray(String[]::new);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_types") + " (id, tenant_id, code, name, operation, effect_code,"
                            + " effect_schema, indicator, requires_approval, operational, origin, target_classes,"
                            + " price_item_ref, deadline_hours, min_crew, confined_space, height_work, trench_work,"
                            + " electrical_work, requires_risk_analysis, requires_entry_permit, requires_work_permit,"
                            + " required_norm_codes, active, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                ps.setObject(++i, id);
                ps.setObject(++i, tenantId);
                ps.setString(++i, input.code());
                ps.setString(++i, input.name());
                ps.setString(++i, input.operation());
                setText(ps, ++i, input.effectCode());
                setText(ps, ++i, input.effectSchemaJson());
                ps.setString(++i, input.indicator().name());
                ps.setBoolean(++i, input.requiresApproval());
                ps.setBoolean(++i, input.operational());
                setText(ps, ++i, input.origin() == null ? null : input.origin().name());
                ps.setArray(++i, connection.createArrayOf("text", classes));
                setText(ps, ++i, input.priceItemRef());
                if (input.deadlineHours() == null) {
                    ps.setNull(++i, Types.INTEGER);
                } else {
                    ps.setInt(++i, input.deadlineHours());
                }
                if (input.minCrew() == null) {
                    ps.setNull(++i, Types.INTEGER);
                } else {
                    ps.setInt(++i, input.minCrew());
                }
                ps.setBoolean(++i, input.confinedSpace());
                ps.setBoolean(++i, input.heightWork());
                ps.setBoolean(++i, input.trenchWork());
                ps.setBoolean(++i, input.electricalWork());
                ps.setBoolean(++i, requiresRisk);
                ps.setBoolean(++i, requiresEntry);
                ps.setBoolean(++i, requiresWork);
                ps.setArray(++i, connection.createArrayOf("text", norms.toArray(String[]::new)));
                ps.setBoolean(++i, true);
                setUuid(ps, ++i, actorId);
                setUuid(ps, ++i, actorId);
                ps.executeUpdate();
            }
            return id;
        });
    }

    public ServiceTypeView findTypeByCode(String code) {
        return inTx(connection -> loadTypeByCode(connection, code));
    }

    private ServiceTypeView loadTypeByCode(Connection connection, String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + t("service_types") + " WHERE tenant_id = ? AND code = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("Tipo de servico nao encontrado: " + code + ".");
                }
                return readType(rs);
            }
        }
    }

    private ServiceTypeView readType(ResultSet rs) throws SQLException {
        var classes = new ArrayList<TargetType>();
        var array = rs.getArray("target_classes");
        if (array != null) {
            for (String raw : (String[]) array.getArray()) {
                classes.add(TargetType.valueOf(raw));
            }
        }
        var norms = new ArrayList<String>();
        var normsArray = rs.getArray("required_norm_codes");
        if (normsArray != null) {
            for (String raw : (String[]) normsArray.getArray()) {
                norms.add(raw);
            }
        }
        String origin = rs.getString("origin");
        return new ServiceTypeView(
                uuid(rs, "id"), rs.getString("code"), rs.getString("name"), rs.getString("operation"),
                rs.getString("effect_code"), rs.getString("effect_schema"),
                ServiceOrderEnums.TypeIndicator.valueOf(rs.getString("indicator")),
                rs.getBoolean("requires_approval"), rs.getBoolean("operational"),
                origin == null ? null : ServiceOrderEnums.OperationalOrigin.valueOf(origin),
                classes, rs.getString("price_item_ref"),
                (Integer) rs.getObject("deadline_hours"), (Integer) rs.getObject("min_crew"),
                rs.getBoolean("confined_space"), rs.getBoolean("height_work"), rs.getBoolean("trench_work"),
                rs.getBoolean("electrical_work"), rs.getBoolean("requires_risk_analysis"),
                rs.getBoolean("requires_entry_permit"), rs.getBoolean("requires_work_permit"),
                norms, rs.getBoolean("active"));
    }

    // ------------------------------------------------------------------
    // Batches
    // ------------------------------------------------------------------

    public UUID createBatch(String code, String description, UUID actorId) {
        return inTx(connection -> {
            nonBlank(code, "code");
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_order_batches")
                            + " (id, tenant_id, code, description, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setString(3, code);
                setText(ps, 4, description);
                setUuid(ps, 5, actorId);
                setUuid(ps, 6, actorId);
                ps.executeUpdate();
            }
            return id;
        });
    }

    public UUID createCloseReason(String code, String description, UUID actorId) {
        return inTx(connection -> {
            nonBlank(code, "code");
            nonBlank(description, "description");
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_order_close_reasons")
                            + " (id, tenant_id, code, description, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setString(3, code);
                ps.setString(4, description);
                setUuid(ps, 5, actorId);
                setUuid(ps, 6, actorId);
                ps.executeUpdate();
            }
            return id;
        });
    }

    // ------------------------------------------------------------------
    // Orders
    // ------------------------------------------------------------------

    public OrderView createOrder(OrderInput input) {
        return inTx(connection -> {
            if (input.createdBy() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("createdBy", "REQUIRED",
                        "Quem registra a OS e obrigatorio (created_by)."));
            }
            ServiceTypeView type = loadTypeByCode(connection, input.serviceTypeCode());
            if (!type.active()) {
                throw new ApiException.Conflict("inactive-type",
                        "O tipo " + type.code() + " esta inativo.");
            }
            if (input.origin() == ServiceOrderEnums.Origin.EXTERNAL && input.protocolId() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("protocolId", "REQUIRED",
                        "Solicitacao externa nasce como protocolo: protocol_id e obrigatorio."));
            }
            if (input.origin() == ServiceOrderEnums.Origin.INTERNAL && input.protocolId() != null) {
                throw ApiException.Validation.of(new ApiException.FieldError("protocolId", "FORBIDDEN",
                        "Alteracao interna do backoffice gera OS sem protocolo."));
            }
            if (type.indicator() == ServiceOrderEnums.TypeIndicator.INTERNAL
                    && input.executionMode() != ServiceOrderEnums.ExecutionMode.INTERNAL) {
                throw ApiException.Validation.of(new ApiException.FieldError("executionMode", "TYPE_MISMATCH",
                        "Tipo interno so gera OS interna."));
            }
            if (type.indicator() == ServiceOrderEnums.TypeIndicator.FIELD
                    && input.executionMode() == ServiceOrderEnums.ExecutionMode.INTERNAL) {
                throw ApiException.Validation.of(new ApiException.FieldError("executionMode", "TYPE_MISMATCH",
                        "Tipo de campo nao gera OS interna."));
            }
            Parameters parameters = readParameters(connection);
            if (type.operational() && !parameters.operationalTypesEnabled()) {
                throw new ApiException.Conflict("operational-types-disabled",
                        "Tipos operacionais estao desligados pelo parametro so.operational_types_enabled.");
            }
            OffsetDateTime dueAt = null;
            if (type.deadlineHours() != null) {
                Integer override = parameters.deadlineOverrides().get(type.code());
                if (override != null) {
                    ServiceOrderParameters.validateDeadlineOverride(type.code(), type.deadlineHours(), override);
                }
                int hours = ServiceOrderParameters.effectiveDeadlineHours(type.deadlineHours(), override);
                dueAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(hours);
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_orders") + " (id, tenant_id, service_type_id, batch_id,"
                            + " parent_order_id, reverses_order_id, protocol_id, origin, execution_mode, priority,"
                            + " status, created_by, updated_by, due_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)")) {
                int i = 0;
                ps.setObject(++i, id);
                ps.setObject(++i, tenantId);
                ps.setObject(++i, type.id());
                setUuid(ps, ++i, input.batchId());
                setUuid(ps, ++i, input.parentOrderId());
                setUuid(ps, ++i, input.reversesOrderId());
                setUuid(ps, ++i, input.protocolId());
                ps.setString(++i, input.origin().name());
                ps.setString(++i, input.executionMode().name());
                ps.setString(++i, input.priority() == null
                        ? ServiceOrderEnums.Priority.NORMAL.name() : input.priority().name());
                setUuid(ps, ++i, input.createdBy());
                setUuid(ps, ++i, input.createdBy());
                if (dueAt == null) {
                    ps.setNull(++i, Types.TIMESTAMP_WITH_TIMEZONE);
                } else {
                    ps.setTimestamp(++i, Timestamp.from(dueAt.toInstant()));
                }
                ps.executeUpdate();
            }
            insertHistory(connection, id, "CREATED", null, "DRAFT", input.createdBy(), null, null);
            return loadOrder(connection, id);
        });
    }

    public OrderView getOrder(UUID orderId) {
        return inTx(connection -> loadOrder(connection, orderId));
    }

    private OrderView loadOrder(Connection connection, UUID orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT o.*, t.code AS type_code FROM " + t("service_orders") + " o"
                        + " JOIN " + t("service_types") + " t ON t.tenant_id = o.tenant_id AND t.id = o.service_type_id"
                        + " WHERE o.tenant_id = ? AND o.id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("Ordem de servico nao encontrada.");
                }
                return new OrderView(uuid(rs, "id"), rs.getString("type_code"),
                        ServiceOrderStatus.valueOf(rs.getString("status")),
                        ServiceOrderEnums.ExecutionMode.valueOf(rs.getString("execution_mode")),
                        ServiceOrderEnums.Origin.valueOf(rs.getString("origin")),
                        ServiceOrderEnums.Priority.valueOf(rs.getString("priority")),
                        uuid(rs, "created_by"), uuid(rs, "approved_by"),
                        timestamp(rs, "due_at"), timestamp(rs, "executed_at"), timestamp(rs, "closed_at"),
                        uuid(rs, "batch_id"), uuid(rs, "protocol_id"),
                        uuid(rs, "reverses_order_id"), uuid(rs, "parent_order_id"));
            }
        }
    }

    private ServiceTypeView loadTypeOf(Connection connection, UUID orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT t.* FROM " + t("service_types") + " t"
                        + " JOIN " + t("service_orders") + " o ON o.tenant_id = t.tenant_id AND o.service_type_id = t.id"
                        + " WHERE o.tenant_id = ? AND o.id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("Tipo da ordem nao encontrado.");
                }
                return readType(rs);
            }
        }
    }

    private void insertHistory(Connection connection, UUID orderId, String eventType,
            String fromStatus, String toStatus, UUID actorId, String reason, String payloadJson) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + t("service_order_history")
                        + " (id, tenant_id, order_id, event_type, from_status, to_status, actor_id, reason, payload, created_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, orderId);
            ps.setString(4, eventType);
            setText(ps, 5, fromStatus);
            setText(ps, 6, toStatus);
            setUuid(ps, 7, actorId);
            setText(ps, 8, reason);
            setText(ps, 9, payloadJson);
            setUuid(ps, 10, actorId);
            ps.executeUpdate();
        }
    }

    private OrderView move(Connection connection, UUID orderId, ServiceOrderStatus to,
            UUID actorId, String reason) throws SQLException {
        OrderView current = loadOrder(connection, orderId);
        if (!current.status().canMoveTo(to)) {
            throw new ApiException.Conflict("invalid-transition",
                    "Transicao invalida: " + current.status() + " -> " + to + ".");
        }
        if (current.status() == to) {
            return current;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE " + t("service_orders") + " SET status = ?, updated_by = ?"
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setString(1, to.name());
            setUuid(ps, 2, actorId);
            ps.setObject(3, tenantId);
            ps.setObject(4, orderId);
            ps.executeUpdate();
        }
        insertHistory(connection, orderId, "TRANSITION", current.status().name(), to.name(), actorId, reason, null);
        return loadOrder(connection, orderId);
    }

    // ------------------------------------------------------------------
    // Targets
    // ------------------------------------------------------------------

    public UUID addTarget(UUID orderId, TargetInput input, UUID actorId) {
        return inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status() != ServiceOrderStatus.DRAFT && order.status() != ServiceOrderStatus.OPEN) {
                throw new ApiException.Conflict("targets-frozen",
                        "Alvos so podem ser incluidos em DRAFT ou OPEN (a ordem esta " + order.status() + ").");
            }
            ServiceTypeView type = loadTypeOf(connection, orderId);
            if (!type.targetClasses().contains(input.targetType())) {
                throw ApiException.Validation.of(new ApiException.FieldError("targetType", "NOT_ACCEPTED",
                        "O tipo " + type.code() + " nao aceita alvos " + input.targetType() + "."));
            }
            if (input.targetType().isLocationOnly()) {
                if (input.targetId() != null) {
                    throw ApiException.Validation.of(new ApiException.FieldError("targetId", "LOCATION_ONLY",
                            "Alvo LOCATION_ONLY carrega local, nao entidade."));
                }
            } else {
                if (input.targetId() == null) {
                    throw ApiException.Validation.of(new ApiException.FieldError("targetId", "REQUIRED",
                            "Alvo " + input.targetType() + " precisa da entidade."));
                }
                TargetVerifierRegistry.TargetExistenceVerifier verifier = verifiers.verifier(input.targetType());
                if (verifier == null) {
                    throw new ApiException.Conflict("no-verifier",
                            "A classe " + input.targetType() + " nao tem verificador registrado.");
                }
                if (!verifier.exists(connection, tenantId, input.targetId())) {
                    throw new ApiException.NotFound("Alvo " + input.targetType() + " nao encontrado.");
                }
            }
            String effectCode = input.effectCode() == null ? type.effectCode() : input.effectCode();
            EffectPayloadValidator.validate(type.effectSchemaJson(), input.effectPayloadJson(), "effectPayload");
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_order_targets") + " (id, tenant_id, order_id, target_type,"
                            + " target_id, location_text, coordinates, effect_code, effect_payload, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, "
                            + (input.coordinatesWkt() == null ? "NULL" : "ST_GeomFromText(?, 4674)")
                            + ", ?, ?::jsonb, ?, ?)")) {
                int i = 0;
                ps.setObject(++i, id);
                ps.setObject(++i, tenantId);
                ps.setObject(++i, orderId);
                ps.setString(++i, input.targetType().name());
                setUuid(ps, ++i, input.targetId());
                setText(ps, ++i, input.locationText());
                if (input.coordinatesWkt() != null) {
                    ps.setString(++i, input.coordinatesWkt());
                }
                setText(ps, ++i, effectCode);
                setText(ps, ++i, input.effectPayloadJson());
                setUuid(ps, ++i, actorId);
                setUuid(ps, ++i, actorId);
                ps.executeUpdate();
            }
            return id;
        });
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public OrderView submit(UUID orderId, UUID actorId) {
        return inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status() != ServiceOrderStatus.DRAFT) {
                throw new ApiException.Conflict("invalid-transition",
                        "So DRAFT pode ser enviado (a ordem esta " + order.status() + ").");
            }
            ServiceTypeView type = loadTypeOf(connection, orderId);
            ServiceOrderStatus next = type.requiresApproval()
                    ? ServiceOrderStatus.PENDING_APPROVAL : ServiceOrderStatus.OPEN;
            return move(connection, orderId, next, actorId, null);
        });
    }

    public OrderView approve(UUID orderId, UUID approverId) {
        return inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status() != ServiceOrderStatus.PENDING_APPROVAL) {
                throw new ApiException.Conflict("not-pending-approval",
                        "So PENDING_APPROVAL pode ser aprovada (a ordem esta " + order.status() + ").");
            }
            if (approverId == null || approverId.equals(order.createdBy())) {
                throw ApiException.Validation.of(new ApiException.FieldError("approvedBy", "SAME_AS_CREATOR",
                        "Quem aprova precisa ser diferente de quem registrou a OS."));
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("service_orders")
                            + " SET approved_by = ?, approved_at = NOW(), updated_by = ?"
                            + " WHERE tenant_id = ? AND id = ?")) {
                setUuid(ps, 1, approverId);
                setUuid(ps, 2, approverId);
                ps.setObject(3, tenantId);
                ps.setObject(4, orderId);
                ps.executeUpdate();
            }
            insertHistory(connection, orderId, "APPROVAL",
                    ServiceOrderStatus.PENDING_APPROVAL.name(), ServiceOrderStatus.OPEN.name(), approverId, null, null);
            return move(connection, orderId, ServiceOrderStatus.OPEN, approverId, null);
        });
    }

    public OrderView schedule(UUID orderId, OffsetDateTime scheduledFor, UUID actorId) {
        return inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status() != ServiceOrderStatus.OPEN
                    && order.status() != ServiceOrderStatus.WAITING_DEPENDENCY) {
                throw new ApiException.Conflict("invalid-transition",
                        "So OPEN ou WAITING_DEPENDENCY pode ser programada (a ordem esta " + order.status() + ").");
            }
            checkSafetyGate(connection, orderId, "programar", false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("service_orders") + " SET scheduled_for = ?, updated_by = ?"
                            + " WHERE tenant_id = ? AND id = ?")) {
                if (scheduledFor == null) {
                    ps.setNull(1, Types.TIMESTAMP_WITH_TIMEZONE);
                } else {
                    ps.setTimestamp(1, Timestamp.from(scheduledFor.toInstant()));
                }
                setUuid(ps, 2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, orderId);
                ps.executeUpdate();
            }
            return move(connection, orderId, ServiceOrderStatus.SCHEDULED, actorId, null);
        });
    }

    public OrderView startExecution(UUID orderId, UUID actorId) {
        return inTx(connection -> move(connection, orderId, ServiceOrderStatus.IN_EXECUTION, actorId, null));
    }

    public OrderView markWaiting(UUID orderId, String reason, UUID actorId) {
        return inTx(connection -> move(connection, orderId, ServiceOrderStatus.WAITING_DEPENDENCY, actorId, reason));
    }

    public OrderView resume(UUID orderId, ServiceOrderStatus target, UUID actorId) {
        return inTx(connection -> {
            if (target != ServiceOrderStatus.OPEN && target != ServiceOrderStatus.SCHEDULED
                    && target != ServiceOrderStatus.IN_EXECUTION) {
                throw ApiException.Validation.of(new ApiException.FieldError("target", "INVALID",
                        "Retomar so volta para OPEN, SCHEDULED ou IN_EXECUTION."));
            }
            return move(connection, orderId, target, actorId, null);
        });
    }

    /**
     * Applies the effects of all targets, transactionally and idempotently.
     * Re-executing an EXECUTED order is a no-op returning the current
     * state; a target whose effect is already marked is skipped. An order
     * whose type requires approval cannot execute before approval, and an
     * order with an effect code and no registered handler is refused
     * (never silently marked applied).
     */
    public OrderView execute(UUID orderId, UUID actorId) {
        return inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status() == ServiceOrderStatus.EXECUTED) {
                return order;
            }
            ServiceTypeView type = loadTypeOf(connection, orderId);
            boolean internal = type.indicator() == ServiceOrderEnums.TypeIndicator.INTERNAL;
            if (internal && order.status() != ServiceOrderStatus.OPEN
                    && order.status() != ServiceOrderStatus.SCHEDULED) {
                throw new ApiException.Conflict("invalid-transition",
                        "OS interna executa a partir de OPEN (a ordem esta " + order.status() + ").");
            }
            if (!internal && order.status() != ServiceOrderStatus.IN_EXECUTION) {
                throw new ApiException.Conflict("invalid-transition",
                        "OS de campo executa a partir de IN_EXECUTION (a ordem esta " + order.status() + ").");
            }
            if (type.requiresApproval() && order.approvedBy() == null) {
                throw new ApiException.Conflict("approval-required",
                        "O tipo " + type.code() + " exige aprovacao por outra pessoa antes de executar.");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("service_order_targets")
                            + " WHERE tenant_id = ? AND order_id = ? ORDER BY created_at, id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID targetId = uuid(rs, "id");
                        boolean applied = rs.getBoolean("effect_applied");
                        if (applied) {
                            continue;
                        }
                        String effectCode = rs.getString("effect_code");
                        if (effectCode == null) {
                            continue;
                        }
                        String payload = rs.getString("effect_payload");
                        EffectPayloadValidator.validate(type.effectSchemaJson(), payload, "effectPayload");
                        EffectHandlerRegistry.EffectHandler handler = effects.handler(effectCode);
                        if (handler == null) {
                            throw new ApiException.Conflict("no-effect-handler",
                                    "Nenhum manipulador registrado para o efeito " + effectCode
                                            + ": a execucao e recusada em vez de marcar o efeito como aplicado.");
                        }
                        handler.apply(new EffectHandlerRegistry.EffectContext(connection, schema, tenantId,
                                orderId, targetId, TargetType.valueOf(rs.getString("target_type")),
                                effectCode, payload, actorId));
                        try (PreparedStatement ins = connection.prepareStatement(
                                "INSERT INTO " + t("service_order_effects")
                                        + " (id, tenant_id, order_id, target_id, effect_code, payload, applied_by, created_by)"
                                        + " VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)")) {
                            ins.setObject(1, UUID.randomUUID());
                            ins.setObject(2, tenantId);
                            ins.setObject(3, orderId);
                            ins.setObject(4, targetId);
                            ins.setString(5, effectCode);
                            setText(ins, 6, payload);
                            setUuid(ins, 7, actorId);
                            setUuid(ins, 8, actorId);
                            ins.executeUpdate();
                        }
                        try (PreparedStatement upd = connection.prepareStatement(
                                "UPDATE " + t("service_order_targets")
                                        + " SET effect_applied = TRUE, effect_applied_at = NOW(), updated_by = ?"
                                        + " WHERE tenant_id = ? AND id = ?")) {
                            setUuid(upd, 1, actorId);
                            upd.setObject(2, tenantId);
                            upd.setObject(3, targetId);
                            upd.executeUpdate();
                        }
                        insertHistory(connection, orderId, "EFFECT_APPLIED", null, null, actorId, null,
                                Json.write(Map.of("targetId", targetId.toString(), "effectCode", effectCode)));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("service_orders") + " SET executed_at = NOW(), updated_by = ?"
                            + " WHERE tenant_id = ? AND id = ?")) {
                setUuid(ps, 1, actorId);
                ps.setObject(2, tenantId);
                ps.setObject(3, orderId);
                ps.executeUpdate();
            }
            return move(connection, orderId, ServiceOrderStatus.EXECUTED, actorId, null);
        });
    }

    public OrderView closeExecuted(UUID orderId, UUID actorId) {
        return inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status() != ServiceOrderStatus.EXECUTED) {
                throw new ApiException.Conflict("invalid-transition",
                        "So EXECUTED pode ser encerrada como executada (a ordem esta " + order.status() + ").");
            }
            checkSafetyGate(connection, orderId, "encerrar como executada", true);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM " + t("service_order_targets")
                            + " WHERE tenant_id = ? AND order_id = ? AND effect_code IS NOT NULL"
                            + " AND effect_applied = FALSE")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) > 0) {
                        throw new ApiException.Conflict("effects-pending",
                                "Ha alvos com efeito nao aplicado; a OS nao pode ser encerrada como executada.");
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("service_orders") + " SET closed_at = NOW(), updated_by = ?"
                            + " WHERE tenant_id = ? AND id = ?")) {
                setUuid(ps, 1, actorId);
                ps.setObject(2, tenantId);
                ps.setObject(3, orderId);
                ps.executeUpdate();
            }
            return move(connection, orderId, ServiceOrderStatus.CLOSED_EXECUTED, actorId, null);
        });
    }

    public OrderView closeNotExecuted(UUID orderId, String reasonCode, UUID actorId) {
        return inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status().isTerminal()) {
                throw new ApiException.Conflict("invalid-transition",
                        "Ordem encerrada nao pode ser encerrada de novo.");
            }
            if (reasonCode == null || reasonCode.isBlank()) {
                throw ApiException.Validation.of(new ApiException.FieldError("closeReasonCode", "REQUIRED",
                        "Encerrar sem execucao exige motivo do catalogo."));
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM " + t("service_order_close_reasons")
                            + " WHERE tenant_id = ? AND code = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, reasonCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw ApiException.Validation.of(new ApiException.FieldError("closeReasonCode", "UNKNOWN",
                                "Motivo de encerramento desconhecido: " + reasonCode + "."));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("service_orders")
                            + " SET close_reason_code = ?, closed_at = NOW(), updated_by = ?"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, reasonCode);
                setUuid(ps, 2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, orderId);
                ps.executeUpdate();
            }
            return move(connection, orderId, ServiceOrderStatus.CLOSED_NOT_EXECUTED, actorId, null);
        });
    }

    // ------------------------------------------------------------------
    // Work safety (SST)
    // ------------------------------------------------------------------

    public void recordSafety(UUID orderId, SafetyInput input, UUID actorId) {
        inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status().isTerminal()) {
                throw new ApiException.Conflict("order-closed",
                        "Ordem encerrada nao aceita registro de seguranca.");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_order_safety") + " (id, tenant_id, order_id, risk_analysis_ref,"
                            + " entry_permit_ref, work_permit_ref, trench_depth_m, qualified_release_by,"
                            + " qualified_release_at, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                            + " ON CONFLICT (tenant_id, order_id) DO UPDATE SET risk_analysis_ref = EXCLUDED.risk_analysis_ref,"
                            + " entry_permit_ref = EXCLUDED.entry_permit_ref, work_permit_ref = EXCLUDED.work_permit_ref,"
                            + " trench_depth_m = EXCLUDED.trench_depth_m, qualified_release_by = EXCLUDED.qualified_release_by,"
                            + " qualified_release_at = EXCLUDED.qualified_release_at, updated_by = EXCLUDED.updated_by")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, orderId);
                setText(ps, 4, input.riskAnalysisRef());
                setText(ps, 5, input.entryPermitRef());
                setText(ps, 6, input.workPermitRef());
                if (input.trenchDepthM() == null) {
                    ps.setNull(7, Types.NUMERIC);
                } else {
                    ps.setBigDecimal(7, input.trenchDepthM());
                }
                setUuid(ps, 8, input.qualifiedReleaseBy());
                if (input.qualifiedReleaseBy() == null) {
                    ps.setNull(9, Types.TIMESTAMP_WITH_TIMEZONE);
                } else {
                    ps.setTimestamp(9, Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));
                }
                setUuid(ps, 10, actorId);
                setUuid(ps, 11, actorId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public UUID addCrewMember(UUID orderId, CrewInput input, UUID actorId) {
        return inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status().isTerminal()) {
                throw new ApiException.Conflict("order-closed",
                        "Ordem encerrada nao aceita equipe.");
            }
            if (input.userId() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("userId", "REQUIRED",
                        "A equipe precisa do usuario."));
            }
            nonBlank(input.role(), "role");
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_order_crew") + " (id, tenant_id, order_id, user_id, role,"
                            + " qualification_code, qualification_valid_until, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, orderId);
                ps.setObject(4, input.userId());
                ps.setString(5, input.role());
                setText(ps, 6, input.qualificationCode());
                if (input.qualificationValidUntil() == null) {
                    ps.setNull(7, Types.DATE);
                } else {
                    ps.setDate(7, java.sql.Date.valueOf(input.qualificationValidUntil()));
                }
                setUuid(ps, 8, actorId);
                setUuid(ps, 9, actorId);
                ps.executeUpdate();
            }
            return id;
        });
    }

    /**
     * Issues one entry permit (PET, NR-33): valid for a single entry
     * only, traceable by code, retained at least five years. When the
     * caller omits {@code retainedUntil}, retention is exactly entry +
     * five years; a longer retention is accepted, a shorter one is
     * refused by the database CHECK.
     */
    public UUID issueEntryPermit(UUID orderId, PermitInput input, UUID actorId) {
        return inTx(connection -> {
            OrderView order = loadOrder(connection, orderId);
            if (order.status().isTerminal()) {
                throw new ApiException.Conflict("order-closed",
                        "Ordem encerrada nao aceita permissao de entrada.");
            }
            nonBlank(input.permitCode(), "permitCode");
            if (input.entryAt() == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("entryAt", "REQUIRED",
                        "A PET precisa da data e hora da entrada."));
            }
            OffsetDateTime retained = input.retainedUntil() == null
                    ? input.entryAt().plusYears(5) : input.retainedUntil();
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_order_entry_permits") + " (id, tenant_id, order_id, permit_code,"
                            + " entry_at, entered_by, supervisor_id, retained_until, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, orderId);
                ps.setString(4, input.permitCode());
                ps.setTimestamp(5, Timestamp.from(input.entryAt().toInstant()));
                setUuid(ps, 6, input.enteredBy());
                setUuid(ps, 7, input.supervisorId());
                ps.setTimestamp(8, Timestamp.from(retained.toInstant()));
                setUuid(ps, 9, actorId);
                setUuid(ps, 10, actorId);
                ps.executeUpdate();
            }
            return id;
        });
    }

    /**
     * Safety gate, run before scheduling and before closing as executed.
     * For each control the type requires (factory controls plus tenant
     * additions, which may only add): the reference must exist, the crew
     * must meet the minimum with a vigente qualification each, and a
     * trench deeper than 1.25 m needs a qualified release. The close gate
     * additionally requires at least one issued entry permit for confined
     * work (entries happen during execution, so the schedule gate cannot
     * require it yet).
     */
    private void checkSafetyGate(Connection connection, UUID orderId, String action, boolean requireEntryPermits)
            throws SQLException {
        ServiceTypeView type = loadTypeOf(connection, orderId);
        Set<ServiceOrderEnums.ControlCode> factory = factoryControls(type);
        if (factory.isEmpty()) {
            return;
        }
        Parameters parameters = readParameters(connection);
        // Absent key = factory controls only. A present key is the tenant's FULL
        // required set for the type and must still contain every factory control
        // (class c: the tenant may only amplify, never remove).
        Set<ServiceOrderEnums.ControlCode> tenantControls =
                parameters.requiredControls().get(type.code());
        ServiceOrderParameters.validateRequiredControls(type.code(), factory, tenantControls);
        Set<ServiceOrderEnums.ControlCode> required = new HashSet<>(factory);
        if (tenantControls != null) {
            required.addAll(tenantControls);
        }

        String riskRef = null;
        String entryRef = null;
        String workRef = null;
        java.math.BigDecimal trenchDepth = null;
        UUID releaseBy = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + t("service_order_safety") + " WHERE tenant_id = ? AND order_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    riskRef = rs.getString("risk_analysis_ref");
                    entryRef = rs.getString("entry_permit_ref");
                    workRef = rs.getString("work_permit_ref");
                    trenchDepth = rs.getBigDecimal("trench_depth_m");
                    releaseBy = uuid(rs, "qualified_release_by");
                }
            }
        }
        List<String> missing = new ArrayList<>();
        if (required.contains(ServiceOrderEnums.ControlCode.RISK_ANALYSIS)
                && (riskRef == null || riskRef.isBlank())) {
            missing.add("analise de risco da tarefa");
        }
        if (required.contains(ServiceOrderEnums.ControlCode.ENTRY_PERMIT)
                && (entryRef == null || entryRef.isBlank())) {
            missing.add("permissao de entrada rastreavel");
        }
        if (required.contains(ServiceOrderEnums.ControlCode.WORK_PERMIT)
                && (workRef == null || workRef.isBlank())) {
            missing.add("permissao de trabalho rastreavel");
        }
        if (required.contains(ServiceOrderEnums.ControlCode.QUALIFIED_RELEASE)
                && trenchDepth != null && trenchDepth.compareTo(new java.math.BigDecimal("1.25")) > 0
                && releaseBy == null) {
            missing.add("liberacao de profissional habilitado para vala acima de 1,25 m (NR-18, 18.7.2.3)");
        }
        if (required.contains(ServiceOrderEnums.ControlCode.MIN_CREW)
                || required.contains(ServiceOrderEnums.ControlCode.TRAINING_VALID)) {
            int minCrew = type.confinedSpace()
                    ? Math.max(type.minCrew() == null ? 1 : type.minCrew(), parameters.minCrewConfinedSpace())
                    : (type.minCrew() == null ? 1 : type.minCrew());
            var crew = new ArrayList<String[]>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT user_id, qualification_code, qualification_valid_until FROM " + t("service_order_crew")
                            + " WHERE tenant_id = ? AND order_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        java.sql.Date valid = rs.getDate("qualification_valid_until");
                        crew.add(new String[]{uuid(rs, "user_id").toString(), rs.getString("qualification_code"),
                                valid == null ? null : valid.toLocalDate().toString()});
                    }
                }
            }
            if (crew.size() < minCrew) {
                missing.add("equipe minima de " + minCrew + " (atual: " + crew.size() + ")");
            }
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            for (String[] member : crew) {
                String code = member[1];
                String validUntil = member[2];
                if (code == null || code.isBlank() || validUntil == null
                        || LocalDate.parse(validUntil).isBefore(today)) {
                    missing.add("habilitacao vigente para o membro " + member[0]);
                    continue;
                }
                if (competencyRegistry != null) {
                    CompetencyRegistry.Competency found =
                            competencyRegistry.lookup(UUID.fromString(member[0]), code);
                    if (found != null && (!found.qualified()
                            || found.validUntil() == null || found.validUntil().isBefore(today))) {
                        missing.add("habilitacao vigente no registro de competencias para o membro " + member[0]);
                    }
                }
            }
        }
        if (requireEntryPermits && type.confinedSpace()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM " + t("service_order_entry_permits")
                            + " WHERE tenant_id = ? AND order_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) == 0) {
                        missing.add("ao menos uma PET emitida por entrada (NR-33)");
                    }
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new ApiException.Conflict("safety-gate",
                    "A OS nao pode ser " + action + " sem: " + String.join("; ", missing) + ".");
        }
    }

    // ------------------------------------------------------------------
    // Notes, history tab, reports
    // ------------------------------------------------------------------

    /**
     * Adds an internal note. A body matching a document or e-mail pattern
     * raises the personal-data alert (a warning, not a refusal: the plan
     * asks for an alert against sensitive personal data in free text).
     * The body is redacted from the audit trail regardless (V9_1).
     */
    public UUID addNote(UUID orderId, UUID authorId, ServiceOrderEnums.NoteVisibility visibility, String body) {
        return inTx(connection -> {
            loadOrder(connection, orderId);
            nonBlank(body, "body");
            boolean alert = CPF_LIKE.matcher(body).find() || CNPJ_LIKE.matcher(body).find()
                    || EMAIL_LIKE.matcher(body).find();
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("service_order_notes") + " (id, tenant_id, order_id, author_id, visibility,"
                            + " body, personal_data_alert, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, orderId);
                setUuid(ps, 4, authorId);
                ps.setString(5, visibility.name());
                ps.setString(6, body);
                ps.setBoolean(7, alert);
                setUuid(ps, 8, authorId);
                setUuid(ps, 9, authorId);
                ps.executeUpdate();
            }
            return id;
        });
    }

    public boolean noteHasPersonalDataAlert(UUID noteId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT personal_data_alert FROM " + t("service_order_notes")
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, noteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("Observacao nao encontrada.");
                    }
                    return rs.getBoolean(1);
                }
            }
        });
    }

    /**
     * History tab source for one consumer unit: applied orders, status
     * history and applied effects, each with who, when, before and after.
     * Version sources of other records (parameter versions, bill and
     * tariff versions, GIS package versions) are read by their own tabs;
     * this query unions the order-side sources of a unit.
     */
    public List<HistoryRow> unitHistory(UUID unitId) {
        return inTx(connection -> {
            var rows = new ArrayList<HistoryRow>();
            String sql = """
                    SELECT h.occurred_at, h.actor_id, 'ORDER_EVENT' AS source,
                           h.event_type AS kind,
                           jsonb_build_object('status', h.from_status) AS before_json,
                           jsonb_build_object('status', h.to_status) AS after_json,
                           h.order_id
                    FROM %s.service_order_history h
                    JOIN %s.service_order_targets tgt
                      ON tgt.tenant_id = h.tenant_id AND tgt.order_id = h.order_id
                    WHERE h.tenant_id = ? AND tgt.target_type = 'CONSUMER_UNIT' AND tgt.target_id = ?
                    UNION ALL
                    SELECT u.changed_at, u.created_by, 'UNIT_STATUS', 'STATUS_CHANGE',
                           jsonb_build_object('statusId', u.from_status_id),
                           jsonb_build_object('statusId', u.to_status_id),
                           u.service_order_id
                    FROM %s.unit_status_history u
                    WHERE u.tenant_id = ? AND u.unit_id = ?
                    UNION ALL
                    SELECT e.applied_at, e.applied_by, 'EFFECT', 'EFFECT_APPLIED',
                           NULL, jsonb_build_object('effectCode', e.effect_code), e.order_id
                    FROM %s.service_order_effects e
                    JOIN %s.service_order_targets tgt
                      ON tgt.tenant_id = e.tenant_id AND tgt.id = e.target_id
                    WHERE e.tenant_id = ? AND tgt.target_type = 'CONSUMER_UNIT' AND tgt.target_id = ?
                    ORDER BY 1""".formatted(schema, schema, schema, schema, schema);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, unitId);
                ps.setObject(3, tenantId);
                ps.setObject(4, unitId);
                ps.setObject(5, tenantId);
                ps.setObject(6, unitId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new HistoryRow(timestamp(rs, "occurred_at"), uuid(rs, "actor_id"),
                                rs.getString("source"), rs.getString("kind"),
                                rs.getString("before_json"), rs.getString("after_json"),
                                uuid(rs, "order_id")));
                    }
                }
            }
            return rows;
        });
    }

    /** Report separation: internal orders vs field orders, by status. */
    public List<ModeCount> countByMode() {
        return inTx(connection -> {
            var rows = new ArrayList<ModeCount>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT execution_mode, status, COUNT(*) FROM " + t("service_orders")
                            + " WHERE tenant_id = ? GROUP BY execution_mode, status ORDER BY 1, 2")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new ModeCount(
                                ServiceOrderEnums.ExecutionMode.valueOf(rs.getString(1)),
                                ServiceOrderStatus.valueOf(rs.getString(2)), rs.getLong(3)));
                    }
                }
            }
            return rows;
        });
    }
}
