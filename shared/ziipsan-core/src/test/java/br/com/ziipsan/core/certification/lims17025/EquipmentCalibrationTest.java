package br.com.ziipsan.core.certification.lims17025;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Calibracao com historico e BLOQUEIO do equipamento vencido (aceite). */
class EquipmentCalibrationTest {

    @Test void validOnLastDayBlockedTheDayAfter() {
        LabEquipment eq = TestSupport.validEquipment(); // valida ate 2027-01-09
        assertTrue(eq.validOn(LocalDate.of(2027, 1, 9)));
        assertFalse(eq.validOn(LocalDate.of(2027, 1, 10)));
        assertTrue(eq.blockedOn(LocalDate.of(2027, 1, 10)));
        assertThrows(IllegalStateException.class,
                () -> eq.requireUsableOn(LocalDate.of(2027, 1, 10)));
    }

    @Test void useBeforeFirstCalibrationIsBlocked() {
        LabEquipment eq = TestSupport.validEquipment(); // calibrou em 2026-01-10
        assertTrue(eq.blockedOn(LocalDate.of(2026, 1, 9)));
        assertThrows(IllegalStateException.class,
                () -> eq.requireUsableOn(LocalDate.of(2026, 1, 9)));
    }

    @Test void calibrationBornExpiredIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new LabEquipment.CalibrationRecord(
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1), "CERT", "CAL-1"));
        // certificado e calibrador sao obrigatorios
        assertThrows(IllegalArgumentException.class, () -> new LabEquipment.CalibrationRecord(
                LocalDate.of(2026, 5, 1), LocalDate.of(2027, 5, 1), " ", "CAL-1"));
    }

    @Test void historyIsPreservedAndRecalibrationExtendsUse() {
        LabEquipment eq = TestSupport.expiredEquipment(); // vencida em 2026-01-31
        assertTrue(eq.blockedOn(TestSupport.DAY));
        eq.recordCalibration(TestSupport.enabled(), new LabEquipment.CalibrationRecord(
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), "CERT-FIXTURE-3", "CALIBRADOR-1"));
        assertEquals(2, eq.history().size());
        assertEquals(LocalDate.of(2026, 1, 31), eq.history().get(0).validUntil());
        assertTrue(eq.validOn(TestSupport.DAY));
        // mas entre o vencimento e a recalibracao, continuava bloqueado
        assertTrue(eq.blockedOn(LocalDate.of(2026, 3, 1)));
    }

    @Test void outOfOrderCalibrationIsRefused() {
        LabEquipment eq = TestSupport.validEquipment();
        assertThrows(IllegalArgumentException.class, () -> eq.recordCalibration(TestSupport.enabled(),
                new LabEquipment.CalibrationRecord(LocalDate.of(2025, 12, 1),
                        LocalDate.of(2026, 11, 30), "CERT-X", "CAL-1")));
    }

    @Test void expiredEquipmentBlocksTheAssay() {
        assertThrows(IllegalStateException.class, () -> AssayExecution.start(TestSupport.enabled(),
                TestSupport.laboratory(), TestSupport.methodValidation(),
                List.of(TestSupport.authorization()), TestSupport.expiredEquipment(),
                TestSupport.envWithin(), TestSupport.qcInControl(), TestSupport.uncertainty(),
                null, UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                TestSupport.METHOD, TestSupport.PARAM, TestSupport.ANALYST, TestSupport.DAY));
    }
}
