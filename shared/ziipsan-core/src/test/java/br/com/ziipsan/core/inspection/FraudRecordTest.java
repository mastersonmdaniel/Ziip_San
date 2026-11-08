package br.com.ziipsan.core.inspection;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite ED-24: fraude registrada com tipo, fotos e boletim — sem um dos tres, nao registra. */
class FraudRecordTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PHOTO = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    private static final UUID AGENT = UUID.fromString("00000000-0000-0000-0000-0000000000b4");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 9, 10, 0);

    static FraudRecord fraud(FraudRecord.FraudStatus status) {
        return new FraudRecord(UUID.fromString("00000000-0000-0000-0000-0000000000b5"), UNIT, ORDER,
                FraudRecord.FraudType.BYPASS, "desvio antes do hidrometro",
                List.of(PHOTO), "BO 2026-1234", status, AGENT, AT);
    }

    @Test
    void completeRecordIsAccepted() {
        FraudRecord record = fraud(FraudRecord.FraudStatus.CONFIRMED);
        assertEquals(FraudRecord.FraudType.BYPASS, record.type());
        assertEquals(1, record.photoDocumentIds().size());
        assertEquals("BO 2026-1234", record.policeReportNumber());
        assertTrue(record.canSupportInfractionNotice());
    }

    @Test
    void withoutPhotoIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new FraudRecord(
                UUID.randomUUID(), UNIT, ORDER, FraudRecord.FraudType.BYPASS, "desvio",
                List.of(), "BO 2026-1234", FraudRecord.FraudStatus.SUSPECTED, AGENT, AT));
    }

    @Test
    void withoutPoliceReportIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new FraudRecord(
                UUID.randomUUID(), UNIT, ORDER, FraudRecord.FraudType.BYPASS, "desvio",
                List.of(PHOTO), " ", FraudRecord.FraudStatus.SUSPECTED, AGENT, AT));
    }

    @Test
    void suspectedFraudCannotSupportNotice() {
        assertFalse(fraud(FraudRecord.FraudStatus.SUSPECTED).canSupportInfractionNotice());
        assertThrows(IllegalStateException.class, () -> InfractionLifecycle.issue(
                fraud(FraudRecord.FraudStatus.SUSPECTED), new java.math.BigDecimal("100.00"), AT,
                30, InspectionParameters.ChargeTiming.AFTER_DECISION));
    }
}
