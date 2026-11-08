package br.com.ziipsan.commercial.billing.leakprotocol;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.ACTOR;
import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.T0;
import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de correcao da revisao da ED-23 (defeitos M1, M2, B1, B2).
 * Cada teste captura o defeito correspondente: falha sobre o codigo
 * anterior a correcao e passa com ela.
 */
class LeakCorrectionEd23Test {

    private LeakReviewProtocol readyToSign(UUID bill, UUID order, boolean leak) {
        LeakReviewProtocol protocol = ProtocolTestSupport.openProtocol(bill, order);
        protocol.beginInspection(ACTOR, T0);
        protocol.recordInspection(ProtocolTestSupport.inspection(order, leak), ACTOR, T0);
        return protocol;
    }

    private static long countCalls(LeakDecisionFlowTest.Fakes fakes, String call) {
        return fakes.calls.stream().filter(c -> c.equals(call)).count();
    }

    // ---- M1: retomada documentada (decisao 4) apos recusa da P2.20 ----

    @Test
    void m1_retryDecideGrantedInRectificationResumesWithoutReSigning() {
        LeakDecisionFlowTest.Fakes fakes = new LeakDecisionFlowTest.Fakes();
        fakes.failRectification = true;
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = readyToSign(UUID.randomUUID(), order, true);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.LEAK_CONFIRMED);
        fakes.flow().signReport(protocol, report, ACTOR, T0);
        LeakProtocolPorts.GrantedVolumes volumes = new LeakProtocolPorts.GrantedVolumes(
                BigDecimal.ONE, BigDecimal.ONE, "PCT_OF_EXCESS");

        assertThrows(IllegalStateException.class,
                () -> fakes.flow().decideGranted(protocol, volumes, ACTOR, "idem-m1", T0));
        assertEquals(LeakReviewProtocol.Phase.RECTIFICATION, protocol.phase());
        assertEquals(1, countCalls(fakes, "sign:DECISION"));
        LeakProtocolPorts.SignatureReceipt signedDecision = protocol.decisionSignature();

        // A P2.20 aceita na segunda tentativa: repetir decideGranted com a
        // mesma chave (caminho documentado) conclui SEM re-assinar.
        fakes.failRectification = false;
        LeakProtocolPorts.RectificationReceipt receipt =
                fakes.flow().decideGranted(protocol, volumes, ACTOR, "idem-m1", T0);

