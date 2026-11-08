package br.com.ziipsan.core.inspection;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite ED-24: relatorio de unidades com fraude. */
class FraudReportTest {

    private static final UUID UNIT_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNIT_B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 9, 10, 0);

    private static FraudRecord fraud(UUID unit, FraudRecord.FraudStatus status) {
        return new FraudRecord(UUID.randomUUID(), unit, UUID.randomUUID(),
                FraudRecord.FraudType.METER_TAMPERING, "violacao do hidrometro",
                List.of(UUID.randomUUID()), "BO 2026-9999", status, UUID.randomUUID(), AT);
    }

    private static InfractionLifecycle.Case notice(UUID unit, BigDecimal amount,
                                                   InspectionParameters.ChargeTiming timing) {
        return InfractionLifecycle.issue(fraud(unit, FraudRecord.FraudStatus.CONFIRMED), amount, AT, 30, timing).newCase();
    }

    @Test
    void reportListsOnlyUnitsWithFraudWithTotals() {
        FraudReport.Report report = FraudReport.build(
                List.of(fraud(UNIT_A, FraudRecord.FraudStatus.CONFIRMED),
                        fraud(UNIT_A, FraudRecord.FraudStatus.SUSPECTED),
                        fraud(UNIT_B, FraudRecord.FraudStatus.CONFIRMED)),
                List.of(notice(UNIT_A, new BigDecimal("500.00"),
                        InspectionParameters.ChargeTiming.ON_ISSUE_SUSPENDED_BY_APPEAL)));

        assertEquals(2, report.unitsWithFraud());
        assertEquals(3, report.totalFrauds());
        assertEquals(new BigDecimal("500.00"), report.totalDebitLaunched());
        // Unidade A primeiro: mais fraudes.
        assertEquals(UNIT_A, report.rows().get(0).consumerUnitId());
        assertEquals(2, report.rows().get(0).fraudCount());
        assertEquals(1, report.rows().get(0).confirmedCount());
        assertEquals(1, report.rows().get(0).noticesIssued());
        assertEquals(1, report.rows().get(1).fraudCount());
    }

    /** D6 da revisao cruzada: DISMISSED tem contador proprio e a decomposicao fecha com fraudCount. */
    @Test
    void dismissedFraudsHaveOwnCounter() {
        FraudReport.Report report = FraudReport.build(
                List.of(fraud(UNIT_A, FraudRecord.FraudStatus.CONFIRMED),
                        fraud(UNIT_A, FraudRecord.FraudStatus.SUSPECTED),
                        fraud(UNIT_A, FraudRecord.FraudStatus.DISMISSED),
                        fraud(UNIT_A, FraudRecord.FraudStatus.DISMISSED)),
                List.of());

        FraudReport.UnitFraudRow row = report.rows().get(0);
        assertEquals(UNIT_A, row.consumerUnitId());
        assertEquals(4, row.fraudCount());
        assertEquals(1, row.confirmedCount());
        assertEquals(1, row.suspectedCount());
        assertEquals(2, row.dismissedCount(), "fraudes DISMISSED contam em contador proprio (D6)");
        assertEquals(row.fraudCount(),
                row.confirmedCount() + row.suspectedCount() + row.dismissedCount(),
                "confirmed + suspected + dismissed == fraudCount, sem inflar o total sem explicacao");
        assertEquals(4, report.totalFrauds());
    }

    @Test
    void emptyInputsGiveEmptyReport() {
        FraudReport.Report report = FraudReport.build(List.of(), List.of());
        assertEquals(0, report.unitsWithFraud());
        assertEquals(BigDecimal.ZERO, report.totalDebitLaunched());
        assertTrue(report.rows().isEmpty());
    }
}
