package br.com.ziipsan.core.cutoff;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cutoff and reconnection as operations of the P1.31 work-order engine
 * (P3.4; ADR-0039) — this service creates, submits, revalidates and
 * cancels orders THROUGH {@link ServiceOrderService}; it has no order
 * engine, no status machine and no direct connection-status write of
 * its own. The status effect itself is applied by the engine through
 * the {@link CutoffPorts.ConnectionEffectPort} handler binding.
 *
 * <p>JDBC follows the {@code ServiceOrderService} pattern (own
 * transaction, {@code app.current_tenant} set, accepted-parameter SQL
 * of P0.19). The field execution is ED-20 and the screens are ED-36:
 * this service is the rule/persistence layer they call, online or with
 * an offline fact snapshot.
 */
public class CutoffService {

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

    private static final List<String> PARAMETER_KEYS = List.of(
            CutoffParameters.MIN_NOTICE_DAYS, CutoffParameters.NOTICE_VALIDITY_DAYS,
            CutoffParameters.MIN_OVERDUE_DAYS, CutoffParameters.MAX_DEBT_AGE_DAYS,
            CutoffParameters.MIN_DEBT_AMOUNT, CutoffParameters.POST_CUT_NOTICE_HOURS,
            CutoffParameters.PROTECTED_CLASSES, CutoffParameters.SOCIAL_TARIFF_ALLOWED,
            CutoffParameters.SEWER_ALLOWED, CutoffParameters.REASONS,
            CutoffParameters.SLA_HOURS, CutoffParameters.URGENT_FEE,
            CutoffParameters.RELEASE_CONDITION, CutoffParameters.AUTO_ORDER_ON_PAYMENT,
            CutoffParameters.UNDUE_CUT_COMPENSATION);

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;
    private final ServiceOrderService engine;
    private final CutoffPorts.DeliveryProofPort proofPort;