        assertEquals(LeakReviewProtocol.Phase.CLOSED_GRANTED, protocol.phase());
        assertEquals(receipt, protocol.rectification());
        assertEquals(1, countCalls(fakes, "sign:DECISION"),
                "retomada nao pode emitir segunda assinatura de decisao");
        assertEquals(signedDecision, protocol.decisionSignature());
        assertEquals(2, countCalls(fakes, "rectify"));
    }

    // ---- M2: validar fase/conclusao ANTES de assinar na P3.34 ----

    @Test
    void m2_signReportInconclusiveDoesNotConsumeSignatureNorOrphanReport() {
        LeakDecisionFlowTest.Fakes fakes = new LeakDecisionFlowTest.Fakes();
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = readyToSign(UUID.randomUUID(), order, false);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.INCONCLUSIVE);

        assertThrows(IllegalStateException.class,
                () -> fakes.flow().signReport(protocol, report, ACTOR, T0));

        assertFalse(report.signed(), "laudo inconclusivo recusado nao pode ficar assinado orfao");
        assertEquals(null, protocol.report());
        assertEquals(LeakReviewProtocol.Phase.REPORT, protocol.phase());
        assertTrue(fakes.calls.isEmpty(),
                "nenhuma assinatura pode ser emitida antes da validacao: " + fakes.calls);
    }

    @Test
    void m2_decideDeniedOnClosedProtocolIsRefusedWithoutSpuriousSignature() {
        LeakDecisionFlowTest.Fakes fakes = new LeakDecisionFlowTest.Fakes();
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = readyToSign(UUID.randomUUID(), order, true);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.LEAK_CONFIRMED);
        fakes.flow().signReport(protocol, report, ACTOR, T0);
        fakes.flow().decideGranted(protocol, new LeakProtocolPorts.GrantedVolumes(
                BigDecimal.ONE, BigDecimal.ONE, "PCT_OF_EXCESS"), ACTOR, "k-m2", T0);
        assertEquals(LeakReviewProtocol.Phase.CLOSED_GRANTED, protocol.phase());
        int callsBefore = fakes.calls.size();

        assertThrows(IllegalStateException.class,
                () -> fakes.flow().decideDenied(protocol, ACTOR, T0));

        assertEquals(LeakReviewProtocol.Phase.CLOSED_GRANTED, protocol.phase());
        assertEquals(callsBefore, fakes.calls.size(),
                "decideDenied sobre protocolo encerrado nao pode emitir assinatura espuria: "
                        + fakes.calls.subList(callsBefore, fakes.calls.size()));
    }

    @Test
    void m2_decideGrantedOnClosedProtocolIsRefusedWithoutSpuriousSignature() {
        LeakDecisionFlowTest.Fakes fakes = new LeakDecisionFlowTest.Fakes();
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = readyToSign(UUID.randomUUID(), order, true);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.LEAK_CONFIRMED);
        fakes.flow().signReport(protocol, report, ACTOR, T0);
        LeakProtocolPorts.GrantedVolumes volumes = new LeakProtocolPorts.GrantedVolumes(
                BigDecimal.ONE, BigDecimal.ONE, "PCT_OF_EXCESS");
        fakes.flow().decideGranted(protocol, volumes, ACTOR, "k-m2b", T0);
        assertEquals(LeakReviewProtocol.Phase.CLOSED_GRANTED, protocol.phase());
        int callsBefore = fakes.calls.size();

        assertThrows(IllegalStateException.class,
                () -> fakes.flow().decideGranted(protocol, volumes, ACTOR, "k-m2b", T0));

        assertEquals(callsBefore, fakes.calls.size(),
                "GRANT repetido sobre protocolo encerrado nao pode assinar de novo");
    }

    @Test
    void m2_signReportOutsideReportPhaseIsRefusedWithoutSigning() {
        LeakDecisionFlowTest.Fakes fakes = new LeakDecisionFlowTest.Fakes();
        UUID order = UUID.randomUUID();
        // Protocolo ainda em OPENED: nenhuma assinatura pode ser consumida.
        LeakReviewProtocol protocol = ProtocolTestSupport.openProtocol(UUID.randomUUID(), order);
        LeakReport report = ProtocolTestSupport.report(
                ProtocolTestSupport.inspection(order, true), LeakReport.Conclusion.LEAK_CONFIRMED);

        assertThrows(IllegalStateException.class,
                () -> fakes.flow().signReport(protocol, report, ACTOR, T0));

        assertFalse(report.signed());
        assertTrue(fakes.calls.isEmpty(), "sem assinatura antes da validacao de fase: " + fakes.calls);
    }

    // ---- B1: GrantedVolumes rejeita volumes negativos na entrada ----

    @Test
    void b1_grantedVolumesRejectsNegativeVolumesAtEntry() {
        assertThrows(IllegalArgumentException.class, () -> new LeakProtocolPorts.GrantedVolumes(
                new BigDecimal("-1"), BigDecimal.ONE, "PCT_OF_EXCESS"));
        assertThrows(IllegalArgumentException.class, () -> new LeakProtocolPorts.GrantedVolumes(
                BigDecimal.ONE, new BigDecimal("-0.01"), "PCT_OF_EXCESS"));
        // Zero e positivo continuam validos (a recusa e so do negativo):
        new LeakProtocolPorts.GrantedVolumes(BigDecimal.ZERO, BigDecimal.ZERO, "PCT_OF_EXCESS");
        new LeakProtocolPorts.GrantedVolumes(BigDecimal.ONE, BigDecimal.ONE, "PCT_OF_EXCESS");
    }

    // ---- B2: Poc26Evidence exige leakReviewId/protocolId distintos ----

    private LeakReviewProtocol completeWithIds(UUID protocolId, UUID billId,
                                               UUID leakReviewId, UUID orderId) {
        LeakDecisionFlowTest.Fakes fakes = new LeakDecisionFlowTest.Fakes();
        LeakReviewProtocol protocol = new LeakReviewProtocol(
                protocolId, TENANT, billId, leakReviewId, orderId, ACTOR, T0);
        protocol.beginInspection(ACTOR, T0);
        protocol.recordInspection(ProtocolTestSupport.inspection(orderId, true), ACTOR, T0);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.LEAK_CONFIRMED);
        LeakDecisionFlow flow = fakes.flow();
        flow.signReport(protocol, report, ACTOR, T0);
        flow.decideGranted(protocol, new LeakProtocolPorts.GrantedVolumes(
                new BigDecimal("29"), new BigDecimal("16"), "PCT_OF_EXCESS"),
                ACTOR, "idem-" + protocolId, T0);
        assertEquals(LeakReviewProtocol.Phase.CLOSED_GRANTED, protocol.phase());
        return protocol;
    }

    @Test
    void b2_sameLeakReviewIdOnTwoBillsDoesNotSatisfyPoc26() {
        UUID sameLeakReview = UUID.randomUUID();
        LeakReviewProtocol first = completeWithIds(UUID.randomUUID(), UUID.randomUUID(),
                sameLeakReview, UUID.randomUUID());
        LeakReviewProtocol second = completeWithIds(UUID.randomUUID(), UUID.randomUUID(),
                sameLeakReview, UUID.randomUUID());
        Poc26Evidence.Verdict verdict = Poc26Evidence.check(List.of(first, second));
        assertFalse(verdict.satisfied(),
                "duas revisoes do mesmo pedido da P3.28 nao sao duas revisoes: " + verdict.reason());
    }

    @Test
    void b2_sameProtocolIdDoesNotSatisfyPoc26() {
        UUID sameProtocol = UUID.randomUUID();
        LeakReviewProtocol first = completeWithIds(sameProtocol, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        LeakReviewProtocol second = completeWithIds(sameProtocol, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        Poc26Evidence.Verdict verdict = Poc26Evidence.check(List.of(first, second));
        assertFalse(verdict.satisfied(),
                "mesmo protocolo repetido nao sao duas revisoes: " + verdict.reason());
    }
}
