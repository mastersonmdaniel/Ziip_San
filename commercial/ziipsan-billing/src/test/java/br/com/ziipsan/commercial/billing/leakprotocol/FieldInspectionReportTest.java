package br.com.ziipsan.commercial.billing.leakprotocol;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.ACTOR;
import static br.com.ziipsan.commercial.billing.leakprotocol.ProtocolTestSupport.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldInspectionReportTest {

    @Test
    void inspectionWithoutAfterPhotoDoesNotExist() {
        assertThrows(NullPointerException.class, () -> new FieldInspection(
                UUID.randomUUID(), ACTOR, T0, UUID.randomUUID(), null, "M-1",
                new BigDecimal("1"), false, null, List.of()));
    }

    @Test
    void inspectionWithoutMeterIdentityDoesNotExist() {
        assertThrows(IllegalArgumentException.class, () -> new FieldInspection(
                UUID.randomUUID(), ACTOR, T0, UUID.randomUUID(), UUID.randomUUID(), " ",
                new BigDecimal("1"), false, null, List.of()));
    }

    @Test
    void leakFoundWithoutLocationDoesNotExist() {
        assertThrows(IllegalArgumentException.class, () -> new FieldInspection(
                UUID.randomUUID(), ACTOR, T0, UUID.randomUUID(), UUID.randomUUID(), "M-1",
                new BigDecimal("1"), true, null, List.of()));
    }

    @Test
    void negativeMeterReadingDoesNotExist() {
        assertThrows(IllegalArgumentException.class, () -> new FieldInspection(
                UUID.randomUUID(), ACTOR, T0, UUID.randomUUID(), UUID.randomUUID(), "M-1",
                new BigDecimal("-1"), false, null, List.of()));
    }

    @Test
    void reportConfirmedAgainstFieldFactDoesNotExist() {
        FieldInspection noLeak = ProtocolTestSupport.inspection(UUID.randomUUID(), false);
        assertThrows(IllegalArgumentException.class, () -> ProtocolTestSupport.report(
                noLeak, LeakReport.Conclusion.LEAK_CONFIRMED));
    }

    @Test
    void inconclusiveReportRequiresReason() {
        FieldInspection inspection = ProtocolTestSupport.inspection(UUID.randomUUID(), false);
        assertThrows(IllegalArgumentException.class, () -> new LeakReport(
                UUID.randomUUID(), inspection, LeakReport.Conclusion.INCONCLUSIVE, null, ACTOR, T0));
    }

    @Test
    void reportStartsUnsignedAndSignsOnlyOnce() {
        LeakReport report = ProtocolTestSupport.report(
                ProtocolTestSupport.inspection(UUID.randomUUID(), true),
                LeakReport.Conclusion.LEAK_CONFIRMED);
        assertFalse(report.signed());
        report.markSigned(new LeakProtocolPorts.SignatureReceipt("VERIFY-1", "QUALIFIED"));
        assertTrue(report.signed());
        assertEquals("VERIFY-1", report.signedReceipt().verificationCode());
        assertThrows(IllegalStateException.class, () -> report.markSigned(
                new LeakProtocolPorts.SignatureReceipt("VERIFY-2", "QUALIFIED")));
    }
}