    public CutoffService(DataSource dataSource, String schema, UUID tenantId,
            ServiceOrderService engine, CutoffPorts.DeliveryProofPort proofPort,
            CutoffPorts.ConnectionEffectPort effectPort) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null || engine == null) {
            throw new IllegalArgumentException("dataSource, schema, tenantId e o nucleo de OS sao obrigatorios");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
        this.engine = engine;
        this.proofPort = proofPort;
        if (effectPort != null) {
            engine.effects().register(CutoffOrderTypes.CUT_EFFECT_CODE, context -> {
                var payload = Json.readTree(context.effectPayloadJson());
                effectPort.applyCut(context.connection(),
                        UUID.fromString(payload.path("consumerUnitId").asText()),
                        context.orderId(),
                        payload.hasNonNull("cutLevel") ? payload.path("cutLevel").asText() : null);
            });
            engine.effects().register(CutoffOrderTypes.RECONNECT_EFFECT_CODE, context -> {
                var payload = Json.readTree(context.effectPayloadJson());
                effectPort.applyReconnection(context.connection(),
                        UUID.fromString(payload.path("consumerUnitId").asText()),
                        context.orderId());
            });
        }
    }

    // ------------------------------------------------------------------
    // Setup (integration calls once per tenant)
    // ------------------------------------------------------------------

    /** Declares the three service types through the engine, when absent. */
    public void declareTypes(UUID actorId) {
        for (ServiceOrderService.ServiceTypeInput input : List.of(
                CutoffOrderTypes.cutoffType(), CutoffOrderTypes.reconnectionType(),
                CutoffOrderTypes.inspectionType())) {
            try {
                engine.findTypeByCode(input.code());
            } catch (ApiException.NotFound e) {
                engine.declareType(input, actorId);
            }
        }
    }

    /** Creates the revalidation close reason in the engine catalog, when absent. */
    public void ensureRevalidationCloseReason(UUID actorId) {
        boolean exists = inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM " + t("service_order_close_reasons")
                            + " WHERE tenant_id = ? AND code = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, CutoffOrderTypes.REVALIDATION_CLOSE_REASON);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
        if (!exists) {
            engine.createCloseReason(CutoffOrderTypes.REVALIDATION_CLOSE_REASON,
                    "Revalidacao do corte falhou na execucao (P3.4, classe a)", actorId);
        }
    }

    // ------------------------------------------------------------------
    // Parameters (accepted + vigente, P0.19; agency values from P2.31)
    // ------------------------------------------------------------------

    public CutoffParameters.Values loadParameters() {
        return inTx(connection -> {
            String sql = String.format(ACCEPTED_PARAMETER_SQL, schema, schema);
            Map<String, String> raw = new LinkedHashMap<>();
            for (String key : PARAMETER_KEYS) {
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
            return CutoffParameters.parse(raw);
        });
    }

    // ------------------------------------------------------------------
    // Selection and notice
    // ------------------------------------------------------------------

    /**
     * Evaluates the minimum selection of a unit; when the unit is not
     * selected, records the non-generation reason per unit (the report
     * is P3.22).
     */
    public NoticeSelectionRule.Selection evaluateSelection(NoticeSelectionRule.UnitFacts facts,
            CutoffParameters.Values params, LocalDate evaluationDate, UUID actorId) {
        NoticeSelectionRule.Selection selection = NoticeSelectionRule.evaluate(facts, params, evaluationDate);
        if (!selection.selected()) {
            insertNonGeneration(facts.consumerUnitId(), selection.nonGenerationReason(),
                    Map.of("connectionStatus", facts.connectionStatus().name(),
                            "billUnderReviewOrContested", facts.billUnderReviewOrContested()),
                    actorId);
        }
        return selection;
    }

    /** Issues the notice from a successful selection, through the P2.30 template identity. */
    public CutoffNoticeRule.Notice issueNotice(NoticeSelectionRule.UnitFacts facts,
            NoticeSelectionRule.Selection selection, LocalDate noticeDate,
            String templateCode, String templateVersion,
            CutoffParameters.Values params, CutoffCalendar.CalendarData calendar, UUID actorId) {
        if (!selection.selected()) {
            throw new ApiException.Conflict("cutoff-not-selected",
                    "Aviso nao nasce de selecao recusada: " + selection.nonGenerationReason() + ".");
        }
        // A still-valid open notice for the unit blocks a duplicate:
        // the non-generation reason NOTICE_ALREADY_VALID is recorded.
        var openNotice = latestOpenNotice(facts.consumerUnitId());
        if (openNotice != null && !openNotice.isExpiredAt(noticeDate, params)) {
            insertNonGeneration(facts.consumerUnitId(),
                    CutoffEnums.NonGenerationReason.NOTICE_ALREADY_VALID,
                    Map.of("existingNoticeId", openNotice.id().toString()), actorId);
            throw new ApiException.Conflict("cutoff-notice-already-valid",
                    "Ja ha aviso valido para a unidade: " + openNotice.id() + ".");
        }
        LocalDate earliest = CutoffNoticeRule.earliestCutDate(noticeDate, params, calendar);
        UUID id = UUID.randomUUID();
        List<Map<String, Object>> snapshot = selection.motivatingDebts().stream()
                .map(d -> Map.<String, Object>of("dueDate", d.dueDate().toString(),
                        "amount", d.amount().toPlainString(), "status", d.status().name()))
                .toList();
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("cutoff_notices") + " (id, tenant_id, consumer_unit_id, notice_date,"
                            + " earliest_cut_date, template_code, template_version, debts_snapshot,"
                            + " total_amount, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, facts.consumerUnitId());
                ps.setObject(4, noticeDate);
                ps.setObject(5, earliest);
                ps.setString(6, templateCode);
                ps.setString(7, templateVersion);
                ps.setString(8, Json.write(snapshot));
                ps.setBigDecimal(9, selection.totalAmount());
                setUuid(ps, 10, actorId);
                setUuid(ps, 11, actorId);
                ps.executeUpdate();
            }
            return null;
        });
        return new CutoffNoticeRule.Notice(id, facts.consumerUnitId(), noticeDate, earliest,
                templateCode, templateVersion, CutoffEnums.DeliveryProofStatus.NONE);
    }

    /** Refreshes the notice proof from the P3.5 port and persists it. */
    public CutoffEnums.DeliveryProofStatus refreshNoticeProof(UUID noticeId, UUID actorId) {
        if (proofPort == null) {
            throw new IllegalStateException("porta de prova de entrega (P3.5) nao ligada: prova nao presumida");
        }
        CutoffEnums.DeliveryProofStatus status = proofPort.proofStatusOf(noticeId);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("cutoff_notices") + " SET delivery_proof_status = ?,"
                            + " delivery_proof_at = CASE WHEN ? = 'DELIVERED' THEN NOW() ELSE delivery_proof_at END,"
                            + " updated_by = ? WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, status.name());
                ps.setString(2, status.name());
                setUuid(ps, 3, actorId);
                ps.setObject(4, tenantId);
                ps.setObject(5, noticeId);
                if (ps.executeUpdate() == 0) {
                    throw new ApiException.NotFound("Aviso de corte nao encontrado.");
                }
            }
            return null;
        });
        return status;
    }

    public CutoffNoticeRule.Notice loadNotice(UUID noticeId) {
        return inTx(connection -> loadNotice(connection, noticeId));
    }

    // ------------------------------------------------------------------
    // Cutoff order
    // ------------------------------------------------------------------

    public record CreateCutoffInput(
            UUID consumerUnitId, CutoffEnums.Reason reason, boolean sewer, CutoffEnums.CutLevel cutLevel,
            UUID noticeId, boolean judicialOrder, String judicialOrderRef,
            String emergencyJustification, UUID emergencyPhotoDocumentId,
            Set<CutoffEnums.ProtectedClass> unitProtectedClasses,
            java.time.ZonedDateTime when, CutoffCalendar.CalendarData calendar) {
    }

    /** Validates the gate and creates + submits the cutoff order in the engine. */
    public UUID createCutoffOrder(CreateCutoffInput input, CutoffParameters.Values params, UUID actorId) {
        // Cross-review correction: the V9_2 CHECK requires the reference
        // of a judicial order; without this pre-check the refusal only
        // surfaced at the cutoff_orders INSERT, AFTER the engine had
        // already created and submitted the order (orphan OS).
        if (input.judicialOrder()
                && (input.judicialOrderRef() == null || input.judicialOrderRef().isBlank())) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "judicialOrderRef", "REQUIRED",
                    "Ordem judicial exige a referencia do processo/oficio que a ressalva."));
        }
        CutoffNoticeRule.Notice notice = input.noticeId() == null ? null : loadNotice(input.noticeId());
        CutoffExecutionRule.assertAllowed(new CutoffExecutionRule.GateInput(
                input.reason(), input.sewer(), input.when(), input.calendar(), input.judicialOrder(),
                input.emergencyJustification(), input.emergencyPhotoDocumentId(),
                input.unitProtectedClasses(), notice, params));
        var order = engine.createOrder(new ServiceOrderService.OrderInput(
                CutoffOrderTypes.CUTOFF_TYPE_CODE, ServiceOrderEnums.Origin.INTERNAL,
                ServiceOrderEnums.ExecutionMode.FIELD, ServiceOrderEnums.Priority.NORMAL,
                actorId, null, null, null, null));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("consumerUnitId", input.consumerUnitId().toString());
        if (input.cutLevel() != null) {
            payload.put("cutLevel", input.cutLevel().name());
        }
        engine.addTarget(order.id(), new ServiceOrderService.TargetInput(
                br.com.ziipsan.core.serviceorder.TargetType.CONSUMER_UNIT, input.consumerUnitId(),
                null, null, CutoffOrderTypes.CUT_EFFECT_CODE, Json.write(payload)), actorId);
        engine.submit(order.id(), actorId);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("cutoff_orders") + " (id, tenant_id, service_order_id, consumer_unit_id,"
                            + " notice_id, reason, cut_level, judicial_order, judicial_order_ref,"
                            + " emergency_justification, emergency_photo_document_id, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, order.id());
                ps.setObject(4, input.consumerUnitId());
                setUuid(ps, 5, input.noticeId());
                ps.setString(6, input.reason().name());
                setText(ps, 7, input.cutLevel() == null ? null : input.cutLevel().name());
                ps.setBoolean(8, input.judicialOrder());
                setText(ps, 9, input.judicialOrderRef());
                setText(ps, 10, input.emergencyJustification());
                setUuid(ps, 11, input.emergencyPhotoDocumentId());
                setUuid(ps, 12, actorId);
                setUuid(ps, 13, actorId);
                ps.executeUpdate();
            }
            return null;
        });
        return order.id();
    }

    // ------------------------------------------------------------------
    // Revalidation at execution (class a): failure cancels the order
    // ------------------------------------------------------------------

    public record RevalidationInput(
            boolean motivatingDebtsSettledOrAgreed, boolean billUnderReviewOrContested,
            boolean judicialOrder, java.time.ZonedDateTime executionWhen,
            CutoffCalendar.CalendarData calendar, Set<CutoffEnums.ProtectedClass> unitProtectedClasses) {
    }

    /**
     * Revalidates the cutoff of an order against the execution snapshot
     * (works offline: the snapshot is the input). On failure the order
     * is CANCELLED through the engine ({@code closeNotExecuted}, close
     * reason {@code CUT_REVALIDATION_FAILED}) and the failure is
     * recorded on {@code cutoff_orders}.
     */
    public Optional<CutoffEnums.RevalidationFailure> revalidate(UUID serviceOrderId,
            RevalidationInput input, CutoffParameters.Values params, UUID actorId) {
        var row = loadCutoffOrder(serviceOrderId);
        CutoffNoticeRule.Notice notice = row.noticeId() == null ? null : loadNotice(row.noticeId());
        var failure = CutoffRevalidationRule.evaluate(new CutoffRevalidationRule.Snapshot(
                input.motivatingDebtsSettledOrAgreed(), input.billUnderReviewOrContested(),
                row.reason(), input.judicialOrder(), input.executionWhen(), input.calendar(),
                notice, input.unitProtectedClasses(), params));
        // Cross-review correction: cancel through the engine FIRST, record
        // after. The first version recorded FAILED in its own transaction
        // before closeNotExecuted: if the engine refused the cancellation
        // (e.g. the order had already been executed), cutoff_orders would
        // claim a cancellation that never happened.
        if (failure.isPresent()) {
            ensureRevalidationCloseReason(actorId);
            engine.closeNotExecuted(serviceOrderId, CutoffOrderTypes.REVALIDATION_CLOSE_REASON, actorId);
        }
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("cutoff_orders") + " SET revalidation_status = ?,"
                            + " revalidation_failure_code = ?, revalidated_at = NOW(), updated_by = ?"
                            + " WHERE tenant_id = ? AND service_order_id = ?")) {
                ps.setString(1, failure.isPresent() ? "FAILED" : "PASSED");
                setText(ps, 2, failure.map(Enum::name).orElse(null));
                setUuid(ps, 3, actorId);
                ps.setObject(4, tenantId);
                ps.setObject(5, serviceOrderId);
                ps.executeUpdate();
            }
            return null;
        });
        return failure;
    }

    // ------------------------------------------------------------------
    // Reconnection on payment
    // ------------------------------------------------------------------

    public record PaymentInput(
            boolean motivatingDebtPaidOrAgreed, boolean downPaymentPaid, boolean fullSettlement,
            boolean urgentRequested, OffsetDateTime paymentAt) {
    }

    public record ReconnectionOutcome(ReconnectionRule.Decision decision, UUID serviceOrderId) {
    }

    /**
     * Payment event of a cut unit: when the parameters say so and the
     * release condition is met, creates + submits the reconnection
     * order in the engine and records the fee decision and SLA.
     */
    public ReconnectionOutcome onPayment(UUID cutoffServiceOrderId, PaymentInput input,
            CutoffParameters.Values params, UUID actorId) {
        var cutoff = loadCutoffOrder(cutoffServiceOrderId);
        CutoffEnums.DeliveryProofStatus proof = CutoffEnums.DeliveryProofStatus.NONE;
        if (cutoff.noticeId() != null) {
            proof = loadNotice(cutoff.noticeId()).deliveryProofStatus();
        }
        var facts = new ReconnectionRule.PaymentFacts(
                input.motivatingDebtPaidOrAgreed(), input.downPaymentPaid(), input.fullSettlement(),
                input.urgentRequested(), cutoff.undue(), proof);
        var decision = ReconnectionRule.evaluate(facts, params, input.paymentAt());
        if (!decision.createOrder()) {
            return new ReconnectionOutcome(decision, null);
        }
        var order = engine.createOrder(new ServiceOrderService.OrderInput(
                CutoffOrderTypes.RECONNECTION_TYPE_CODE, ServiceOrderEnums.Origin.INTERNAL,
                ServiceOrderEnums.ExecutionMode.FIELD, ServiceOrderEnums.Priority.NORMAL,
                actorId, null, null, null, null));
        engine.addTarget(order.id(), new ServiceOrderService.TargetInput(
                br.com.ziipsan.core.serviceorder.TargetType.CONSUMER_UNIT, cutoff.consumerUnitId(),
                null, null, CutoffOrderTypes.RECONNECT_EFFECT_CODE,
                Json.write(Map.of("consumerUnitId", cutoff.consumerUnitId().toString()))), actorId);
        engine.submit(order.id(), actorId);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("reconnection_orders") + " (id, tenant_id, service_order_id,"
                            + " cutoff_order_id, consumer_unit_id, release_condition, fee_decision,"
                            + " urgent, sla_due_at, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, order.id());
                ps.setObject(4, cutoff.id());
                ps.setObject(5, cutoff.consumerUnitId());
                ps.setString(6, decision.appliedCondition().name());
                ps.setString(7, decision.feeDecision().name());
                ps.setBoolean(8, input.urgentRequested());
                ps.setTimestamp(9, Timestamp.from(decision.slaDueAt().toInstant()));
                setUuid(ps, 10, actorId);
                setUuid(ps, 11, actorId);
                ps.executeUpdate();
            }
            return null;
        });
        return new ReconnectionOutcome(decision, order.id());
    }

    /** Marks a reconnection completed; returns true when it completed late (compensation trigger). */
    public boolean completeReconnection(UUID serviceOrderId, OffsetDateTime completedAt, UUID actorId) {
        return inTx(connection -> {
            OffsetDateTime due = null;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT sla_due_at FROM " + t("reconnection_orders")
                            + " WHERE tenant_id = ? AND service_order_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, serviceOrderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("Religacao nao encontrada para a OS.");
                    }
                    Timestamp ts = rs.getTimestamp(1);
                    due = ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
                }
            }
            boolean late = ReconnectionRule.isLate(due, completedAt);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("reconnection_orders") + " SET completed_at = ?, late = ?, updated_by = ?"
                            + " WHERE tenant_id = ? AND service_order_id = ?")) {
                ps.setTimestamp(1, Timestamp.from(completedAt.toInstant()));
                ps.setBoolean(2, late);
                setUuid(ps, 3, actorId);
                ps.setObject(4, tenantId);
                ps.setObject(5, serviceOrderId);
                ps.executeUpdate();
            }
            return late;
        });
    }

    // ------------------------------------------------------------------
    // Compensation
    // ------------------------------------------------------------------

    /** Computes (by the tenant formula) and records the automatic compensation. */
    public UndueCutCompensationRule.Compensation registerCompensation(UUID cutoffServiceOrderId,
            CutoffEnums.CompensationTrigger trigger, java.math.BigDecimal lastInvoiceAmount,
            java.math.BigDecimal firstInvoiceAfterReconnectionAmount, java.math.BigDecimal manualAmount,
            OffsetDateTime detectedAt, CutoffParameters.Values params, UUID actorId) {
        // Cross-review correction: detectedAt is written into the basis
        // JSON for every formula; a null one died as a NullPointerException
        // instead of the explicit error the package promises for absences.
        if (detectedAt == null) {
            throw new IllegalArgumentException("instante da constatacao (corte indevido/atraso) e obrigatorio");
        }
        var cutoff = loadCutoffOrder(cutoffServiceOrderId);
        var compensation = UndueCutCompensationRule.compute(params.undueCompensationFormula(),
                params.urgentFee(), lastInvoiceAmount, firstInvoiceAfterReconnectionAmount,
                manualAmount, detectedAt);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("cutoff_compensations") + " (id, tenant_id, consumer_unit_id,"
                            + " cutoff_order_id, trigger_reason, formula, credit_amount, basis, status,"
                            + " reconnection_due_at, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, cutoff.consumerUnitId());
                ps.setObject(4, cutoff.id());
                ps.setString(5, trigger.name());
                ps.setString(6, compensation.formula().name());
                if (compensation.creditAmount() == null) {
                    ps.setNull(7, Types.NUMERIC);
                } else {
                    ps.setBigDecimal(7, compensation.creditAmount());
                }
                ps.setString(8, Json.write(Map.of(
                        "trigger", trigger.name(),
                        "detectedAt", detectedAt.toString())));
                ps.setString(9, compensation.status().name());
                if (compensation.reconnectionDueAt() == null) {
                    ps.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE);
                } else {
                    ps.setTimestamp(10, Timestamp.from(compensation.reconnectionDueAt().toInstant()));
                }
                setUuid(ps, 11, actorId);
                setUuid(ps, 12, actorId);
                ps.executeUpdate();
            }
            return null;
        });
        return compensation;
    }

    // ------------------------------------------------------------------
    // Consumption after cutoff -> inspection order (ADR-0039)
    // ------------------------------------------------------------------

    /**
     * A cut connection that registers consumption again does NOT change
     * its situation by itself: this creates the inspection order in the
     * engine (possible unregistered reconnection, NR 11, art. 42, V)
     * and records the alert. The situation changes only when that order
     * is closed — no status write happens here.
     */
    public UUID onConsumptionAfterCut(UUID consumerUnitId, String readingRef, UUID actorId) {
        var order = engine.createOrder(new ServiceOrderService.OrderInput(
                CutoffOrderTypes.INSPECTION_TYPE_CODE, ServiceOrderEnums.Origin.INTERNAL,
                ServiceOrderEnums.ExecutionMode.FIELD, ServiceOrderEnums.Priority.HIGH,
                actorId, null, null, null, null));
        engine.addTarget(order.id(), new ServiceOrderService.TargetInput(
                br.com.ziipsan.core.serviceorder.TargetType.CONSUMER_UNIT, consumerUnitId,
                null, null, null, null), actorId);
        engine.submit(order.id(), actorId);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("cutoff_consumption_alerts") + " (id, tenant_id, consumer_unit_id,"
                            + " reading_ref, inspection_order_id, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, consumerUnitId);
                ps.setString(4, readingRef);
                ps.setObject(5, order.id());
                setUuid(ps, 6, actorId);
                setUuid(ps, 7, actorId);
                ps.executeUpdate();
            }
            return null;
        });
        return order.id();
    }

    // ------------------------------------------------------------------
    // Post-cut notice
    // ------------------------------------------------------------------

    /** Computes (parameter required) and records the post-cut notice deadline on the notice. */
    public OffsetDateTime schedulePostCutNotice(UUID noticeId, OffsetDateTime executedAt,
            CutoffParameters.Values params, UUID actorId) {
        OffsetDateTime due = CutoffNoticeRule.postCutNoticeDueAt(executedAt, params);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("cutoff_notices") + " SET post_cut_notice_due_at = ?,"
                            + " status = 'CONSUMED', updated_by = ? WHERE tenant_id = ? AND id = ?")) {
                ps.setTimestamp(1, Timestamp.from(due.toInstant()));
                setUuid(ps, 2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, noticeId);
                if (ps.executeUpdate() == 0) {
                    throw new ApiException.NotFound("Aviso de corte nao encontrado.");
                }
            }
            return null;
        });
        return due;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private record CutoffOrderRow(UUID id, UUID consumerUnitId, UUID noticeId,
            CutoffEnums.Reason reason, boolean undue) {
    }

    private CutoffOrderRow loadCutoffOrder(UUID serviceOrderId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, consumer_unit_id, notice_id, reason, undue FROM " + t("cutoff_orders")
                            + " WHERE tenant_id = ? AND service_order_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, serviceOrderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("Corte nao encontrado para a OS.");
                    }
                    return new CutoffOrderRow((UUID) rs.getObject("id"),
                            (UUID) rs.getObject("consumer_unit_id"), (UUID) rs.getObject("notice_id"),
                            CutoffEnums.Reason.valueOf(rs.getString("reason")), rs.getBoolean("undue"));
                }
            }
        });
    }

    private CutoffNoticeRule.Notice loadNotice(Connection connection, UUID noticeId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + t("cutoff_notices") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, noticeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("Aviso de corte nao encontrado.");
                }
                return new CutoffNoticeRule.Notice(
                        (UUID) rs.getObject("id"), (UUID) rs.getObject("consumer_unit_id"),
                        rs.getObject("notice_date", LocalDate.class),
                        rs.getObject("earliest_cut_date", LocalDate.class),
                        rs.getString("template_code"), rs.getString("template_version"),
                        CutoffEnums.DeliveryProofStatus.valueOf(rs.getString("delivery_proof_status")));
            }
        }
    }

    private CutoffNoticeRule.Notice latestOpenNotice(UUID unitId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id FROM " + t("cutoff_notices")
                            + " WHERE tenant_id = ? AND consumer_unit_id = ? AND status = 'ISSUED'"
                            + " ORDER BY notice_date DESC, created_at DESC LIMIT 1")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, unitId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return loadNotice(connection, (UUID) rs.getObject("id"));
                }
            }
        });
    }

    private void insertNonGeneration(UUID unitId, CutoffEnums.NonGenerationReason reason,
            Map<String, Object> detail, UUID actorId) {
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("cutoff_non_generation_reasons")
                            + " (id, tenant_id, consumer_unit_id, reason_code, detail, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, unitId);
                ps.setString(4, reason.name());
                ps.setString(5, Json.write(detail));
                setUuid(ps, 6, actorId);
                setUuid(ps, 7, actorId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
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
                throw new IllegalStateException("Cutoff operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cutoff operation failed: " + e.getMessage(), e);
        }
    }

    private String t(String table) {
        return schema + "." + table;
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
}
