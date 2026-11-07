package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.commercial.billing.domain.BillCancelReasonEntity;
import br.com.ziipsan.commercial.billing.domain.BillCreditEntity;
import br.com.ziipsan.commercial.billing.domain.BillDebitEntity;
import br.com.ziipsan.commercial.billing.domain.BillEntity;
import br.com.ziipsan.commercial.billing.domain.BillMaintenanceOperationEntity;
import br.com.ziipsan.commercial.billing.domain.BillReviewEntity;
import br.com.ziipsan.commercial.billing.domain.BillReviewReasonEntity;
import br.com.ziipsan.commercial.billing.domain.CreditReceivableEntity;
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
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2.20 (2a rodada pos-revisao): o servico com a porta de OS falsa -- mas
 * uma porta que EXECUTA o efeito, como o adaptador real faz dentro do
 * {@code execute} do motor (o ciclo contra o motor de verdade esta em
 * {@link BillOrderEngineFlowTest}). Cobre o que a revisao cobrou: servico
 * de cancelamento/vencimento/revisao/fila, idempotencia chamando a
 * operacao DUAS vezes sobre um registro em memoria, conflito com mesmo
 * total e composicao diferente, ligacao do consumo a conta nova (DAD-14),
 * disposicao fiscal invocada e as duas direcoes da divergencia entre
 * {@code service_type.internal_approval} e a fabrica do tipo.
 */
class BillMaintenanceServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID APPROVER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000000030");
    private static final UUID CONSUMPTION = UUID.fromString("00000000-0000-0000-0000-000000000040");
    private static final UUID NEW_CONSUMPTION = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final UUID FISCAL_DOC = UUID.fromString("00000000-0000-0000-0000-000000000050");
    private static final UUID FISCAL_EVENT = UUID.fromString("00000000-0000-0000-0000-000000000051");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 2);

    private BillRepository bills;
    private BillStatusEventRepository statusEvents;
    private BillMaintenanceOperationRepository operations;
    private BillReviewRepository reviews;
    private BillReviewReasonRepository reviewReasons;
    private BillCancelReasonRepository cancelReasons;
    private BillDebitRepository billDebits;
    private BillCreditRepository billCredits;
    private CreditReceivableRepository creditReceivables;
    private ReadingAnalysisDecisionRepository analysisDecisions;
    private ServiceOrderGateway orders;
    private ConsumptionLinkGateway consumptionLinks;
    private NfageDispositionGateway nfageEvents;
    private AnalysisQueueSource queueSource;
    private AnalysisQueueEffects queueEffects;
    private BillMaintenanceService service;
    private BillEntity current;
    private final Map<String, BillMaintenanceOperationEntity> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        bills = mock(BillRepository.class);
        statusEvents = mock(BillStatusEventRepository.class);
        BillFieldChangeRepository fieldChanges = mock(BillFieldChangeRepository.class);
        reviews = mock(BillReviewRepository.class);
        reviewReasons = mock(BillReviewReasonRepository.class);
        cancelReasons = mock(BillCancelReasonRepository.class);
        operations = mock(BillMaintenanceOperationRepository.class);
        billDebits = mock(BillDebitRepository.class);
        billCredits = mock(BillCreditRepository.class);
        creditReceivables = mock(CreditReceivableRepository.class);
        analysisDecisions = mock(ReadingAnalysisDecisionRepository.class);
        orders = mock(ServiceOrderGateway.class);
        consumptionLinks = mock(ConsumptionLinkGateway.class);
        nfageEvents = mock(NfageDispositionGateway.class);
        queueSource = mock(AnalysisQueueSource.class);
        queueEffects = mock(AnalysisQueueEffects.class);
        service = new BillMaintenanceService(bills, statusEvents, fieldChanges, reviews, reviewReasons,
                cancelReasons, operations, billDebits, billCredits, creditReceivables, analysisDecisions,
                orders, consumptionLinks, nfageEvents, queueSource, queueEffects);
        current = BillVersionFactoryTest.currentBill();
        current.setTenantId(TENANT);
        when(bills.findById(current.getId())).thenReturn(Optional.of(current));
        when(bills.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(bills.findByTenantIdAndUnitIdAndReferenceMonth(TENANT, current.getUnitId(),
                current.getReferenceMonth())).thenReturn(List.of(current));
        // Registro de idempotencia EM MEMORIA: a segunda chamada le o que a primeira gravou.
        when(operations.findByTenantIdAndIdempotencyKey(eq(TENANT), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(1))));
        when(operations.save(any())).thenAnswer(inv -> {
            BillMaintenanceOperationEntity e = inv.getArgument(0);
            store.put(e.getIdempotencyKey(), e);
            return e;
        });
        when(operations.countByTenantIdAndUnitIdAndAgainstUserTrueAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any(), any(), any())).thenReturn(0L);
        when(operations.countByTenantIdAndBillIdInAndOperationAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(orders.createOrder(any(), any(), any(), any(), any(), any())).thenReturn(ORDER);
        // A porta falsa executa o efeito, como o handler do adaptador real faz no execute do motor.
        doAnswer(inv -> {
            ((ServiceOrderGateway.MaintenanceEffect) inv.getArgument(2)).apply();
            return null;
        }).when(orders).execute(any(), any(), any());
        when(analysisDecisions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static MaintenanceParameters params() {
        return MaintenanceParameters.fromValues(Map.of(
                MaintenanceParameters.RECTIFICATION_LIMITS_AGAINST_USER, "{\"maxPerUnitAndYear\": 3}",
                MaintenanceParameters.REVIEW_MAX_DAYS, "30"));
    }

    private static MaintenanceParameters paramsWith(String key, String json) {
        Map<String, String> values = new HashMap<>(Map.of(
                MaintenanceParameters.RECTIFICATION_LIMITS_AGAINST_USER, "{\"maxPerUnitAndYear\": 3}",
                MaintenanceParameters.REVIEW_MAX_DAYS, "30"));
        values.put(key, json);
        return MaintenanceParameters.fromValues(values);
    }

    private static MaintenanceParameters dueParams() {
        return MaintenanceParameters.fromValues(Map.of(
                MaintenanceParameters.DUE_CHANGE_MAX_PER_YEAR, "2",
                MaintenanceParameters.DUE_CHANGE_MIN_DAYS_BEFORE_DUE, "0",
                MaintenanceParameters.RECTIFICATION_LIMITS_AGAINST_USER, "{\"maxPerUnitAndYear\": 3}",
                MaintenanceParameters.REVIEW_MAX_DAYS, "30"));
    }

    private static BillMaintenanceService.RectifiedValues values80() {
        // 48,00 + 32,00 + 0 + 0 - 0 - 0 = 80,00 (a favor do usuario: 100,00 -> 80,00)
        return new BillMaintenanceService.RectifiedValues(8, 8,
                new BigDecimal("48.00"), new BigDecimal("32.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    private void typeIs(BillOrderType type, boolean requiresApproval) {
        when(orders.findType(type)).thenReturn(new ServiceOrderGateway.TypeInfo(true, requiresApproval));
    }

    // ------------------------------------------------------------------
    // Retificacao
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sem aprovacao exigida: retifica, gera versao RECTIFIED, anterior sai e o consumo nasce ligado a conta nova")
    void rectifyWithoutApprovalLinksConsumption() {
        typeIs(BillOrderType.BILL_RECTIFICATION, false);
        when(consumptionLinks.rectifyToBill(eq(TENANT), eq(CONSUMPTION), any(), any(), eq(ACTOR)))
                .thenReturn(NEW_CONSUMPTION);
        var result = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "leitura corrigida pelo mostrador", ACTOR, null, "key-1", params(), TODAY);
        assertThat(result.applied()).isTrue();
        assertThat(result.resultBillId()).isNotNull();
        assertThat(result.resultConsumptionRecordId()).isEqualTo(NEW_CONSUMPTION);
        assertThat(current.getStatus()).isEqualTo("CANCELLED_BY_RECTIFICATION");
        // DAD-14: a ligacao foi pedida com o id e o mes da conta NOVA.
        verify(consumptionLinks).rectifyToBill(TENANT, CONSUMPTION, result.resultBillId(),
                LocalDate.of(2026, 3, 1), ACTOR);
        verify(orders).execute(eq(ORDER), eq(ACTOR), any());
        verify(statusEvents).save(any());
    }

    @Test
    @DisplayName("retificar leitura sem a versao do consumo e recusado antes de qualquer OS")
    void rectifyReadingWithoutConsumptionRefused() {
        assertThatThrownBy(() -> service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_READING,
                values80(), null, null, "leitura corrigida", ACTOR, null, "key-1b", params(), TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DAD-14");
        verify(orders, never()).createOrder(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("com protocolo: a OS nasce EXTERNAL com o protocolId (P1.36); sem protocolo: INTERNAL")
    void originFollowsProtocol() {
        typeIs(BillOrderType.BILL_RECTIFICATION, false);
        UUID protocol = UUID.fromString("00000000-0000-0000-0000-000000000099");
        service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, protocol, "pedido do usuario", ACTOR, null, "key-2", params(), TODAY);
        verify(orders).createOrder(eq(BillOrderType.BILL_RECTIFICATION), eq(ServiceOrderEnums.Origin.EXTERNAL),
                eq(protocol), eq(ACTOR), eq(current.getUnitId()), anyString());
    }

    @Test
    @DisplayName("aprovacao exigida e sem aprovador: AWAITING_APPROVAL, nenhuma versao, nenhum evento, nenhum efeito")
    void awaitingApprovalAppliesNothing() {
        typeIs(BillOrderType.BILL_RECTIFICATION, true);
        var result = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "aguardando aprovacao", ACTOR, null, "key-3", params(), TODAY);
        assertThat(result.applied()).isFalse();
        assertThat(result.status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(current.getStatus()).isEqualTo("NORMAL");
        verify(statusEvents, never()).save(any());
        verify(orders, never()).execute(any(), any(), any());
        verify(consumptionLinks, never()).rectifyToBill(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("aprovacao exigida com aprovador diferente: aprova, executa e aplica")
    void approvalByAnotherApplies() {
        typeIs(BillOrderType.BILL_RECTIFICATION, true);
        var result = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "aprovada", ACTOR, APPROVER, "key-4", params(), TODAY);
        assertThat(result.applied()).isTrue();
        verify(orders).approve(ORDER, APPROVER);
        verify(orders).execute(eq(ORDER), eq(APPROVER), any());
    }

    @Test
    @DisplayName("retomada com a mesma chave: pendente na 1a chamada, aprovada e aplicada na 2a, uma OS so")
    void resumePendingWithApproverApplies() {
        typeIs(BillOrderType.BILL_RECTIFICATION, true);
        var first = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "aguardando", ACTOR, null, "key-resume", params(), TODAY);
        assertThat(first.applied()).isFalse();
        var second = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "aguardando", ACTOR, APPROVER, "key-resume", params(), TODAY);
        assertThat(second.applied()).isTrue();
        assertThat(second.resultBillId()).isNotNull();
        assertThat(current.getStatus()).isEqualTo("CANCELLED_BY_RECTIFICATION");
        verify(orders, times(1)).createOrder(any(), any(), any(), any(), any(), any());
        verify(orders).approve(ORDER, APPROVER);
    }

    // ------------------------------------------------------------------
    // Aprovacao x fabrica do tipo (G3, os dois sentidos da divergencia)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sobreposicao REQUIRED com fabrica livre: erro explicito, nenhuma OS (o motor recusaria o approve)")
    void overlayRequiredAgainstFreeFactoryRefused() {
        typeIs(BillOrderType.BILL_RECTIFICATION, false);
        MaintenanceParameters p = paramsWith(MaintenanceParameters.INTERNAL_APPROVAL,
                "{\"BILL_RECTIFICATION\": \"REQUIRED\"}");
        assertThatThrownBy(() -> service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION,
                values80(), CONSUMPTION, null, "x", ACTOR, APPROVER, "key-d1", p, TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service_type.internal_approval");
        verify(orders, never()).createOrder(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sobreposicao NONE com fabrica exigente: erro explicito, nenhuma OS (o motor recusaria o execute)")
    void overlayNoneAgainstRequiredFactoryRefused() {
        typeIs(BillOrderType.BILL_RECTIFICATION, true);
        MaintenanceParameters p = paramsWith(MaintenanceParameters.INTERNAL_APPROVAL,
                "{\"BILL_RECTIFICATION\": \"NONE\"}");
        assertThatThrownBy(() -> service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION,
                values80(), CONSUMPTION, null, "x", ACTOR, null, "key-d2", p, TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service_type.internal_approval");
        verify(orders, never()).createOrder(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("tipo ausente: declarado com a exigencia da sobreposicao (NONE) e a operacao executa sem aprovacao")
    void absentTypeDeclaredWithOverlay() {
        when(orders.findType(BillOrderType.BILL_RECTIFICATION)).thenReturn(null);
        when(orders.ensureType(BillOrderType.BILL_RECTIFICATION, false))
                .thenReturn(new ServiceOrderGateway.TypeInfo(true, false));
        MaintenanceParameters p = paramsWith(MaintenanceParameters.INTERNAL_APPROVAL,
                "{\"BILL_RECTIFICATION\": \"NONE\"}");
        var result = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "declarada", ACTOR, null, "key-d3", p, TODAY);
        assertThat(result.applied()).isTrue();
        verify(orders).ensureType(BillOrderType.BILL_RECTIFICATION, false);
    }

    @Test
    @DisplayName("tipo ausente e sem sobreposicao: declarado com o padrao de fabrica (aprovacao de quem muda faturamento)")
    void absentTypeDeclaredWithFactoryDefault() {
        when(orders.findType(BillOrderType.BILL_RECTIFICATION)).thenReturn(null);
        when(orders.ensureType(BillOrderType.BILL_RECTIFICATION, true))
                .thenReturn(new ServiceOrderGateway.TypeInfo(true, true));
        var result = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "declarada", ACTOR, APPROVER, "key-d4", params(), TODAY);
        assertThat(result.applied()).isTrue();
        verify(orders).ensureType(BillOrderType.BILL_RECTIFICATION, true);
    }

    // ------------------------------------------------------------------
    // Idempotencia: duas chamadas de verdade (A4) e hash integral (A5)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("mesma Idempotency-Key chamada DUAS vezes: um unico efeito (1 OS, 1 versao, mesmo resultado)")
    void idempotencyTwoCallsOneEffect() {
        typeIs(BillOrderType.BILL_RECTIFICATION, false);
        var first = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "leitura corrigida pelo mostrador", ACTOR, null, "key-twice", params(), TODAY);
        var second = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "leitura corrigida pelo mostrador", ACTOR, null, "key-twice", params(), TODAY);
        assertThat(second.applied()).isTrue();
        assertThat(second.resultBillId()).isEqualTo(first.resultBillId());
        verify(orders, times(1)).createOrder(any(), any(), any(), any(), any(), any());
        // Versao nova + anterior substituida: 2 saves na 1a chamada, nenhum na 2a.
        verify(bills, times(2)).save(any());
        verify(statusEvents, times(1)).save(any());
    }

    @Test
    @DisplayName("mesma chave, MESMO total e composicao diferente: CONFLICT, nunca replay silencioso")
    void idempotencyConflictSameTotalDifferentComposition() {
        typeIs(BillOrderType.BILL_RECTIFICATION, false);
        service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "leitura corrigida", ACTOR, null, "key-conflict", params(), TODAY);
        // 50,00 + 30,00 = 80,00: o total e o mesmo de values80, a composicao nao.
        var otherComposition = new BillMaintenanceService.RectifiedValues(9, 7,
                new BigDecimal("50.00"), new BigDecimal("30.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));
        assertThatThrownBy(() -> service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION,
                otherComposition, CONSUMPTION, null, "leitura corrigida", ACTOR, null, "key-conflict",
                params(), TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pedido diferente");
        verify(orders, times(1)).createOrder(any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Cancelamento (servico)
    // ------------------------------------------------------------------

    private BillCancelReasonEntity cancelReason(String coveredJson) {
        BillCancelReasonEntity reason = new BillCancelReasonEntity();
        reason.setId(UUID.fromString("00000000-0000-0000-0000-000000000060"));
        reason.setTenantId(TENANT);
        reason.setCoveredDebitTypeCodes(coveredJson);
        reason.setActive(true);
        when(cancelReasons.findByTenantIdAndId(TENANT, reason.getId())).thenReturn(Optional.of(reason));
        return reason;
    }

    private CreditReceivableEntity cancelFacts() {
        UUID receivable = UUID.fromString("00000000-0000-0000-0000-000000000061");
        BillCreditEntity credit = new BillCreditEntity();
        credit.setReceivableId(receivable);
        credit.setAppliedAmount(new BigDecimal("50.00"));
        when(billCredits.findByTenantIdAndBillId(TENANT, current.getId())).thenReturn(List.of(credit));
        BillDebitEntity covered = new BillDebitEntity();
        covered.setReceivableId(UUID.fromString("00000000-0000-0000-0000-000000000062"));
        covered.setDebitTypeCode("SERVICO");
        covered.setAmount(new BigDecimal("10.00"));
        BillDebitEntity notCovered = new BillDebitEntity();
        notCovered.setReceivableId(UUID.fromString("00000000-0000-0000-0000-000000000063"));
        notCovered.setDebitTypeCode("MULTA");
        notCovered.setAmount(new BigDecimal("15.00"));
        when(billDebits.findByTenantIdAndBillId(TENANT, current.getId())).thenReturn(List.of(covered, notCovered));
        CreditReceivableEntity saldo = new CreditReceivableEntity();
        saldo.setId(receivable);
        saldo.setResidualAmount(new BigDecimal("10.00"));
        saldo.setStatus("APPLIED");
        when(creditReceivables.findByTenantIdAndId(TENANT, receivable)).thenReturn(Optional.of(saldo));
        when(creditReceivables.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return saldo;
    }

    @Test
    @DisplayName("cancelamento sem substituicao: credito volta ao saldo (10+50=60, NORMAL), so MULTA recobra, plano para a proxima emissao")
    void cancelReturnsCreditsAndPlansToNextEmission() {
        BillCancelReasonEntity reason = cancelReason("[\"SERVICO\"]");
        CreditReceivableEntity saldo = cancelFacts();
        typeIs(BillOrderType.BILL_CANCELLATION, false);
        UUID mandate = UUID.fromString("00000000-0000-0000-0000-000000000064");
        var outcome = service.cancel(TENANT, current.getId(), reason.getId(),
                new BillMaintenanceService.LinkedFacts(List.of(), null, mandate), null,
                null, "cancelada a pedido", ACTOR, null, "key-c1", params(), TODAY);
        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.settlement().creditsReturnedToBalance()).isEqualByComparingTo("50.00");
        assertThat(outcome.settlement().debitsToRebill())
                .extracting(CancellationSettlement.ChargedDebit::debitTypeCode).containsExactly("MULTA");
        // A1: sem conta nova, o destino do plano e a proxima emissao -- nunca a conta cancelada.
        assertThat(outcome.migration()).isNotNull();
        assertThat(outcome.migration().newBillId()).isNull();
        assertThat(outcome.migration().toNextEmission()).isTrue();
        assertThat(outcome.migration().autoDebitMandateId()).isEqualTo(mandate);
        assertThat(current.getStatus()).isEqualTo("CANCELLED");
        verify(creditReceivables).save(saldo);
        assertThat(saldo.getResidualAmount()).isEqualByComparingTo("60.00");
        assertThat(saldo.getStatus()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("cancelamento com substituicao: plano migra para a conta NOVA, anterior sai por retificacao")
    void cancelWithReplacementMigratesToNewBill() {
        BillCancelReasonEntity reason = cancelReason("[]");
        cancelFacts();
        typeIs(BillOrderType.BILL_CANCELLATION, false);
        UUID payment = UUID.fromString("00000000-0000-0000-0000-000000000065");
        var outcome = service.cancel(TENANT, current.getId(), reason.getId(),
                new BillMaintenanceService.LinkedFacts(List.of(payment), null, null), values80(),
                null, "cancelada com substituicao", ACTOR, null, "key-c2", params(), TODAY);
        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.resultBillId()).isNotNull().isNotEqualTo(current.getId());
        assertThat(outcome.migration()).isNotNull();
        assertThat(outcome.migration().newBillId()).isEqualTo(outcome.resultBillId());
        assertThat(outcome.migration().paymentIds()).containsExactly(payment);
        assertThat(outcome.migration().toNextEmission()).isFalse();
        assertThat(current.getStatus()).isEqualTo("CANCELLED_BY_RECTIFICATION");
        // A conta nova carrega o motivo do cancelamento.
        ArgumentCaptor<BillEntity> savedBills = ArgumentCaptor.forClass(BillEntity.class);
        verify(bills, times(2)).save(savedBills.capture());
        assertThat(savedBills.getAllValues()).filteredOn(b -> b.getId().equals(outcome.resultBillId()))
                .singleElement().satisfies(b -> assertThat(b.getCancelReasonId()).isEqualTo(reason.getId()));
    }

    @Test
    @DisplayName("cancelamento com NFAg-e viva: evento CANCELLATION registrado no fiscal e devolvido no resultado")
    void cancelWithLiveNfageRegistersEvent() {
        BillCancelReasonEntity reason = cancelReason("[]");
        cancelFacts();
        typeIs(BillOrderType.BILL_CANCELLATION, false);
        current.setFiscalDocumentId(FISCAL_DOC);
        when(nfageEvents.registerCancellation(TENANT, FISCAL_DOC, "cancelada", ACTOR)).thenReturn(FISCAL_EVENT);
        var outcome = service.cancel(TENANT, current.getId(), reason.getId(),
                BillMaintenanceService.LinkedFacts.none(), null,
                null, "cancelada", ACTOR, null, "key-c3", params(), TODAY);
        assertThat(outcome.nfageDisposition()).isEqualTo("CANCELLATION");
        assertThat(outcome.nfageEventId()).isEqualTo(FISCAL_EVENT);
        verify(nfageEvents).registerCancellation(TENANT, FISCAL_DOC, "cancelada", ACTOR);
    }

    @Test
    @DisplayName("retificacao com NFAg-e viva: disposicao SUBSTITUTION decidida e devolvida (o evento nasce no emissor do substituto)")
    void rectifyWithLiveNfageDecidesSubstitution() {
        typeIs(BillOrderType.BILL_RECTIFICATION, false);
        current.setFiscalDocumentId(FISCAL_DOC);
        var result = service.rectify(TENANT, current.getId(), BillOperation.RECTIFY_CONSUMPTION, values80(),
                CONSUMPTION, null, "retificada", ACTOR, null, "key-c4", params(), TODAY);
        assertThat(result.nfageDisposition()).isEqualTo("SUBSTITUTION");
        verify(nfageEvents, never()).registerCancellation(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Vencimento (servico) -- contagem por cadeia da conta (A6)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("alteracao de vencimento: nova versao com o vencimento novo e due_change_count + 1")
    void changeDueDateApplies() {
        typeIs(BillOrderType.BILL_DUE_DATE_CHANGE, false);
        LocalDate newDue = LocalDate.of(2026, 3, 20);
        var result = service.changeDueDate(TENANT, current.getId(), newDue, null, "vencimento a pedido",
                ACTOR, null, "key-v1", dueParams(), TODAY);
        assertThat(result.applied()).isTrue();
        ArgumentCaptor<BillEntity> savedBills = ArgumentCaptor.forClass(BillEntity.class);
        verify(bills, times(2)).save(savedBills.capture());
        assertThat(savedBills.getAllValues()).filteredOn(b -> b.getId().equals(result.resultBillId()))
                .singleElement().satisfies(b -> {
                    assertThat(b.getDueDate()).isEqualTo(newDue);
                    assertThat(b.getDueChangeCount()).isEqualTo(1);
                    assertThat(b.getOriginalDueDate()).isEqualTo(LocalDate.of(2026, 3, 10));
                });
        // A contagem consultada e a da CADEIA da conta (ids das versoes), nao a da unidade.
        ArgumentCaptor<Collection<UUID>> chain = ArgumentCaptor.forClass(Collection.class);
        verify(operations).countByTenantIdAndBillIdInAndOperationAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(TENANT), chain.capture(), eq("CHANGE_DUE_DATE"), eq("EXECUTED"), any(), any());
        assertThat(chain.getValue()).containsExactly(current.getId());
    }

    @Test
    @DisplayName("alteracao de vencimento com a cadeia ja no teto do ano: recusada, nenhuma OS")
    void changeDueDateAtChainLimitRefused() {
        typeIs(BillOrderType.BILL_DUE_DATE_CHANGE, false);
        when(operations.countByTenantIdAndBillIdInAndOperationAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any(), any(), any(), any())).thenReturn(2L);
        assertThatThrownBy(() -> service.changeDueDate(TENANT, current.getId(), LocalDate.of(2026, 3, 20),
                null, "vencimento a pedido", ACTOR, null, "key-v2", dueParams(), TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("due_change_max_per_year");
        verify(orders, never()).createOrder(any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Revisao (servico)
    // ------------------------------------------------------------------

    private BillReviewReasonEntity reviewReason() {
        BillReviewReasonEntity reason = new BillReviewReasonEntity();
        reason.setId(UUID.fromString("00000000-0000-0000-0000-000000000070"));
        reason.setTenantId(TENANT);
        reason.setActive(true);
        when(reviewReasons.findByTenantIdAndId(TENANT, reason.getId())).thenReturn(Optional.of(reason));
        when(reviews.findByTenantIdAndBillIdAndStatus(TENANT, current.getId(), "OPEN"))
                .thenReturn(Optional.empty());
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return reason;
    }

    @Test
    @DisplayName("abrir revisao: linha OPEN com teto fotografado, conta marcada com motivo e OS")
    void openReviewApplies() {
        BillReviewReasonEntity reason = reviewReason();
        typeIs(BillOrderType.BILL_REVIEW, false);
        var result = service.openReview(TENANT, current.getId(), reason.getId(), null, "contestacao do usuario",
                ACTOR, null, "key-r1", params(), TODAY);
        assertThat(result.applied()).isTrue();
        ArgumentCaptor<BillReviewEntity> saved = ArgumentCaptor.forClass(BillReviewEntity.class);
        verify(reviews).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("OPEN");
        assertThat(saved.getValue().getOpenedOn()).isEqualTo(TODAY);
        assertThat(saved.getValue().getDeadlineOn()).isEqualTo(TODAY.plusDays(30));
        assertThat(saved.getValue().getServiceOrderId()).isEqualTo(ORDER);
        assertThat(current.getReviewReasonId()).isEqualTo(reason.getId());
        assertThat(current.getServiceOrderId()).isEqualTo(ORDER);
    }

    @Test
    @DisplayName("fechar revisao: linha CLOSED com a data e conta com reviewed_on")
    void closeReviewApplies() {
        BillReviewEntity open = new BillReviewEntity();
        open.setId(UUID.fromString("00000000-0000-0000-0000-000000000071"));
        open.setStatus("OPEN");
        open.setServiceOrderId(ORDER);
        when(reviews.findByTenantIdAndBillIdAndStatus(TENANT, current.getId(), "OPEN"))
                .thenReturn(Optional.of(open));
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = service.closeReview(TENANT, current.getId(), "revisao concluida", ACTOR, "key-r2", TODAY);
        assertThat(result.applied()).isTrue();
        assertThat(open.getStatus()).isEqualTo("CLOSED");
        assertThat(open.getClosedOn()).isEqualTo(TODAY);
        assertThat(current.getReviewedOn()).isEqualTo(TODAY);
    }

    // ------------------------------------------------------------------
    // Fila JOR-09 (servico)
    // ------------------------------------------------------------------

    private static final UUID READING = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID REREAD_ORDER = UUID.fromString("00000000-0000-0000-0000-000000000080");

    private AnalysisQueueRule.QueueItem queueItem() {
        return new AnalysisQueueRule.QueueItem(READING, current.getId(), current.getUnitId(), 150, 100,
                List.of(90, 100, 110), "foto://leitura/1", "CONSUMO_ALTO", "ANOMALIA", "LEITURA_RELEITURA");
    }

    @Test
    @DisplayName("consulta da fila: monta itens da fonte com media, ultimos meses, foto, ocorrencia e desvio")
    void queueItemsAssembledFromSource() {
        when(queueSource.heldReadings(TENANT)).thenReturn(List.of(new AnalysisQueueSource.HeldReading(
                READING, current.getId(), current.getUnitId(), 150, 100, List.of(90, 100, 110),
                "foto://leitura/1", "CONSUMO_ALTO", "ANOMALIA", "LEITURA_RELEITURA")));
        var items = service.queueItems(TENANT);
        assertThat(items).hasSize(1);
        var item = items.get(0);
        assertThat(item.readingId()).isEqualTo(READING);
        assertThat(item.averageConsumption()).isEqualTo(100);
        assertThat(item.lastMonthsConsumption()).containsExactly(90, 100, 110);
        assertThat(item.photoRef()).isEqualTo("foto://leitura/1");
        assertThat(item.occurrenceCode()).isEqualTo("CONSUMO_ALTO");
        assertThat(item.deviationPercent()).isEqualByComparingTo("50.00");
        assertThat(item.rereadingOrderTypeCode()).isEqualTo("LEITURA_RELEITURA");
    }

    @Test
    @DisplayName("fila RELEASE: destrava a leitura/conta para faturamento e registra a decisao")
    void queueReleaseUnlocksAndRecords() {
        var saved = service.applyQueueAction(TENANT, List.of(queueItem()),
                AnalysisQueueRule.QueueAction.RELEASE, null, "liberada", ACTOR, params(), TODAY);
        verify(queueEffects).releaseForBilling(TENANT, READING, current.getId(), ACTOR);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getAction()).isEqualTo("RELEASE");
        assertThat(saved.get(0).getServiceOrderId()).isNull();
    }

    @Test
    @DisplayName("fila KEEP_HELD: so registra a decisao (manter e, por natureza, sem efeito)")
    void queueKeepHeldOnlyRecords() {
        var saved = service.applyQueueAction(TENANT, List.of(queueItem()),
                AnalysisQueueRule.QueueAction.KEEP_HELD, null, "mantida", ACTOR, params(), TODAY);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getAction()).isEqualTo("KEEP_HELD");
        verify(queueEffects, never()).releaseForBilling(any(), any(), any(), any());
        verify(orders, never()).createSubmittedOrder(any(), any(), any());
    }

    @Test
    @DisplayName("fila OPEN_REVIEW sem motivo: recusado ANTES de qualquer item (nenhuma decisao, nenhum efeito)")
    void queueOpenReviewWithoutReasonFailsFirst() {
        assertThatThrownBy(() -> service.applyQueueAction(TENANT, List.of(queueItem()),
                AnalysisQueueRule.QueueAction.OPEN_REVIEW, null, "abrir", ACTOR, params(), TODAY))
                .isInstanceOf(NullPointerException.class);
        verify(analysisDecisions, never()).save(any());
        verify(bills, never()).save(any());
    }

    @Test
    @DisplayName("fila OPEN_REVIEW: abre a revisao de verdade e a decisao carrega a OS")
    void queueOpenReviewOpensReview() {
        BillReviewReasonEntity reason = reviewReason();
        typeIs(BillOrderType.BILL_REVIEW, false);
        var saved = service.applyQueueAction(TENANT, List.of(queueItem()),
                AnalysisQueueRule.QueueAction.OPEN_REVIEW, reason.getId(), null, ACTOR, params(), TODAY);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getAction()).isEqualTo("OPEN_REVIEW");
        assertThat(saved.get(0).getServiceOrderId()).isEqualTo(ORDER);
        verify(reviews).save(any());
    }

    @Test
    @DisplayName("fila REQUEST_REREADING: cria a OS do tipo que a ocorrencia declara e a decisao a referencia")
    void queueRequestRereadingCreatesOrder() {
        when(orders.createSubmittedOrder("LEITURA_RELEITURA", current.getUnitId(), ACTOR))
                .thenReturn(REREAD_ORDER);
        var saved = service.applyQueueAction(TENANT, List.of(queueItem()),
                AnalysisQueueRule.QueueAction.REQUEST_REREADING, null, "reler", ACTOR, params(), TODAY);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getAction()).isEqualTo("REQUEST_REREADING");
        assertThat(saved.get(0).getServiceOrderId()).isEqualTo(REREAD_ORDER);
        verify(orders).createSubmittedOrder("LEITURA_RELEITURA", current.getUnitId(), ACTOR);
    }
}
