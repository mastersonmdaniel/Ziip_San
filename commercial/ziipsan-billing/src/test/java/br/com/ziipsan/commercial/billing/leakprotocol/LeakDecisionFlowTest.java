package br.com.ziipsan.commercial.billing.leakprotocol;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.ACTOR;
import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeakDecisionFlowTest {

    /** Portas falsas: registram as chamadas; a assinatura devolve recibo sintetico. */
    static final class Fakes {
        final List<String> calls = new ArrayList<>();
        UUID rectifiedBill;
        LeakProtocolPorts.GrantedVolumes rectifiedVolumes;
        boolean failRectification;

        final LeakProtocolPorts.SigningPort signing = (tenantId, kind, documentRef, signerId) -> {
            calls.add("sign:" + kind);
            return new LeakProtocolPorts.SignatureReceipt("VERIFY-" + kind, "ADVANCED");
        };
        final LeakProtocolPorts.RectificationPort rectification = (tenantId, billId, volumes,
                                                                     protocolId, key, actorId) -> {
            calls.add("rectify");
            if (failRectification) {
                throw new IllegalStateException("retificacao barrada pelos limites da P2.20 (falso)");
            }
            rectifiedBill = billId;
            rectifiedVolumes = volumes;
            return new LeakProtocolPorts.RectificationReceipt(UUID.randomUUID(), UUID.randomUUID());
        };
        final LeakProtocolPorts.NotificationPort notifications =
                (tenantId, protocolId, phaseCode, billId) -> calls.add("notify:" + phaseCode);

        LeakDecisionFlow flow() {
            return new LeakDecisionFlow(signing, rectification, notifications);
        }
    }

    private LeakReviewProtocol protocolReadyToSignReport(UUID bill, UUID order, boolean leak) {
        LeakReviewProtocol protocol = ProtocolTestSupport.openProtocol(bill, order);
        protocol.beginInspection(ACTOR, T0);
        protocol.recordInspection(ProtocolTestSupport.inspection(order, leak), ACTOR, T0);
        return protocol;
    }

    @Test
    void grantedFlowSignsReportSignsDecisionRectifiesViaP220AndCloses() {
        Fakes fakes = new Fakes();
        UUID bill = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = protocolReadyToSignReport(bill, order, true);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.LEAK_CONFIRMED);

        fakes.flow().signReport(protocol, report, ACTOR, T0);
        assertEquals(LeakReviewProtocol.Phase.DECISION, protocol.phase());
        assertTrue(report.signed());

        LeakProtocolPorts.GrantedVolumes volumes = new LeakProtocolPorts.GrantedVolumes(
                new BigDecimal("29"), new BigDecimal("16"), "PCT_OF_EXCESS");
        fakes.flow().decideGranted(protocol, volumes, ACTOR, "idem-1", T0);

        assertEquals(LeakReviewProtocol.Phase.CLOSED_GRANTED, protocol.phase());
        assertEquals(bill, fakes.rectifiedBill);
        assertEquals(new BigDecimal("29"), fakes.rectifiedVolumes.revisedWater());
        assertEquals(List.of("sign:LAB_REPORT", "notify:REPORT_SIGNED",
                "sign:DECISION", "rectify", "notify:CLOSED_GRANTED"), fakes.calls);
    }

    @Test
    void deniedFlowSignsDecisionAndNeverRectifies() {
        Fakes fakes = new Fakes();
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = protocolReadyToSignReport(UUID.randomUUID(), order, false);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.NO_LEAK_FOUND);
        fakes.flow().signReport(protocol, report, ACTOR, T0);
        fakes.flow().decideDenied(protocol, ACTOR, T0);
        assertEquals(LeakReviewProtocol.Phase.CLOSED_DENIED, protocol.phase());
        assertTrue(fakes.calls.stream().noneMatch(c -> c.equals("rectify")));
    }

    @Test
    void grantWithReportNotConfirmingLeakIsRefusedBeforeAnyRectification() {
        Fakes fakes = new Fakes();
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = protocolReadyToSignReport(UUID.randomUUID(), order, false);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.NO_LEAK_FOUND);
        fakes.flow().signReport(protocol, report, ACTOR, T0);
        assertThrows(IllegalStateException.class, () -> fakes.flow().decideGranted(protocol,
                new LeakProtocolPorts.GrantedVolumes(BigDecimal.ONE, BigDecimal.ONE, "AVERAGE_6M"),
                ACTOR, "idem-2", T0));
        assertEquals(LeakReviewProtocol.Phase.DECISION, protocol.phase());
        assertTrue(fakes.calls.stream().noneMatch(c -> c.equals("rectify")));
    }

    @Test
    void rectificationRefusedByP220LeavesProtocolInRectificationNotClosed() {
        Fakes fakes = new Fakes();
        fakes.failRectification = true;
        UUID order = UUID.randomUUID();
        LeakReviewProtocol protocol = protocolReadyToSignReport(UUID.randomUUID(), order, true);
        LeakReport report = ProtocolTestSupport.report(
                protocol.inspection(), LeakReport.Conclusion.LEAK_CONFIRMED);
        fakes.flow().signReport(protocol, report, ACTOR, T0);
        assertThrows(IllegalStateException.class, () -> fakes.flow().decideGranted(protocol,
                new LeakProtocolPorts.GrantedVolumes(BigDecimal.ONE, BigDecimal.ONE, "PCT_OF_EXCESS"),
                ACTOR, "idem-3", T0));
        // A decisao assinada avancou a fase, mas o fechamento NAO foi fingido:
        assertEquals(LeakReviewProtocol.Phase.RECTIFICATION, protocol.phase());
        assertEquals(null, protocol.rectification());
    }

    @Test
    void signingKindsMatchP334DocumentTypes() {
        assertEquals("lab_report", LeakProtocolPorts.SignableKind.LAB_REPORT.p334Code());
        assertEquals("service_order_acceptance", LeakProtocolPorts.SignableKind.DECISION.p334Code());
    }
}
