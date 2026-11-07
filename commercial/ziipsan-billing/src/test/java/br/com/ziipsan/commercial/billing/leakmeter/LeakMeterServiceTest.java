package br.com.ziipsan.commercial.billing.leakmeter;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3.28: o servico com a porta de OS falsa que EXECUTA o efeito (como o
 * adaptador real faz dentro do execute do motor) e a porta da P2.20 falsa
 * que registra a abertura. Prova a ordem do fluxo (prazo/elegibilidade
 * antes de qualquer OS; tipo garantido antes da BILL_REVIEW; filha depois
 * da abertura aplicada), o estado pendente da P2.20 sem filha nem linha
 * (A1), as passagens de conducao da OS de campo (B1), a decisao com o
 * calculo fotografado e o ciclo pedido -> laudo da afericao. O ciclo
 * contra o MOTOR REAL vive em {@link LeakMeterEngineFlowTest}.
 */
class LeakMeterServiceTest {

    /** Porta falsa: garante tipos como "declarados", cria OS registradas e executa o efeito na hora. */
    static final class FakeGateway implements LeakMeterOrderGateway {
        final List<String> calls = new ArrayList<>();
        final Map<LeakMeterOrderType, TypeInfo> types = new HashMap<>();
        /** Quando falso, o execute registra a chamada mas NAO invoca o efeito (execute no-op do motor). */
        boolean applyEffect = true;
        UUID lastCreatedOrderId;
        UUID lastParentOrderId;
        String lastPayload;

        @Override
        public TypeInfo findType(LeakMeterOrderType type) {
            return types.get(type);
        }

        @Override
        public TypeInfo ensureType(LeakMeterOrderType type, boolean requiresApproval) {
            calls.add("ensureType:" + type.code());
            TypeInfo info = new TypeInfo(true, requiresApproval);
            types.put(type, info);
            return info;
        }

        @Override
        public UUID createOrder(LeakMeterOrderType type, ServiceOrderEnums.Origin origin, UUID protocolId,
                                UUID createdBy, UUID unitId, UUID parentOrderId, String effectPayloadJson) {
            calls.add("createOrder:" + type.code());
            lastParentOrderId = parentOrderId;
            lastPayload = effectPayloadJson;
            lastCreatedOrderId = UUID.randomUUID();
            return lastCreatedOrderId;
        }

        @Override
        public void approve(UUID orderId, UUID approverId) {
            calls.add("approve");
        }

        @Override
        public void schedule(UUID orderId, java.time.OffsetDateTime scheduledFor, UUID actorId) {
            calls.add("schedule");
        }

        @Override
        public void startExecution(UUID orderId, UUID actorId) {
            calls.add("startExecution");
        }

        @Override
        public void execute(UUID orderId, UUID actorId, LeakMeterEffect effect) {
            calls.add("execute");
            if (applyEffect) {
                effect.apply();
            }
        }
    }

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID REASON = UUID.randomUUID();
    private static final UUID METER = UUID.randomUUID();

