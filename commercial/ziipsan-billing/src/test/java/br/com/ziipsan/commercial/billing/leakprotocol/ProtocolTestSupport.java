package br.com.ziipsan.commercial.billing.leakprotocol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Massa sintetica declarada dos testes da ED-23 (nenhum valor de tenant). */
final class ProtocolTestSupport {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID ACTOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final Instant T0 = Instant.parse("2026-11-08T12:00:00Z");

    private ProtocolTestSupport() {
    }

    static LeakReviewProtocol openProtocol(UUID billId, UUID orderId) {
        return new LeakReviewProtocol(UUID.randomUUID(), TENANT, billId,
                UUID.randomUUID(), orderId, ACTOR, T0);
    }

    static FieldInspection inspection(UUID orderId, boolean leakFound) {
        return new FieldInspection(orderId, ACTOR, T0,
                UUID.randomUUID(), UUID.randomUUID(), "METER-123",
                new BigDecimal("42.000"), leakFound,
                leakFound ? "cavalete" : null, List.of("foto do cavalete"));
    }

    static LeakReport report(FieldInspection inspection, LeakReport.Conclusion conclusion) {
        return new LeakReport(UUID.randomUUID(), inspection, conclusion,
                conclusion == LeakReport.Conclusion.INCONCLUSIVE ? "acesso ao ramal negado" : null,
                ACTOR, T0);
    }

    /** Protocolo levado ate a fase DECISION com laudo assinado falso (recibo sintetico). */
    static LeakReviewProtocol protocolInDecision(UUID billId, UUID orderId, boolean leakFound) {
        LeakReviewProtocol protocol = openProtocol(billId, orderId);
        protocol.beginInspection(ACTOR, T0);
        FieldInspection inspection = inspection(orderId, leakFound);
        protocol.recordInspection(inspection, ACTOR, T0);
        LeakReport report = report(inspection, leakFound
                ? LeakReport.Conclusion.LEAK_CONFIRMED : LeakReport.Conclusion.NO_LEAK_FOUND);
        report.markSigned(new LeakProtocolPorts.SignatureReceipt("VERIFY-REPORT", "ADVANCED"));
        protocol.attachSignedReport(report, ACTOR, T0);
        return protocol;
    }
}
