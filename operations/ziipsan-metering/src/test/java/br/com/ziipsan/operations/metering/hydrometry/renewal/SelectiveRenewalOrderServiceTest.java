package br.com.ziipsan.operations.metering.hydrometry.renewal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Teste §4.4 da estimativa da II-5: payload por hidrómetro seleccionado, 1:1 com o plano. */
class SelectiveRenewalOrderServiceTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private AnnualRenewalPlan.AnnualPlan threeLinePlan() {
        var cfg = RenewalFixtures.config();
        var c1 = RenewalFixtures.candidate(RenewalFixtures.snapshot("31", "H-001",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2016, 1, 1), 20_000, "TEC_A", LocalDate.of(2018, 6, 1),
                new BigDecimal("600")), cfg);
        var c2 = RenewalFixtures.candidate(RenewalFixtures.snapshot("32", "H-002",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 10_000, "TEC_A", LocalDate.of(2020, 1, 1),
                new BigDecimal("500")), cfg);
        var c3 = RenewalFixtures.candidate(RenewalFixtures.snapshot("33", "H-003",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2021, 1, 1), 5_000, "TEC_B", LocalDate.of(2019, 1, 1),
                new BigDecimal("300")), cfg);
        return AnnualRenewalPlan.build(2027, List.of(c3, c1, c2),
                new AnnualRenewalPlan.RenewalRestriction.ByCount(3), null);
    }

    @Test @DisplayName("payloads 1:1 com o plano: mesmo tamanho, mesma ordem, mesmos hidrómetros e posições")
    void payloadsOneToOneWithPlan() {
        var plan = threeLinePlan();
        var svc = new SelectiveRenewalOrderService((t, p) -> UUID.randomUUID(), (t, m) -> false);
        var payloads = svc.buildPayloads(TENANT, plan);

        assertEquals(plan.lines().size(), payloads.size());
        for (int i = 0; i < plan.lines().size(); i++) {
            var line = plan.lines().get(i);
            var payload = payloads.get(i);
            assertEquals(line.meterId(), payload.meterId());
            assertEquals(line.position(), payload.position());
            assertEquals(2027, payload.planYear());
            assertEquals(TENANT, payload.tenantId());
            assertEquals("METER_REPLACEMENT_SELECTIVE", payload.serviceTypeCode());
            assertEquals("METER", payload.targetClass());
            assertEquals(line.meterId(), payload.targetId());
            assertEquals(line.totalScore(), payload.totalScore());
            assertEquals(line.clockStatus(), payload.clockStatus());
        }
    }

    @Test @DisplayName("JSON do payload é exacto no formato e determinístico (mesmo plano -> mesmo JSON)")
    void payloadJsonExactAndDeterministic() {
        var plan = threeLinePlan();
        var svc = new SelectiveRenewalOrderService((t, p) -> UUID.randomUUID(), (t, m) -> false);
        var first = svc.buildPayloads(TENANT, plan);
        var second = svc.buildPayloads(TENANT, plan);
        assertEquals(first, second);

        var p = first.get(1); // H-002, score 0.67, próxima verificação 2027-01-01, relógio CURRENT
        String expected = "{\"meterId\":\"00000000-0000-0000-0000-000000000032\""
                + ",\"unitId\":\"99999999-9999-9999-9999-999999999999\""
                + ",\"meterNumber\":\"H-002\""
                + ",\"planYear\":2027"
                + ",\"planPosition\":2"
                + ",\"renewalScore\":\"" + p.totalScore().toPlainString() + "\""
                + ",\"dominantCriterion\":\"AGE\""
                + ",\"clockStatus\":\"CURRENT\""
                + ",\"nextVerificationOn\":\"2027-01-01\"}";
        assertEquals(expected, p.effectPayloadJson());
        RenewalFixtures.assertBd("0.67", p.totalScore());
    }

    @Test @DisplayName("despacho abre na ordem do plano e suprime quem já tem OS aberta (sem duplicar)")
    void dispatchOpensInOrderAndSuppressesOpen() {
        var plan = threeLinePlan();
        List<RenewalOrderPayload> received = new ArrayList<>();
        UUID alreadyOpenMeter = plan.lines().get(1).meterId();
        var svc = new SelectiveRenewalOrderService(
                (t, p) -> { received.add(p); return UUID.randomUUID(); },
                (t, m) -> m.equals(alreadyOpenMeter));
        var opened = svc.dispatch(TENANT, plan);
        assertEquals(2, opened.size());
        assertEquals(List.of(plan.lines().get(0).meterId(), plan.lines().get(2).meterId()),
                received.stream().map(RenewalOrderPayload::meterId).toList());
    }

    @Test @DisplayName("plano vazio: nenhum payload, nenhuma OS")
    void emptyPlanOpensNothing() {
        var plan = AnnualRenewalPlan.build(2027, List.of(),
                new AnnualRenewalPlan.RenewalRestriction.ByCount(5), null);
        List<RenewalOrderPayload> received = new ArrayList<>();
        var svc = new SelectiveRenewalOrderService(
                (t, p) -> { received.add(p); return UUID.randomUUID(); }, (t, m) -> false);
        assertTrue(svc.buildPayloads(TENANT, plan).isEmpty());
        assertTrue(svc.dispatch(TENANT, plan).isEmpty());
        assertTrue(received.isEmpty());
    }
}
