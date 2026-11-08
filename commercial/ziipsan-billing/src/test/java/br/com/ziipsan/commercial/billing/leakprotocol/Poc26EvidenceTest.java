package br.com.ziipsan.commercial.billing.leakprotocol;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.ACTOR;
import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.T0;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Poc26EvidenceTest {

    /** Uma revisao completa de ponta a ponta, com portas falsas (cenario local do POC-26). */
    private LeakReviewProtocol completeRevision(UUID bill) {
        LeakDecisionFlowTest.Fakes fakes = new LeakDecisionFlowTest.Fakes();
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = ProtocolTestSupport.openProtocol(bill, order);
        protocol.beginInspection(ACTOR, T0);
        protocol.recordInspection(ProtocolTestSupport.inspection(order, true), ACTOR, T0);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.LEAK_CONFIRMED);
        LeakDecisionFlow flow = fakes.flow();
        flow.signReport(protocol, report, ACTOR, T0);
        flow.decideGranted(protocol, new LeakProtocolPorts.GrantedVolumes(
                new BigDecimal("29"), new BigDecimal("16"), "PCT_OF_EXCESS"), ACTOR,
                "idem-" + bill, T0);
        return protocol;
    }

    @Test
    void twoCompleteRevisionsOnTwoDistinctBillsSatisfyPoc26() {
        Poc26Evidence.Verdict verdict = Poc26Evidence.check(List.of(
                completeRevision(UUID.randomUUID()), completeRevision(UUID.randomUUID())));
        assertTrue(verdict.satisfied(), verdict.reason());
    }

    @Test
    void sameBillTwiceDoesNotSatisfyPoc26() {
        UUID bill = UUID.randomUUID();
        Poc26Evidence.Verdict verdict = Poc26Evidence.check(List.of(
                completeRevision(bill), completeRevision(bill)));
        assertFalse(verdict.satisfied());
    }

    @Test
    void oneRevisionOnlyDoesNotSatisfyPoc26() {
        assertFalse(Poc26Evidence.check(List.of(completeRevision(UUID.randomUUID()))).satisfied());
    }

    @Test
    void deniedRevisionDoesNotSatisfyPoc26() {
        LeakReviewProtocol denied = ProtocolTestSupport.protocolInDecision(
                UUID.randomUUID(), UUID.randomUUID(), false);
        denied.recordDeniedDecision(
                new LeakProtocolPorts.SignatureReceipt("VERIFY-D", "SIMPLE"), ACTOR, T0);
        Poc26Evidence.Verdict verdict = Poc26Evidence.check(List.of(
                completeRevision(UUID.randomUUID()), denied));
        assertFalse(verdict.satisfied());
    }
}
