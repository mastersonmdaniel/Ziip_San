package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.commercial.billing.bill.BillStatus;
import br.com.ziipsan.commercial.billing.domain.BillCancelReasonEntity;
import br.com.ziipsan.commercial.billing.domain.BillCreditEntity;
import br.com.ziipsan.commercial.billing.domain.BillDebitEntity;
import br.com.ziipsan.commercial.billing.domain.BillEntity;
import br.com.ziipsan.commercial.billing.domain.BillFieldChangeEntity;
import br.com.ziipsan.commercial.billing.domain.BillMaintenanceOperationEntity;
import br.com.ziipsan.commercial.billing.domain.BillReviewEntity;
import br.com.ziipsan.commercial.billing.domain.BillReviewReasonEntity;
import br.com.ziipsan.commercial.billing.domain.BillStatusEventEntity;
import br.com.ziipsan.commercial.billing.domain.CreditReceivableEntity;
import br.com.ziipsan.commercial.billing.domain.ReadingAnalysisDecisionEntity;
import br.com.ziipsan.commercial.billing.repository.BillCancelReasonRepository;
import br.com.ziipsan.commercial.billing.repository.BillCreditRepository;
import br.com.ziipsan.commercial.billing.repository.BillDebitRepository;
import br.com.ziipsan.commercial.billing.repository.BillFieldChangeRepository;
import br.com.ziipsan.commercial.billing.repository.BillMaintenanceOperationRepository;
import br.com.ziipsan.commercial.billing.repository.BillRepository;
import br.com.ziipsan.commercial.billing.repository.BillReviewRepository;
import br.com.ziipsan.commercial.billing.repository.BillReviewReasonRepository;
import br.com.ziipsan.commercial.billing.repository.BillStatusEventRepository;
import br.com.ziipsan.commercial.billing.repository.CreditReceivableRepository;
import br.com.ziipsan.commercial.billing.repository.ReadingAnalysisDecisionRepository;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Manutencao e retificacao de conta pelo backoffice (P2.20, DAD-16).
 *
 * <p>Invariantes de todas as operacoes:
 * <ul>
 *   <li>motivo de catalogo (revisao/cancelamento) e anotacao sao
 *       obrigatorios -- sem eles, nada e criado, nem a OS;</li>
 *   <li>pedido do usuario entra pelo protocolo da P1.36 (OS EXTERNAL com
 *       {@code protocolId}); alteracao interna gera OS sem protocolo
 *       (INTERNAL) -- ADR-0039, item 7, a mesma regra que o motor da P1.31
 *       ja impoe;</li>
 *   <li>aprovacao conforme {@code service_type.internal_approval} sobre o
 *       padrao de fabrica do tipo -- traduzida para o motor real, que so
 *       conhece o {@code requires_approval} declarado no tipo (ver
 *       {@link #ensureOrder});</li>
 *   <li>a manutencao so e aplicada <b>dentro</b> do {@code execute} da OS,
 *       pelo handler de efeito que o adaptador registra no motor: operacao
 *       aguardando aprovacao nao aplica nada, e efeito que falha nao fica
 *       marcado como aplicado;</li>
 *   <li>operacao que muda a conta gera <b>nova versao</b> (a anterior sai
 *       da vigencia sem ser apagada), evento em {@code bill_status_events}
 *       e trilha por campo em {@code bill_field_changes};</li>
 *   <li>repetir a {@code Idempotency-Key} com o mesmo pedido devolve o
 *       resultado gravado; com pedido diferente (qualquer campo da
 *       composicao), conflito.</li>
 * </ul>
 *
 * <p>O recalculo tarifario em si e do motor da P2.5 ({@code BillCalculator}):
 * quem chama entrega os valores recalculados em {@link RectifiedValues} e
 * este servico confere a formula do total (o mesmo CHECK do banco), as
 * travas de parametro e a cadeia de versoes.
 */
@Service
public class BillMaintenanceService {

    /**
     * Padrao de fabrica da exigencia de aprovacao para o que muda
     * faturamento (ADR-0039, item 8; catalogo de parametros do tenant):
     * aprovacao por outra pessoa. Vale na DECLARACAO do tipo
     * {@code BILL_*} quando o tenant ainda nao tem o tipo e nao declarou
     * sobreposicao em {@code service_type.internal_approval}.
     */
    static final boolean FACTORY_APPROVAL_FOR_BILLING_CHANGES = true;

    /** Valores recalculados pelo motor (P2.5) para a nova versao da conta. */
    public record RectifiedValues(Integer waterConsumption, Integer sewerVolume,
                                  BigDecimal waterValue, BigDecimal sewerValue, BigDecimal fixedPartValue,
                                  BigDecimal debitsValue, BigDecimal creditsValue, BigDecimal taxesValue) {
        public BigDecimal total() {
            return waterValue.add(sewerValue).add(fixedPartValue)
                    .add(debitsValue).subtract(creditsValue).subtract(taxesValue);
        }
    }

    /**
     * Resultado de uma operacao: aplicada, ou aguardando aprovacao da OS.
     * {@code resultConsumptionRecordId} e a nova versao do consumo ligada a
     * conta nova (DAD-14), quando a retificacao retificou o consumo;
     * {@code nfageDisposition} e o evento fiscal decidido para a NFAg-e
     * viva (vocabulario de {@link NfageDisposition}), quando havia uma.
     */
    public record MaintenanceResult(UUID operationRecordId, UUID serviceOrderId, UUID resultBillId,
                                    boolean applied, String status,
                                    UUID resultConsumptionRecordId, String nfageDisposition) {
    }

    /** Vinculos a migrar da conta cancelada (fatos de quem chama: P3.1/P3.24/fatura agrupadora). */
    public record LinkedFacts(List<UUID> paymentIds, UUID groupedInvoiceItemId, UUID autoDebitMandateId) {
        public LinkedFacts {
            paymentIds = paymentIds == null ? List.of() : List.copyOf(paymentIds);
        }

        /** Ha algum vinculo a migrar? (Quem tem o metodo e este record -- correcao G1 da revisao.) */
        public boolean hasAnythingToMigrate() {
            return !paymentIds.isEmpty() || groupedInvoiceItemId != null || autoDebitMandateId != null;
        }

        public static LinkedFacts none() {
            return new LinkedFacts(List.of(), null, null);
        }
    }

    private final BillRepository bills;
    private final BillStatusEventRepository statusEvents;
    private final BillFieldChangeRepository fieldChanges;
    private final BillReviewRepository reviews;
    private final BillReviewReasonRepository reviewReasons;
    private final BillCancelReasonRepository cancelReasons;
    private final BillMaintenanceOperationRepository operations;
    private final BillDebitRepository billDebits;
    private final BillCreditRepository billCredits;
    private final CreditReceivableRepository creditReceivables;
    private final ReadingAnalysisDecisionRepository analysisDecisions;
    private final ServiceOrderGateway orders;
    private final ConsumptionLinkGateway consumptionLinks;
    private final NfageDispositionGateway nfageEvents;
    private final AnalysisQueueSource queueSource;
    private final AnalysisQueueEffects queueEffects;

    public BillMaintenanceService(BillRepository bills, BillStatusEventRepository statusEvents,
                                  BillFieldChangeRepository fieldChanges, BillReviewRepository reviews,
                                  BillReviewReasonRepository reviewReasons,
                                  BillCancelReasonRepository cancelReasons,
                                  BillMaintenanceOperationRepository operations,
                                  BillDebitRepository billDebits, BillCreditRepository billCredits,
                                  CreditReceivableRepository creditReceivables,
                                  ReadingAnalysisDecisionRepository analysisDecisions,
                                  ServiceOrderGateway orders, ConsumptionLinkGateway consumptionLinks,
                                  NfageDispositionGateway nfageEvents, AnalysisQueueSource queueSource,
                                  AnalysisQueueEffects queueEffects) {
        this.bills = bills;
        this.statusEvents = statusEvents;
        this.fieldChanges = fieldChanges;
        this.reviews = reviews;
        this.reviewReasons = reviewReasons;
        this.cancelReasons = cancelReasons;
        this.operations = operations;
        this.billDebits = billDebits;
        this.billCredits = billCredits;
        this.creditReceivables = creditReceivables;
        this.analysisDecisions = analysisDecisions;
        this.orders = orders;
        this.consumptionLinks = consumptionLinks;
        this.nfageEvents = nfageEvents;
        this.queueSource = queueSource;
        this.queueEffects = queueEffects;
    }

    // ------------------------------------------------------------------
    // Retificacao (leitura, consumo, servicos, cadastro, retirar agua/
    // esgoto, credito avulso, pagamento a confirmar -- BillOperation)
    // ------------------------------------------------------------------

    /**
     * Retifica a conta gerando a nova versao. Quando a operacao e de
     * leitura/consumo (ou o chamador informa a versao vigente do consumo),
     * a nova versao do consumo nasce ligada a conta nova pela P2.5
     * (DAD-14, {@link ConsumptionLinkGateway}); retificar leitura/consumo
     * sem informar o consumo e recusado -- sem ele, o consumo ficaria
     * ligado a conta morta.
     */
    @Transactional
    public MaintenanceResult rectify(UUID tenantId, UUID billId, BillOperation operation, RectifiedValues values,
                                     UUID consumptionRecordId, UUID protocolId, String note, UUID actorId,
                                     UUID approverId, String idempotencyKey, MaintenanceParameters parameters,
                                     LocalDate today) {
        requireNote(note);
        Objects.requireNonNull(values, "values");
        if (operation == null || operation.orderType() != BillOrderType.BILL_RECTIFICATION) {
            throw new IllegalArgumentException("Operacao nao e retificacao: " + operation);
        }
        if ((operation == BillOperation.RECTIFY_READING || operation == BillOperation.RECTIFY_CONSUMPTION)
                && consumptionRecordId == null) {
            throw new IllegalArgumentException("Retificar " + operation
                    + " exige a versao vigente do consumo (consumptionRecordId): sem ela a nova versao "
                    + "do consumo nao nasce ligada a conta nova (DAD-14)");
        }
        Map<String, Object> request = baseRequest(operation.name(), billId, protocolId, note);
        request.put("values", valuesMap(values));
        request.put("consumptionRecordId", text(consumptionRecordId));
        IdemGate gate = idemGate(tenantId, idempotencyKey, request);
        if (gate.replay() != null) {
            return gate.replay();
        }
        BillEntity current = loadBill(tenantId, billId);
        prepareRecord(gate.record(), operation, current);
        // Trava de limite ANTES de criar OS/versao: retificacao barrada nao deixa rastro de cobranca.
        RectificationLimitRule.check(parameters, current.getTotalValue(), values.total(),
                againstUserCount(tenantId, current.getUnitId(), today));
        boolean againstUser = RectificationLimitRule.direction(current.getTotalValue(), values.total())
                == RectificationLimitRule.Direction.AGAINST_USER;

        AppliedVersion[] applied = new AppliedVersion[1];
        OrderGate order = ensureOrder(gate.record(), operation, operation.orderType(), protocolId, actorId,
                approverId, current.getUnitId(), parameters,
                () -> applied[0] = applyRectification(gate.record(), current, operation, values,
                        consumptionRecordId, gate.record().getServiceOrderId(), protocolId, note, actorId));
        if (!order.executed()) {
            return pending(gate.record(), order.orderId());
        }
        Objects.requireNonNull(applied[0], "execute da OS " + order.orderId()
                + " nao aplicou o efeito de manutencao (handler nao invocado)");
        completeOperation(gate.record(), tenantId, operation, current, applied[0].newBillId(), order.orderId(),
                againstUser);
        return new MaintenanceResult(gate.record().getId(), order.orderId(), applied[0].newBillId(), true,
                "EXECUTED", applied[0].newConsumptionRecordId(), applied[0].nfageDisposition());
    }

    /** Aplicacao da retificacao -- roda DENTRO do execute da OS, pelo handler de efeito. */
    private AppliedVersion applyRectification(BillMaintenanceOperationEntity record, BillEntity current,
                                              BillOperation operation, RectifiedValues values,
                                              UUID consumptionRecordId, UUID orderId, UUID protocolId,
                                              String note, UUID actorId) {
        BillVersionFactory.VersionedBill versioned = BillVersionFactory.nextVersion(
                current, BillStatus.RECTIFIED, orderId, protocolId);
        BillEntity next = versioned.newVersion();
        applyValues(next, values);
        next.setCompleted(true);
        bills.save(next);
        recordTrail(current.getTenantId(), current, next, orderId, protocolId, null, null, note, actorId);
        supersede(current, versioned.supersededStatus());
        bills.save(current);
        UUID newConsumptionId = null;
        if (consumptionRecordId != null) {
            // DAD-14: a nova versao do consumo nasce ligada a conta nova (mecanismo da P2.5).
            newConsumptionId = consumptionLinks.rectifyToBill(current.getTenantId(), consumptionRecordId,
                    next.getId(), next.getReferenceMonth(), actorId);
        }
        // Disposicao fiscal decidida aqui (P2.6): com NFAg-e viva e conta nova, SUBSTITUTION --
        // o evento em si e registrado pelo emissor do documento substituto (ver NfageDispositionGateway).
        String disposition = NfageDisposition.eventType(
                NfageDisposition.decide(current.getFiscalDocumentId(), operation, next.getId()));
        return new AppliedVersion(next.getId(), newConsumptionId, disposition);
    }

    // ------------------------------------------------------------------
    // Alteracao de vencimento
    // ------------------------------------------------------------------

    @Transactional
    public MaintenanceResult changeDueDate(UUID tenantId, UUID billId, LocalDate newDueDate,
                                           UUID protocolId, String note, UUID actorId, UUID approverId,
                                           String idempotencyKey, MaintenanceParameters parameters, LocalDate today) {
        requireNote(note);
        Objects.requireNonNull(newDueDate, "newDueDate");
        Map<String, Object> request = baseRequest(BillOperation.CHANGE_DUE_DATE.name(), billId, protocolId, note);
        request.put("newDueDate", newDueDate.toString());
        IdemGate gate = idemGate(tenantId, idempotencyKey, request);
        if (gate.replay() != null) {
            return gate.replay();
        }
        BillEntity current = loadBill(tenantId, billId);
        prepareRecord(gate.record(), BillOperation.CHANGE_DUE_DATE, current);
        DueDateChangeRule.check(parameters, dueChangeCount(tenantId, current, today),
                current.getDueDate(), newDueDate, today);
        AppliedVersion[] applied = new AppliedVersion[1];
        OrderGate order = ensureOrder(gate.record(), BillOperation.CHANGE_DUE_DATE,
                BillOrderType.BILL_DUE_DATE_CHANGE, protocolId, actorId, approverId, current.getUnitId(),
                parameters, () -> applied[0] = applyDueDateChange(current, newDueDate,
                        gate.record().getServiceOrderId(), protocolId, note, actorId));
        if (!order.executed()) {
            return pending(gate.record(), order.orderId());
        }
        Objects.requireNonNull(applied[0], "execute da OS " + order.orderId()
                + " nao aplicou o efeito de manutencao (handler nao invocado)");
        completeOperation(gate.record(), tenantId, BillOperation.CHANGE_DUE_DATE, current,
                applied[0].newBillId(), order.orderId(), false);
        return new MaintenanceResult(gate.record().getId(), order.orderId(), applied[0].newBillId(), true,
                "EXECUTED", null, null);
    }

    /** Aplicacao da alteracao de vencimento -- roda DENTRO do execute da OS. */
    private AppliedVersion applyDueDateChange(BillEntity current, LocalDate newDueDate, UUID orderId,
                                              UUID protocolId, String note, UUID actorId) {
        BillStatus sameStatus = BillStatus.valueOf(current.getStatus());
        BillVersionFactory.VersionedBill versioned = BillVersionFactory.nextVersion(
                current, sameStatus, orderId, protocolId);
        BillEntity next = versioned.newVersion();
        next.setRectificationCount(current.getRectificationCount());
        next.setDueDate(newDueDate);
        next.setDueChangeCount(current.getDueChangeCount() + 1);
        if (next.getOriginalDueDate() == null) {
            next.setOriginalDueDate(current.getDueDate());
        }
        bills.save(next);
        recordTrail(current.getTenantId(), current, next, orderId, protocolId, null, null, note, actorId);
        supersede(current, versioned.supersededStatus());
        bills.save(current);
        return new AppliedVersion(next.getId(), null, null);
    }

    // ------------------------------------------------------------------
    // Revisao: abrir e fechar
    // ------------------------------------------------------------------

    @Transactional
    public MaintenanceResult openReview(UUID tenantId, UUID billId, UUID reviewReasonId,
                                        UUID protocolId, String note, UUID actorId, UUID approverId,
                                        String idempotencyKey, MaintenanceParameters parameters, LocalDate today) {
        requireNote(note);
        Objects.requireNonNull(reviewReasonId, "reviewReasonId");
        Map<String, Object> request = baseRequest(BillOperation.OPEN_REVIEW.name(), billId, protocolId, note);
        request.put("reviewReasonId", String.valueOf(reviewReasonId));
        IdemGate gate = idemGate(tenantId, idempotencyKey, request);
        if (gate.replay() != null) {
            return gate.replay();
        }
        BillEntity current = loadBill(tenantId, billId);
        prepareRecord(gate.record(), BillOperation.OPEN_REVIEW, current);
        BillReviewReasonEntity reason = reviewReasons.findByTenantIdAndId(tenantId, reviewReasonId)
                .filter(BillReviewReasonEntity::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Motivo de revisao inexistente ou inativo no catalogo: " + reviewReasonId));
        if (reviews.findByTenantIdAndBillIdAndStatus(tenantId, billId, "OPEN").isPresent()) {
            throw new IllegalStateException("Conta ja tem revisao aberta");
        }
        OrderGate order = ensureOrder(gate.record(), BillOperation.OPEN_REVIEW, BillOrderType.BILL_REVIEW,
                protocolId, actorId, approverId, current.getUnitId(), parameters,
                () -> applyReviewOpening(current, reason, gate.record().getServiceOrderId(), protocolId, note,
                        actorId, parameters, today));
        if (!order.executed()) {
            return pending(gate.record(), order.orderId());
        }
        completeOperation(gate.record(), tenantId, BillOperation.OPEN_REVIEW, current, billId,
                order.orderId(), false);
        return new MaintenanceResult(gate.record().getId(), order.orderId(), billId, true, "EXECUTED",
                null, null);
    }

    /**
     * Aplicacao da abertura de revisao -- roda DENTRO do execute da OS.
     * A revisao e estado da conta vigente, nao nova fatura: abre a linha em
     * {@code bill_reviews} e marca motivo/OS/protocolo na propria conta
     * (sem nova versao -- declarado no RELATORIO-P2.20, correcao M2).
     */
    private void applyReviewOpening(BillEntity current, BillReviewReasonEntity reason, UUID orderId,
                                    UUID protocolId, String note, UUID actorId,
                                    MaintenanceParameters parameters, LocalDate today) {
        BillReviewEntity review = new BillReviewEntity();
        review.setTenantId(current.getTenantId());
        review.setBillId(current.getId());
        review.setBillReferenceMonth(current.getReferenceMonth());
        review.setReviewReasonId(reason.getId());
        review.setServiceOrderId(orderId);
        review.setProtocolId(protocolId);
        review.setStatus("OPEN");
        review.setOpenedOn(today);
        review.setDeadlineOn(today.plusDays(parameters.reviewMaxDays()));
        review.setNote(note);
        review.setCreatedBy(actorId);
        review.setUpdatedBy(actorId);
        reviews.save(review);
        current.setReviewReasonId(reason.getId());
        current.setServiceOrderId(orderId);
        current.setProtocolId(protocolId);
        bills.save(current);
        recordEvent(current.getTenantId(), current, current.getStatus(), current.getStatus(), reason.getId(),
                null, orderId, protocolId, note, actorId);
    }

    @Transactional
    public MaintenanceResult closeReview(UUID tenantId, UUID billId, String note, UUID actorId,
                                         String idempotencyKey, LocalDate today) {
        requireNote(note);
        Map<String, Object> request = baseRequest(BillOperation.CLOSE_REVIEW.name(), billId, null, note);
        IdemGate gate = idemGate(tenantId, idempotencyKey, request);
        if (gate.replay() != null) {
            return gate.replay();
        }
        BillEntity current = loadBill(tenantId, billId);
        prepareRecord(gate.record(), BillOperation.CLOSE_REVIEW, current);
        BillReviewEntity review = reviews.findByTenantIdAndBillIdAndStatus(tenantId, billId, "OPEN")
                .orElseThrow(() -> new IllegalStateException("Conta sem revisao aberta para fechar"));
        review.setStatus("CLOSED");
        review.setClosedOn(today);
        review.setUpdatedBy(actorId);
        reviews.save(review);
        current.setReviewedOn(today);
        bills.save(current);
        recordEvent(tenantId, current, current.getStatus(), current.getStatus(), review.getReviewReasonId(),
                null, review.getServiceOrderId(), review.getProtocolId(), note, actorId);
        completeOperation(gate.record(), tenantId, BillOperation.CLOSE_REVIEW, current, billId,
                review.getServiceOrderId(), false);
        return new MaintenanceResult(gate.record().getId(), review.getServiceOrderId(), billId, true,
                "EXECUTED", null, null);
    }

    /** Estado da revisao da conta para a regra de suspensao (cobranca, negativacao, corte, debito). */
    @Transactional(readOnly = true)
    public ReviewSuspensionRule.ReviewState reviewState(UUID tenantId, UUID billId) {
        return reviews.findByTenantIdAndBillIdAndStatus(tenantId, billId, "OPEN")
                .map(r -> ReviewSuspensionRule.ReviewState.openSince(r.getOpenedOn()))
                .orElseGet(ReviewSuspensionRule.ReviewState::none);
    }

    // ------------------------------------------------------------------
    // Cancelamento
    // ------------------------------------------------------------------

    /**
     * Cancela a conta, com ou sem substituicao:
     * <ul>
     *   <li><b>sem substituicao</b> ({@code replacementValues == null}): a
     *       conta vai a CANCELLED, os creditos realizados voltam ao saldo e
     *       os debitos fora do motivo voltam a ser cobrados na proxima
     *       conta (plano devolvido; a recobranca em si e lancamento da
     *       proxima emissao). Os vinculos migram para a proxima emissao
     *       ({@link CancellationSettlement.MigrationPlan#toNextEmission()});</li>
     *   <li><b>com substituicao</b>: nasce a nova versao da conta com os
     *       valores dados, a anterior sai por CANCELLED_BY_RECTIFICATION e
     *       os vinculos migram para a conta nova -- o plano aponta para o
     *       id DELA, nunca para a conta cancelada (correcao A1).</li>
     * </ul>
     * Com NFAg-e viva: sem substituicao, o evento CANCELLATION e registrado
     * no modulo fiscal ({@link NfageDispositionGateway}); com substituicao,
     * a disposicao e SUBSTITUTION e o evento nasce na emissao do documento
     * substituto (ver a porta).
     */
    @Transactional
    public CancellationOutcome cancel(UUID tenantId, UUID billId, UUID cancelReasonId, LinkedFacts linked,
                                      RectifiedValues replacementValues, UUID protocolId, String note,
                                      UUID actorId, UUID approverId, String idempotencyKey,
                                      MaintenanceParameters parameters, LocalDate today) {
        requireNote(note);
        Objects.requireNonNull(cancelReasonId, "cancelReasonId");
        Objects.requireNonNull(linked, "linked");
        Map<String, Object> request = baseRequest(BillOperation.CANCEL.name(), billId, protocolId, note);
        request.put("cancelReasonId", String.valueOf(cancelReasonId));
        request.put("linked", linkedMap(linked));
        request.put("replacementValues", replacementValues == null ? null : valuesMap(replacementValues));
        IdemGate gate = idemGate(tenantId, idempotencyKey, request);
        if (gate.replay() != null) {
            BillMaintenanceOperationEntity rec = gate.record();
            return new CancellationOutcome(rec.getId(), rec.getServiceOrderId(), rec.getResultBillId(), true,
                    "EXECUTED", null, null, null, null);
        }
        BillEntity current = loadBill(tenantId, billId);
        prepareRecord(gate.record(), BillOperation.CANCEL, current);
        BillCancelReasonEntity reason = cancelReasons.findByTenantIdAndId(tenantId, cancelReasonId)
                .filter(BillCancelReasonEntity::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Motivo de cancelamento inexistente ou inativo no catalogo: " + cancelReasonId));
        AppliedCancellation[] applied = new AppliedCancellation[1];
        OrderGate order = ensureOrder(gate.record(), BillOperation.CANCEL, BillOrderType.BILL_CANCELLATION,
                protocolId, actorId, approverId, current.getUnitId(), parameters,
                () -> applied[0] = applyCancellation(current, reason, linked, replacementValues,
                        gate.record().getServiceOrderId(), protocolId, note, actorId));
        if (!order.executed()) {
            BillMaintenanceOperationEntity rec = gate.record();
            rec.setStatus("AWAITING_APPROVAL");
            rec.setServiceOrderId(order.orderId());
            operations.save(rec);
            return new CancellationOutcome(rec.getId(), order.orderId(), null, false, "AWAITING_APPROVAL",
                    null, null, null, null);
        }
        Objects.requireNonNull(applied[0], "execute da OS " + order.orderId()
                + " nao aplicou o efeito de manutencao (handler nao invocado)");
        completeOperation(gate.record(), tenantId, BillOperation.CANCEL, current, applied[0].resultBillId(),
                order.orderId(), false);
        return new CancellationOutcome(gate.record().getId(), order.orderId(), applied[0].resultBillId(), true,
                "EXECUTED", applied[0].settlement(), applied[0].migration(), applied[0].nfageDisposition(),
                applied[0].nfageEventId());
    }

    /** Aplicacao do cancelamento -- roda DENTRO do execute da OS, pelo handler de efeito. */
    private AppliedCancellation applyCancellation(BillEntity current, BillCancelReasonEntity reason,
                                                  LinkedFacts linked, RectifiedValues replacementValues,
                                                  UUID orderId, UUID protocolId, String note, UUID actorId) {
        UUID tenantId = current.getTenantId();
        UUID billId = current.getId();
        Set<String> covered = new LinkedHashSet<>();
        for (JsonNode code : Json.readTree(reason.getCoveredDebitTypeCodes())) {
            covered.add(code.asText());
        }
        List<BillCreditEntity> creditLines = billCredits.findByTenantIdAndBillId(tenantId, billId);
        List<CancellationSettlement.ChargedDebit> debits = billDebits.findByTenantIdAndBillId(tenantId, billId)
                .stream()
                .map(d -> new CancellationSettlement.ChargedDebit(d.getReceivableId(), d.getDebitTypeCode(),
                        d.getAmount()))
                .toList();
        CancellationSettlement.Settlement settlement = CancellationSettlement.settle(
                creditLines.stream().map(BillCreditEntity::getAppliedAmount).toList(), debits, covered);
        // Creditos realizados voltam ao saldo (CDC, art. 42 -- travado).
        for (BillCreditEntity line : creditLines) {
            CreditReceivableEntity receivable = creditReceivables.findByTenantIdAndId(tenantId,
                            line.getReceivableId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Credito realizado sem recebivel para devolver ao saldo: " + line.getReceivableId()));
            receivable.setResidualAmount(receivable.getResidualAmount().add(line.getAppliedAmount()));
            receivable.setStatus("NORMAL");
            receivable.setUpdatedBy(actorId);
            creditReceivables.save(receivable);
        }
        if (replacementValues != null) {
            // Com substituicao: a conta nova nasce cobrando e a anterior sai por retificacao.
            BillVersionFactory.VersionedBill versioned = BillVersionFactory.nextVersion(
                    current, BillStatus.RECTIFIED, orderId, protocolId);
            BillEntity next = versioned.newVersion();
            applyValues(next, replacementValues);
            next.setCancelReasonId(reason.getId());
            next.setCompleted(true);
            bills.save(next);
            recordTrail(tenantId, current, next, orderId, protocolId, null, reason.getId(), note, actorId);
            supersede(current, versioned.supersededStatus());
            bills.save(current);
            CancellationSettlement.MigrationPlan migration = linked.hasAnythingToMigrate()
                    ? new CancellationSettlement.MigrationPlan(next.getId(), linked.paymentIds(),
                            linked.groupedInvoiceItemId(), linked.autoDebitMandateId())
                    : null;
            String disposition = NfageDisposition.eventType(
                    NfageDisposition.decide(current.getFiscalDocumentId(), BillOperation.CANCEL, next.getId()));
            return new AppliedCancellation(settlement, migration, disposition, null, next.getId());
        }
        // Sem substituicao: a propria conta vai a CANCELLED.
        String previousStatus = current.getStatus();
        current.setStatus(BillStatus.CANCELLED.name());
        current.setCancelReasonId(reason.getId());
        current.setServiceOrderId(orderId);
        current.setProtocolId(protocolId);
        bills.save(current);
        recordEvent(tenantId, current, previousStatus, BillStatus.CANCELLED.name(), null, reason.getId(),
                orderId, protocolId, note, actorId);
        CancellationSettlement.MigrationPlan migration = linked.hasAnythingToMigrate()
                ? new CancellationSettlement.MigrationPlan(null, linked.paymentIds(),
                        linked.groupedInvoiceItemId(), linked.autoDebitMandateId())
                : null;
        NfageDisposition.Disposition disposition = NfageDisposition.decide(
                current.getFiscalDocumentId(), BillOperation.CANCEL, null);
        UUID nfageEventId = null;
        if (disposition == NfageDisposition.Disposition.CANCELLATION) {
            nfageEventId = nfageEvents.registerCancellation(tenantId, current.getFiscalDocumentId(), note,
                    actorId);
        }
        return new AppliedCancellation(settlement, migration, NfageDisposition.eventType(disposition),
                nfageEventId, billId);
    }

    public record CancellationOutcome(UUID operationRecordId, UUID serviceOrderId, UUID resultBillId,
                                      boolean applied, String status,
                                      CancellationSettlement.Settlement settlement,
                                      CancellationSettlement.MigrationPlan migration,
                                      String nfageDisposition, UUID nfageEventId) {
    }

    /** Resultado interno da aplicacao de uma operacao que versiona a conta. */
    private record AppliedVersion(UUID newBillId, UUID newConsumptionRecordId, String nfageDisposition) {
    }

    /** Resultado interno da aplicacao do cancelamento. */
    private record AppliedCancellation(CancellationSettlement.Settlement settlement,
                                       CancellationSettlement.MigrationPlan migration,
                                       String nfageDisposition, UUID nfageEventId, UUID resultBillId) {
    }

    // ------------------------------------------------------------------
    // Fila de analise (JOR-09): consulta e acoes em lote
    // ------------------------------------------------------------------

    /**
     * Os itens da fila: monta os {@link AnalysisQueueRule.QueueItem} das
     * linhas da {@link AnalysisQueueSource} (leitura/conta retida com
     * media, ultimos meses, foto e ocorrencia) -- a consulta que faltava
     * (correcao A3 da revisao).
     */
    @Transactional(readOnly = true)
    public List<AnalysisQueueRule.QueueItem> queueItems(UUID tenantId) {
        List<AnalysisQueueRule.QueueItem> items = new ArrayList<>();
        for (AnalysisQueueSource.HeldReading row : queueSource.heldReadings(tenantId)) {
            items.add(new AnalysisQueueRule.QueueItem(row.readingId(), row.billId(), row.unitId(),
                    row.currentConsumption(), row.averageConsumption(), row.lastMonthsConsumption(),
                    row.photoRef(), row.occurrenceCode(), row.holdReason(), row.rereadingOrderTypeCode()));
        }
        return List.copyOf(items);
    }

    /**
     * Aplica uma acao em lote da fila. Efeitos alem do registro da decisao:
     * RELEASE destrava a leitura/conta para o faturamento
     * ({@link AnalysisQueueEffects}); REQUEST_REREADING cria a OS do tipo
     * que a ocorrencia declara no catalogo de anormalidades
     * ({@link ServiceOrderGateway#createSubmittedOrder}); OPEN_REVIEW abre
     * a revisao de verdade; KEEP_HELD e, por natureza, so o registro.
     */
    @Transactional
    public List<ReadingAnalysisDecisionEntity> applyQueueAction(UUID tenantId,
                                                                List<AnalysisQueueRule.QueueItem> items,
                                                                AnalysisQueueRule.QueueAction action,
                                                                UUID reviewReasonId, String note, UUID actorId,
                                                                MaintenanceParameters parameters,
                                                                LocalDate today) {
        // Validacao do lote ANTES de qualquer item (correcao M3): abrir revisao
        // sem motivo nao pode estourar no meio do lote, depois de efeitos parciais.
        if (action == AnalysisQueueRule.QueueAction.OPEN_REVIEW) {
            Objects.requireNonNull(reviewReasonId,
                    "Abrir BILL_REVIEW pela fila exige o motivo de revisao (reviewReasonId)");
        }
        List<AnalysisQueueRule.PlannedAction> planned = AnalysisQueueRule.plan(items, action);
        List<ReadingAnalysisDecisionEntity> saved = new ArrayList<>();
        for (AnalysisQueueRule.PlannedAction p : planned) {
            UUID orderId = null;
            switch (action) {
                case RELEASE -> queueEffects.releaseForBilling(tenantId, p.item().readingId(),
                        p.item().billId(), actorId);
                case KEEP_HELD -> {
                    // Manter retido e so a decisao: nenhum fato muda.
                }
                case OPEN_REVIEW -> {
                    MaintenanceResult r = openReview(tenantId, p.item().billId(), reviewReasonId, null,
                            note == null ? "Revisao aberta pela fila de analise (JOR-09)" : note,
                            actorId, null, null, parameters, today);
                    orderId = r.serviceOrderId();
                }
                case REQUEST_REREADING -> orderId = orders.createSubmittedOrder(
                        p.item().rereadingOrderTypeCode(), p.item().unitId(), actorId);
            }
            ReadingAnalysisDecisionEntity decision = new ReadingAnalysisDecisionEntity();
            decision.setTenantId(tenantId);
            decision.setReadingId(p.item().readingId());
            decision.setBillId(p.item().billId());
            decision.setUnitId(p.item().unitId());
            decision.setAction(action.name());
            decision.setServiceOrderId(orderId);
            decision.setNote(note);
            decision.setCreatedBy(actorId);
            decision.setUpdatedBy(actorId);
            saved.add(analysisDecisions.save(decision));
        }
        return List.copyOf(saved);
    }

    // ------------------------------------------------------------------
    // Nucleo comum: OS, idempotencia, trilha
    // ------------------------------------------------------------------

    private record OrderGate(UUID orderId, boolean executed) {
    }

    /**
     * Garante a OS da operacao e, quando o fluxo fecha, a executa -- a
     * aplicacao da manutencao acontece dentro do {@code execute}, pelo
     * handler de efeito ({@code effect}).
     *
     * <p><b>Aprovacao contra o motor real (correcao G3):</b> o motor decide
     * a aprovacao pelo {@code requires_approval} declarado no tipo e nunca
     * le {@code service_type.internal_approval}. A traducao desta camada:
     * <ul>
     *   <li>tipo <b>ausente</b>: a exigencia efetiva (sobreposicao do tenant
     *       sobre o padrao de fabrica de {@link #FACTORY_APPROVAL_FOR_BILLING_CHANGES})
     *       e gravada na declaracao do tipo via {@code ensureType};</li>
     *   <li>tipo <b>existente</b>: a exigencia efetiva tem de ser igual a
     *       fabrica declarada; divergencia e erro explicito (o motor nao
     *       redeclara tipo, e chamar {@code approve}/{@code execute} fora
     *       do estado que a fabrica produz seria recusado por ele);</li>
     *   <li>exigida e sem aprovador: a OS fica submetida (PENDING_APPROVAL)
     *       e a operacao, AWAITING_APPROVAL, sem aplicar nada; a retomada
     *       com a mesma chave aprova e executa a OS ja criada.</li>
     * </ul>
     */
    private OrderGate ensureOrder(BillMaintenanceOperationEntity record, BillOperation operation,
                                  BillOrderType type, UUID protocolId, UUID actorId, UUID approverId,
                                  UUID unitId, MaintenanceParameters parameters,
                                  ServiceOrderGateway.MaintenanceEffect effect) {
        if (record.getServiceOrderId() != null) {
            // Retomada de operacao pendente de aprovacao com a mesma chave:
            // a OS ja foi criada e submetida; aprovar (o motor confere
            // aprovador != autor) e executar, aplicando o efeito agora.
            if (approverId == null) {
                return new OrderGate(record.getServiceOrderId(), false);
            }
            orders.approve(record.getServiceOrderId(), approverId);
            orders.execute(record.getServiceOrderId(), approverId, effect);
            return new OrderGate(record.getServiceOrderId(), true);
        }
        ServiceOrderGateway.TypeInfo info = orders.findType(type);
        boolean approvalRequired;
        if (info == null) {
            approvalRequired = parameters.approvalRequired(type, FACTORY_APPROVAL_FOR_BILLING_CHANGES);
            info = orders.ensureType(type, approvalRequired);
            if (info == null || !info.active()) {
                throw new IllegalStateException("Tipo de OS " + type.code()
                        + " nao declarado ou inativo no tenant (catalogo service_types da P1.31)");
            }
        } else {
            if (!info.active()) {
                throw new IllegalStateException("Tipo de OS " + type.code()
                        + " inativo no tenant (catalogo service_types da P1.31)");
            }
            approvalRequired = parameters.approvalRequired(type, info.requiresApproval());
            if (approvalRequired != info.requiresApproval()) {
                throw new IllegalStateException("service_type.internal_approval exige "
                        + (approvalRequired ? "REQUIRED" : "NONE") + " para " + type.code()
                        + ", mas o tipo esta declarado no tenant com requires_approval="
                        + info.requiresApproval() + ": o motor da P1.31 decide a aprovacao pela fabrica "
                        + "declarada e nao redeclara tipo existente -- alinhe a declaracao do tipo no "
                        + "catalogo do tenant ao parametro");
            }
        }
        ServiceOrderEnums.Origin origin = protocolId != null
                ? ServiceOrderEnums.Origin.EXTERNAL : ServiceOrderEnums.Origin.INTERNAL;
        UUID orderId = orders.createOrder(type, origin, protocolId, actorId, unitId,
                effectPayload(record, operation));
        record.setServiceOrderId(orderId);
        if (!approvalRequired) {
            orders.execute(orderId, actorId, effect);
            return new OrderGate(orderId, true);
        }
        if (approverId == null) {
            return new OrderGate(orderId, false);
        }
        if (approverId.equals(actorId)) {
            throw new IllegalArgumentException("Quem aprova a OS deve ser diferente de quem a criou (P1.31)");
        }
        orders.approve(orderId, approverId);
        orders.execute(orderId, approverId, effect);
        return new OrderGate(orderId, true);
    }

    /** Carga do efeito que o alvo da OS carrega (contrato de BillOrderType.effectSchemaJson). */
    private String effectPayload(BillMaintenanceOperationEntity record, BillOperation operation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationRecordId", String.valueOf(record.getId()));
        payload.put("operation", operation.name());
        payload.put("billId", String.valueOf(record.getBillId()));
        return Json.write(payload);
    }

    private record IdemGate(BillMaintenanceOperationEntity record, MaintenanceResult replay) {
    }

    private IdemGate idemGate(UUID tenantId, String idempotencyKey, Map<String, Object> request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // Sem chave, a operacao roda sem deduplicacao: registro transitorio com
            // chave sintetizada (o completeOperation o persiste assim mesmo).
            BillMaintenanceOperationEntity transientRecord = new BillMaintenanceOperationEntity();
            transientRecord.setId(UUID.randomUUID());
            transientRecord.setTenantId(tenantId);
            transientRecord.setIdempotencyKey("NO-KEY-" + transientRecord.getId());
            transientRecord.setRequestHash(MaintenanceIdempotency.requestHash(request));
            transientRecord.setStatus("EXECUTED");
            return new IdemGate(transientRecord, null);
        }
        String hash = MaintenanceIdempotency.requestHash(request);
        var existing = operations.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isEmpty()) {
            BillMaintenanceOperationEntity record = new BillMaintenanceOperationEntity();
            // O id nasce aqui (e nao so no @PrePersist) porque a carga de efeito
            // da OS carrega o operationRecordId antes de qualquer save.
            record.setId(UUID.randomUUID());
            record.setTenantId(tenantId);
            record.setIdempotencyKey(idempotencyKey);
            record.setRequestHash(hash);
            record.setStatus("EXECUTED");
            return new IdemGate(record, null);
        }
        BillMaintenanceOperationEntity record = existing.get();
        MaintenanceIdempotency.Decision decision = MaintenanceIdempotency.decide(record.getRequestHash(), hash);
        if (decision == MaintenanceIdempotency.Decision.CONFLICT) {
            throw new IllegalStateException("Idempotency-Key '" + idempotencyKey
                    + "' ja usada com um pedido diferente");
        }
        if ("EXECUTED".equals(record.getStatus())) {
            return new IdemGate(record, new MaintenanceResult(record.getId(), record.getServiceOrderId(),
                    record.getResultBillId(), true, "EXECUTED", null, null));
        }
        // AWAITING_APPROVAL com a mesma chave: retoma (nao e replay).
        return new IdemGate(record, null);
    }

    private MaintenanceResult pending(BillMaintenanceOperationEntity record, UUID orderId) {
        // Os chamadores preenchem operation/bill_id/unit_id antes (NOT NULL na V4_16).
        record.setStatus("AWAITING_APPROVAL");
        record.setServiceOrderId(orderId);
        operations.save(record);
        return new MaintenanceResult(record.getId(), orderId, record.getResultBillId(), false,
                "AWAITING_APPROVAL", null, null);
    }

    /** Preenche os campos NOT NULL do registro antes de a OS nascer (a carga de efeito os referencia). */
    private void prepareRecord(BillMaintenanceOperationEntity record, BillOperation operation, BillEntity current) {
        record.setOperation(operation.name());
        record.setBillId(current.getId());
        record.setUnitId(current.getUnitId());
    }

    private void completeOperation(BillMaintenanceOperationEntity record, UUID tenantId, BillOperation operation,
                                   BillEntity current, UUID resultBillId, UUID orderId, boolean againstUser) {
        record.setOperation(operation.name());
        record.setBillId(current.getId());
        record.setUnitId(current.getUnitId());
        record.setResultBillId(resultBillId);
        record.setServiceOrderId(orderId);
        record.setStatus("EXECUTED");
        record.setAgainstUser(againstUser);
        operations.save(record);
    }

    private BillEntity loadBill(UUID tenantId, UUID billId) {
        BillEntity bill = bills.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Conta inexistente: " + billId));
        if (!tenantId.equals(bill.getTenantId())) {
            throw new IllegalArgumentException("Conta inexistente neste tenant: " + billId);
        }
        return bill;
    }

    private int againstUserCount(UUID tenantId, UUID unitId, LocalDate today) {
        OffsetDateTime from = today.withDayOfYear(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = from.plusYears(1);
        return Math.toIntExact(operations
                .countByTenantIdAndUnitIdAndAgainstUserTrueAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        tenantId, unitId, "EXECUTED", from, to));
    }

    private int dueChangeCount(UUID tenantId, BillEntity current, LocalDate today) {
        // Por CONTA (correcao A6): a cadeia inteira de versoes da conta -- mesma
        // unidade e mesma referencia -- no ano civil, sobre bill_maintenance_operations.
        // due_change_count da conta e o acumulado da cadeia sem recorte anual: nao serve de base.
        List<UUID> chainIds = bills
                .findByTenantIdAndUnitIdAndReferenceMonth(tenantId, current.getUnitId(),
                        current.getReferenceMonth())
                .stream().map(BillEntity::getId).toList();
        OffsetDateTime from = today.withDayOfYear(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = from.plusYears(1);
        long recorded = operations
                .countByTenantIdAndBillIdInAndOperationAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        tenantId, chainIds, BillOperation.CHANGE_DUE_DATE.name(), "EXECUTED", from, to);
        return Math.toIntExact(recorded);
    }

    private static void supersede(BillEntity current, BillStatus supersededStatus) {
        String previous = current.getStatus();
        current.setStatus(supersededStatus.name());
        current.setPreviousStatus(previous);
    }

    private void applyValues(BillEntity next, RectifiedValues values) {
        next.setWaterConsumption(values.waterConsumption());
        next.setSewerVolume(values.sewerVolume());
        next.setWaterValue(values.waterValue());
        next.setSewerValue(values.sewerValue());
        next.setFixedPartValue(values.fixedPartValue());
        next.setDebitsValue(values.debitsValue());
        next.setCreditsValue(values.creditsValue());
        next.setTaxesValue(values.taxesValue());
        BigDecimal total = values.total();
        if (total.signum() < 0) {
            throw new IllegalArgumentException("Total recalculado nao pode ser negativo: " + total);
        }
        next.setTotalValue(total);
        next.setLateFeeCharged(false);
    }

    private void recordTrail(UUID tenantId, BillEntity previous, BillEntity next, UUID orderId, UUID protocolId,
                             UUID reviewReasonId, UUID cancelReasonId, String note, UUID actorId) {
        for (FieldChangeTrail.FieldChange change : FieldChangeTrail.diff(previous, next)) {
            BillFieldChangeEntity row = new BillFieldChangeEntity();
            row.setTenantId(tenantId);
            row.setBillId(next.getId());
            row.setBillReferenceMonth(next.getReferenceMonth());
            row.setPreviousBillId(previous.getId());
            row.setServiceOrderId(orderId);
            row.setFieldName(change.field());
            row.setPreviousValue(change.previousValue());
            row.setNewValue(change.newValue());
            row.setCreatedBy(actorId);
            row.setUpdatedBy(actorId);
            fieldChanges.save(row);
        }
        recordEvent(tenantId, next, previous.getStatus(), next.getStatus(), reviewReasonId, cancelReasonId,
                orderId, protocolId, note, actorId);
    }

    private void recordEvent(UUID tenantId, BillEntity bill, String previousStatus, String newStatus,
                             UUID reviewReasonId, UUID cancelReasonId, UUID orderId, UUID protocolId,
                             String note, UUID actorId) {
        BillStatusEventEntity event = new BillStatusEventEntity();
        event.setTenantId(tenantId);
        event.setBillId(bill.getId());
        event.setBillReferenceMonth(bill.getReferenceMonth());
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(newStatus);
        event.setReviewReasonId(reviewReasonId);
        event.setCancelReasonId(cancelReasonId);
        event.setServiceOrderId(orderId);
        event.setProtocolId(protocolId);
        event.setNote(note);
        event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setCreatedBy(actorId);
        event.setUpdatedBy(actorId);
        statusEvents.save(event);
    }

    // ------------------------------------------------------------------
    // Hash do pedido (idempotencia): a composicao INTEGRAL da operacao
    // ------------------------------------------------------------------

    private static Map<String, Object> baseRequest(String operation, UUID billId, UUID protocolId, String note) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operation", operation);
        request.put("billId", String.valueOf(billId));
        request.put("protocolId", text(protocolId));
        request.put("note", note);
        return request;
    }

    /** Todos os campos que mudam o efeito da retificacao (correcao A5: nao so o total). */
    private static Map<String, Object> valuesMap(RectifiedValues values) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("waterConsumption", values.waterConsumption() == null ? null : String.valueOf(values.waterConsumption()));
        map.put("sewerVolume", values.sewerVolume() == null ? null : String.valueOf(values.sewerVolume()));
        map.put("waterValue", decimal(values.waterValue()));
        map.put("sewerValue", decimal(values.sewerValue()));
        map.put("fixedPartValue", decimal(values.fixedPartValue()));
        map.put("debitsValue", decimal(values.debitsValue()));
        map.put("creditsValue", decimal(values.creditsValue()));
        map.put("taxesValue", decimal(values.taxesValue()));
        return map;
    }

    private static Map<String, Object> linkedMap(LinkedFacts linked) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("paymentIds", linked.paymentIds().stream().map(String::valueOf).toList());
        map.put("groupedInvoiceItemId", text(linked.groupedInvoiceItemId()));
        map.put("autoDebitMandateId", text(linked.autoDebitMandateId()));
        return map;
    }

    private static String text(UUID id) {
        return id == null ? null : String.valueOf(id);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static void requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Anotacao da operacao e obrigatoria (aceite da P2.20, DAD-16)");
        }
    }
}