    private LeakReviewRepository leakReviews;
    private MeterTestRepository meterTests;
    private FakeGateway gateway;
    private BillReviewPort billReviewPort;
    private LeakMeterService service;
    private final List<LeakReviewEntity> savedReviews = new ArrayList<>();
    private final List<MeterTestEntity> savedTests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        leakReviews = mock(LeakReviewRepository.class);
        meterTests = mock(MeterTestRepository.class);
        gateway = new FakeGateway();
        billReviewPort = mock(BillReviewPort.class);
        service = new LeakMeterService(leakReviews, meterTests, gateway, billReviewPort);
        doAnswer(inv -> {
            LeakReviewEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            savedReviews.add(e);
            return e;
        }).when(leakReviews).save(any(LeakReviewEntity.class));
        doAnswer(inv -> {
            MeterTestEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            savedTests.add(e);
            return e;
        }).when(meterTests).save(any(MeterTestEntity.class));
        when(leakReviews.findByTenantIdAndBillIdAndStatus(any(), any(), eq("OPEN")))
                .thenReturn(Optional.empty());
        when(leakReviews.findByTenantIdAndUnitIdAndStatus(any(), any(), eq("GRANTED")))
                .thenReturn(List.of());
    }

    /** Parametros no cenario ARESC: 30 dias do vencimento, teto 2, percentual 70, esgoto pela media. */
    private static LeakMeterParameters arescParams() {
        Map<String, String> map = new HashMap<>();
        map.put(LeakMeterParameters.LEAK_CLAIM_DEADLINE_DAYS, "30");
        map.put(LeakMeterParameters.LEAK_CLAIM_DEADLINE_ANCHOR, "\"DUE_DATE\"");
        map.put(LeakMeterParameters.LEAK_MAX_BILLS_PER_12_MONTHS, "2");
        map.put(LeakMeterParameters.LEAK_DISCOUNT_RULE, "\"PCT_OF_EXCESS\"");
        map.put(LeakMeterParameters.LEAK_MAX_DISCOUNT_PCT_WATER, "70");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true");
        map.put(LeakMeterParameters.METER_TEST_NOTICE_DAYS, "5");
        map.put(LeakMeterParameters.METER_TEST_REVIEW_MONTHS, "6");
        return LeakMeterParameters.fromValues(map);
    }

    private LeakMeterService.LeakReviewOutcome openLeakReview() {
        UUID billReviewId = UUID.randomUUID();
        UUID billReviewOrderId = UUID.randomUUID();
        when(billReviewPort.openBillReview(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new BillReviewPort.Opened(billReviewId, billReviewOrderId));
        return service.requestLeakReview(TENANT, BILL, LocalDate.of(2026, 3, 1), UNIT, REASON, null,
                "vazamento no ramal interno", ACTOR, null, "key-1", arescParams(),
                LocalDate.of(2026, 4, 10), LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 20),
                List.of(YearMonth.of(2026, 3)), LocalDate.of(2026, 4, 10));
    }

    @Test
    @DisplayName("abertura: BILL_REVIEW da P2.20 abre primeiro e a LEAK_REVIEW nasce filha dela, com prazo fotografado")
    void requestOpensBillReviewFirstAndChildOrder() {
        LeakMeterService.LeakReviewOutcome outcome = openLeakReview();
        verify(billReviewPort).openBillReview(eq(TENANT), eq(BILL), eq(REASON), any(), any(),
                eq(ACTOR), any(), any(), any());
        assertThat(gateway.calls).containsExactly("ensureType:LEAK_REVIEW", "createOrder:LEAK_REVIEW");
        assertThat(gateway.lastParentOrderId).isEqualTo(outcome.billReviewOrderId());
        assertThat(gateway.lastPayload).contains("\"billReviewId\"", "\"billReviewOrderId\"");
        assertThat(outcome.deadlineOn()).isEqualTo(LocalDate.of(2026, 4, 19));
        assertThat(savedReviews).hasSize(1);
        LeakReviewEntity saved = savedReviews.get(0);
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getDeadlineAnchor()).isEqualTo("DUE_DATE");
        assertThat(saved.getCoveredReferences()).contains("2026-03");
        assertThat(saved.getServiceOrderId()).isEqualTo(outcome.serviceOrderId());
    }

    @Test
    @DisplayName("pedido intempestivo e recusado ANTES de qualquer OS (nem a BILL_REVIEW nasce)")
    void lateClaimRefusedBeforeAnyOrder() {
        assertThatThrownBy(() -> service.requestLeakReview(TENANT, BILL, LocalDate.of(2026, 3, 1), UNIT,
                REASON, null, "tarde demais", ACTOR, null, "key-2", arescParams(),
                LocalDate.of(2026, 4, 20), LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 20),
                List.of(YearMonth.of(2026, 3)), LocalDate.of(2026, 4, 20)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("intempestiva");
        verify(billReviewPort, never()).openBillReview(any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(gateway.calls).isEmpty();
        assertThat(savedReviews).isEmpty();
    }

    @Test
    @DisplayName("acima do teto de 12 meses e recusado ANTES de qualquer OS")
    void overLimitRefusedBeforeAnyOrder() {
        LeakReviewEntity granted = new LeakReviewEntity();
        granted.setCoveredReferences("[\"2026-01\",\"2026-02\"]");
        when(leakReviews.findByTenantIdAndUnitIdAndStatus(any(), any(), eq("GRANTED")))
                .thenReturn(List.of(granted));
        assertThatThrownBy(() -> service.requestLeakReview(TENANT, BILL, LocalDate.of(2026, 3, 1), UNIT,
                REASON, null, "terceira em 12 meses", ACTOR, null, "key-3", arescParams(),
                LocalDate.of(2026, 4, 10), LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 20),
                List.of(YearMonth.of(2026, 3)), LocalDate.of(2026, 4, 10)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inelegivel");
        verify(billReviewPort, never()).openBillReview(any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(gateway.calls).isEmpty();
    }

    @Test
    @DisplayName("decisao GRANT roda no execute e fotografa o calculo: 50/20/40/16 a 70% vira 29/16 GRANTED")
    void grantDecisionComputesInsideExecute() {
        LeakMeterService.LeakReviewOutcome outcome = openLeakReview();
        LeakReviewEntity open = savedReviews.get(0);
        when(leakReviews.findByTenantIdAndId(TENANT, open.getId())).thenReturn(Optional.of(open));
        LeakMeterService.LeakDecision decision = service.decideLeakReview(TENANT, open.getId(),
                LeakMeterService.LeakDecisionKind.GRANT,
                new LeakDiscountRule.Volumes(new BigDecimal("50"), new BigDecimal("20"),
                        new BigDecimal("40"), new BigDecimal("16")),
                "vistoria confirmou vazamento oculto", ACTOR, arescParams(), LocalDate.of(2026, 4, 15));
        assertThat(gateway.calls).contains("execute");
        assertThat(decision.status()).isEqualTo("GRANTED");
        assertThat(decision.revisedWater()).isEqualByComparingTo("29");
        assertThat(decision.revisedSewer()).isEqualByComparingTo("16");
        assertThat(decision.effectiveRule()).isEqualTo("PCT_OF_EXCESS");
        assertThat(open.getStatus()).isEqualTo("GRANTED");
        assertThat(open.getDecidedOn()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(open.getMaxDiscountPctWater()).isEqualByComparingTo("70");
        // Ba3: a nota da abertura nao e sobrescrita; a da decisao vai para decision_note.
        assertThat(open.getNote()).isEqualTo("vazamento no ramal interno");
        assertThat(open.getDecisionNote()).isEqualTo("vistoria confirmou vazamento oculto");
        assertThat(outcome.leakReviewId()).isEqualTo(open.getId());
    }

    @Test
    @DisplayName("decisao DENY registra sem volumes revistos")
    void denyDecision() {
        openLeakReview();
        LeakReviewEntity open = savedReviews.get(0);
        when(leakReviews.findByTenantIdAndId(TENANT, open.getId())).thenReturn(Optional.of(open));
        LeakMeterService.LeakDecision decision = service.decideLeakReview(TENANT, open.getId(),
                LeakMeterService.LeakDecisionKind.DENY, null,
                "vistoria nao confirmou vazamento", ACTOR, arescParams(), LocalDate.of(2026, 4, 15));
        assertThat(decision.status()).isEqualTo("DENIED");
        assertThat(decision.revisedWater()).isNull();
        assertThat(open.getStatus()).isEqualTo("DENIED");
        assertThat(open.getRevisedWater()).isNull();
        assertThat(open.getNote()).isEqualTo("vazamento no ramal interno");
        assertThat(open.getDecisionNote()).isEqualTo("vistoria nao confirmou vazamento");
    }

    @Test
    @DisplayName("A1: abertura da P2.20 aguardando aprovacao nao cria filha nem grava pedido; o erro nomeia a retomada")
    void awaitingApprovalCreatesNothing() {
        UUID billReviewOrderId = UUID.randomUUID();
        when(billReviewPort.openBillReview(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new BillReviewPort.AwaitingApproval(billReviewOrderId));
        assertThatThrownBy(() -> service.requestLeakReview(TENANT, BILL, LocalDate.of(2026, 3, 1), UNIT,
                REASON, null, "vazamento no ramal interno", ACTOR, null, "key-await", arescParams(),
                LocalDate.of(2026, 4, 10), LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 20),
                List.of(YearMonth.of(2026, 3)), LocalDate.of(2026, 4, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aguardando aprovacao")
                .hasMessageContaining("Idempotency-Key");
        // O tipo foi garantido ANTES da abertura (sem orfa), mas nenhuma OS filha nasceu.
        assertThat(gateway.calls).containsExactly("ensureType:LEAK_REVIEW");
        assertThat(savedReviews).isEmpty();
    }

    @Test
    @DisplayName("B1: o servico expoe approve e startExecution sobre o motor (o condutor do ciclo de campo)")
    void serviceExposesApproveAndStartExecution() {
        UUID orderId = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        service.approve(orderId, approver);
        service.startExecution(orderId, ACTOR);
        assertThat(gateway.calls).containsExactly("approve", "startExecution");
    }

    @Test
    @DisplayName("M2: DENY com execute no-op (efeito nao aplicado) e recusado, como no GRANT")
    void denyWithoutAppliedEffectIsRefused() {
        openLeakReview();
        LeakReviewEntity open = savedReviews.get(0);
        when(leakReviews.findByTenantIdAndId(TENANT, open.getId())).thenReturn(Optional.of(open));
        gateway.applyEffect = false;
        assertThatThrownBy(() -> service.decideLeakReview(TENANT, open.getId(),
                LeakMeterService.LeakDecisionKind.DENY, null,
                "vistoria nao confirmou vazamento", ACTOR, arescParams(), LocalDate.of(2026, 4, 15)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nao aplicou o efeito");
        assertThat(open.getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Ba2: GRANT sob AVERAGE_6M nao exige leak.max_discount_pct_water e fotografa o percentual como nulo")
    void grantUnderAverageRuleDoesNotRequireWaterPct() {
        Map<String, String> map = new HashMap<>();
        map.put(LeakMeterParameters.LEAK_DISCOUNT_RULE, "\"AVERAGE_6M\"");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true");
        LeakMeterParameters averageParams = LeakMeterParameters.fromValues(map);
        LeakReviewEntity open = new LeakReviewEntity();
        open.setId(UUID.randomUUID());
        open.setTenantId(TENANT);
        open.setStatus("OPEN");
        open.setServiceOrderId(UUID.randomUUID());
        when(leakReviews.findByTenantIdAndId(TENANT, open.getId())).thenReturn(Optional.of(open));
        LeakMeterService.LeakDecision decision = service.decideLeakReview(TENANT, open.getId(),
                LeakMeterService.LeakDecisionKind.GRANT,
                new LeakDiscountRule.Volumes(new BigDecimal("50"), new BigDecimal("20"),
                        new BigDecimal("40"), new BigDecimal("16")),
                "deferido pela media", ACTOR, averageParams, LocalDate.of(2026, 4, 15));
        assertThat(decision.status()).isEqualTo("GRANTED");
        assertThat(decision.effectiveRule()).isEqualTo("AVERAGE_6M");
        assertThat(decision.revisedWater()).isEqualByComparingTo("20");
        assertThat(decision.revisedSewer()).isEqualByComparingTo("16");
        assertThat(open.getMaxDiscountPctWater()).isNull();
    }

    @Test
    @DisplayName("afericao marcada sem a antecedencia do aviso e recusada sem criar OS")
    void meterTestNoticeTooShort() {
        assertThatThrownBy(() -> service.requestMeterTest(TENANT, METER, UNIT, null,
                "afericao a pedido", ACTOR, arescParams(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("antecedencia");
        assertThat(gateway.calls).isEmpty();
        assertThat(savedTests).isEmpty();
    }

    @Test
    @DisplayName("afericao: pedido grava REQUESTED, cria a OS METER_TEST, agenda no motor (M3) e a carga nomeia o hidrometro")
    void meterTestRequest() {
        LeakMeterService.MeterTestOutcome outcome = service.requestMeterTest(TENANT, METER, UNIT, null,
                "afericao a pedido", ACTOR, arescParams(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        assertThat(gateway.calls).containsExactly("ensureType:METER_TEST", "createOrder:METER_TEST", "schedule");
        assertThat(gateway.lastPayload).contains("\"meterId\"", "\"unitId\"");
        assertThat(outcome.earliestTestDate()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(savedTests).hasSize(1);
        assertThat(savedTests.get(0).getStatus()).isEqualTo("REQUESTED");
        assertThat(savedTests.get(0).getServiceOrderId()).isEqualTo(outcome.serviceOrderId());
    }

    @Test
    @DisplayName("laudo IRREGULAR: nao cobra e devolve os 6 meses de revisao (2026-03 a 2026-08)")
    void reportIrregular() {
        LeakMeterService.MeterTestOutcome outcome = service.requestMeterTest(TENANT, METER, UNIT, null,
                "afericao a pedido", ACTOR, arescParams(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        MeterTestEntity test = savedTests.get(0);
        when(meterTests.findByTenantIdAndId(TENANT, test.getId())).thenReturn(Optional.of(test));
        LeakMeterService.MeterTestReportOutcome report = service.recordMeterTestReport(TENANT, test.getId(),
                MeterTestRules.MeterVerdict.IRREGULAR, new BigDecimal("12.5000"), "laudo-2026-0815",
                LocalDate.of(2026, 8, 15), ACTOR, arescParams());
        assertThat(report.chargeable()).isFalse();
        assertThat(report.reviewReferences()).containsExactly(
                YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5),
                YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8));
        assertThat(test.getStatus()).isEqualTo("REPORTED");
        assertThat(test.getChargeable()).isFalse();
        assertThat(test.getVerdict()).isEqualTo("IRREGULAR");
        assertThat(outcome.meterTestId()).isEqualTo(test.getId());
    }

    @Test
    @DisplayName("laudo NORMAL: cobra e nao abre revisao; laudo sem referencia e recusado")
    void reportNormalAndMissingReportRef() {
        service.requestMeterTest(TENANT, METER, UNIT, null, "afericao a pedido", ACTOR, arescParams(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        MeterTestEntity test = savedTests.get(0);
        when(meterTests.findByTenantIdAndId(TENANT, test.getId())).thenReturn(Optional.of(test));
        assertThatThrownBy(() -> service.recordMeterTestReport(TENANT, test.getId(),
                MeterTestRules.MeterVerdict.NORMAL, new BigDecimal("1.0000"), " ",
                LocalDate.of(2026, 8, 15), ACTOR, arescParams()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("laudo");
        LeakMeterService.MeterTestReportOutcome report = service.recordMeterTestReport(TENANT, test.getId(),
                MeterTestRules.MeterVerdict.NORMAL, new BigDecimal("1.0000"), "laudo-2026-0815",
                LocalDate.of(2026, 8, 15), ACTOR, arescParams());
        assertThat(report.chargeable()).isTrue();
        assertThat(report.reviewReferences()).isEmpty();
        assertThat(test.getChargeable()).isTrue();
    }

    @Test
    @DisplayName("Ba1: laudo sem data do ensaio e sem erro medido e recusado no servico, antes do execute")
    void reportMissingTestedOnAndMissingError() {
        service.requestMeterTest(TENANT, METER, UNIT, null, "afericao a pedido", ACTOR, arescParams(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        MeterTestEntity test = savedTests.get(0);
        when(meterTests.findByTenantIdAndId(TENANT, test.getId())).thenReturn(Optional.of(test));
        assertThatThrownBy(() -> service.recordMeterTestReport(TENANT, test.getId(),
                MeterTestRules.MeterVerdict.IRREGULAR, new BigDecimal("12.5000"), "laudo-2026-0815",
                null, ACTOR, arescParams()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("testedOn");
        assertThatThrownBy(() -> service.recordMeterTestReport(TENANT, test.getId(),
                MeterTestRules.MeterVerdict.NORMAL, null, "laudo-2026-0815",
                LocalDate.of(2026, 8, 15), ACTOR, arescParams()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("measuredErrorPct");
        assertThat(test.getStatus()).isEqualTo("REQUESTED");
        assertThat(gateway.calls).doesNotContain("execute");
    }
}
