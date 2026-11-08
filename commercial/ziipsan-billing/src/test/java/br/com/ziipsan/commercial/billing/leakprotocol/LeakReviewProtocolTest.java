package br.com.ziipsan.commercial.billing.leakprotocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.ACTOR;
import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeakReviewProtocolTest {

    @Test
    void fullGrantedPathWalksEveryPhaseInOrder() {
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = ProtocolTestSupport.protocolInDecision(UUID.randomUUID(), order, true);
        assertEquals(LeakReviewProtocol.Phase.DECISION, protocol.phase());
        protocol.recordGrantedDecision(
                new LeakProtocolPorts.SignatureReceipt("VERIFY-DECISION", "ADVANCED"), ACTOR, T0);
        assertEquals(LeakReviewProtocol.Phase.RECTIFICATION, protocol.phase());
        protocol.completeRectification(new LeakProtocolPorts.RectificationReceipt(
                UUID.randomUUID(), UUID.randomUUID()), ACTOR, T0);
        assertEquals(LeakReviewProtocol.Phase.CLOSED_GRANTED, protocol.phase());
        // Abertura + INSPECTION + REPORT + DECISION + RECTIFICATION + CLOSED = 6 eventos.
        assertEquals(6, protocol.events().size());
    }

    @Test
    void deniedPathClosesWithoutRectification() {
        LeakReviewProtocol protocol = ProtocolTestSupport.protocolInDecision(
                UUID.randomUUID(), UUID.randomUUID(), false);
        protocol.recordDeniedDecision(
                new LeakProtocolPorts.SignatureReceipt("VERIFY-DENY", "SIMPLE"), ACTOR, T0);
        assertEquals(LeakReviewProtocol.Phase.CLOSED_DENIED, protocol.phase());
        assertEquals(null, protocol.rectification());
    }

    @Test
    void inspectionFromAnotherOrderIsRefused() {
        LeakReviewProtocol protocol = ProtocolTestSupport.openProtocol(UUID.randomUUID(), UUID.randomUUID());
        protocol.beginInspection(ACTOR, T0);
        FieldInspection foreign = ProtocolTestSupport.inspection(UUID.randomUUID(), true);
        assertThrows(IllegalArgumentException.class, () -> protocol.recordInspection(foreign, ACTOR, T0));
        assertEquals(LeakReviewProtocol.Phase.INSPECTION, protocol.phase());
    }

    @Test
    void unsignedReportDoesNotOpenDecision() {
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = ProtocolTestSupport.openProtocol(UUID.randomUUID(), order);
        protocol.beginInspection(ACTOR, T0);
        FieldInspection inspection = ProtocolTestSupport.inspection(order, true);
        protocol.recordInspection(inspection, ACTOR, T0);
        LeakReport unsigned = ProtocolTestSupport.report(inspection, LeakReport.Conclusion.LEAK_CONFIRMED);
        assertThrows(IllegalStateException.class, () -> protocol.attachSignedReport(unsigned, ACTOR, T0));
        assertEquals(LeakReviewProtocol.Phase.REPORT, protocol.phase());
    }

    @Test
    void inconclusiveReportCannotOpenDecisionButReturnsToInspection() {
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = ProtocolTestSupport.openProtocol(UUID.randomUUID(), order);
        protocol.beginInspection(ACTOR, T0);
        FieldInspection inspection = ProtocolTestSupport.inspection(order, false);
        protocol.recordInspection(inspection, ACTOR, T0);
        LeakReport inconclusive = ProtocolTestSupport.report(inspection, LeakReport.Conclusion.INCONCLUSIVE);
        inconclusive.markSigned(new LeakProtocolPorts.SignatureReceipt("VERIFY-INC", "SIMPLE"));
        assertThrows(IllegalStateException.class, () -> protocol.attachSignedReport(inconclusive, ACTOR, T0));
        protocol.returnToInspection("laudo inconclusivo", ACTOR, T0);
        assertEquals(LeakReviewProtocol.Phase.INSPECTION, protocol.phase());
        assertEquals(null, protocol.inspection());
    }

    @Test
    void grantedCloseRequiresRectificationReceiptFromP220() {
        LeakReviewProtocol protocol = ProtocolTestSupport.protocolInDecision(
                UUID.randomUUID(), UUID.randomUUID(), true);
        // Sem passar pela fase RECTIFICATION, fechar como granted nao existe:
        assertThrows(IllegalStateException.class, () -> protocol.completeRectification(
                new LeakProtocolPorts.RectificationReceipt(UUID.randomUUID(), UUID.randomUUID()), ACTOR, T0));
    }

    @Test
    void cancelRequiresReasonAndIsRefusedWhenTerminal() {
        LeakReviewProtocol protocol = ProtocolTestSupport.openProtocol(UUID.randomUUID(), UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> protocol.cancel(" ", ACTOR, T0));
        protocol.cancel("usuario desistiu", ACTOR, T0);
        assertEquals(LeakReviewProtocol.Phase.CANCELLED, protocol.phase());
        assertThrows(IllegalStateException.class, () -> protocol.cancel("de novo", ACTOR, T0));
    }

    @Test
    void skippingPhasesIsRefused() {
        LeakReviewProtocol protocol = ProtocolTestSupport.openProtocol(UUID.randomUUID(), UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> protocol.recordInspection(
                ProtocolTestSupport.inspection(protocol.leakServiceOrderId(), true), ACTOR, T0));
        assertTrue(protocol.events().size() == 1);
    }
}
