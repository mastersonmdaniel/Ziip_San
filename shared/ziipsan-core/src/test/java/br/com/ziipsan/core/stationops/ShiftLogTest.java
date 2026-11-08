package br.com.ziipsan.core.stationops;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Diario por turno: equipamentos, leituras, passagem de turno. */
class ShiftLogTest {

    private ShiftLog openLog() {
        return new ShiftLog(java.util.UUID.randomUUID(), TestSupport.etaRapida(),
                LocalDate.of(2026, 3, 10), "DIA", "operador-1", TestSupport.T0);
    }

    @Test
    void diaryRecordsEquipmentOperatingReserveHorimeterAndStarts() {
        ShiftLog log = openLog();
        log.setEquipment(new ShiftLog.EquipmentState("BOMBA-1",
                ShiftLog.EquipmentSituation.OPERATING, new BigDecimal("1234.5"), 7));
        log.setEquipment(new ShiftLog.EquipmentState("BOMBA-2",
                ShiftLog.EquipmentSituation.STANDBY_RESERVE, new BigDecimal("900.0"), 2));
        assertThat(log.equipment()).hasSize(2);
        assertThat(log.equipment().get(0).situation()).isEqualTo(ShiftLog.EquipmentSituation.OPERATING);
        assertThat(log.equipment().get(1).situation()).isEqualTo(ShiftLog.EquipmentSituation.STANDBY_RESERVE);
        assertThatThrownBy(() -> log.setEquipment(new ShiftLog.EquipmentState("BOMBA-1",
                ShiftLog.EquipmentSituation.OUT_OF_SERVICE, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readingsCoverLevelFlowPressureAndProcess() {
        ShiftLog log = openLog();
        log.addReading(new DiaryReading(DiaryReading.Kind.LEVEL, null, new BigDecimal("3.20"), "m", TestSupport.T0));
        log.addReading(new DiaryReading(DiaryReading.Kind.FLOW, null, new BigDecimal("42.0"), "L/s", TestSupport.T0));
        log.addReading(new DiaryReading(DiaryReading.Kind.PRESSURE, null, new BigDecimal("28.0"), "mca", TestSupport.T0));
        log.addReading(new DiaryReading(DiaryReading.Kind.TURBIDITY, "F1", new BigDecimal("0.40"), "uT", TestSupport.T0));
        assertThat(log.readings()).hasSize(4);
        assertThat(log.processReadings()).hasSize(1);
        assertThat(log.turbidityOf("F1")).hasSize(1);
    }

    @Test
    void turbidityForUnknownFilterIsRefused() {
        ShiftLog log = openLog();
        assertThatThrownBy(() -> log.addReading(new DiaryReading(
                DiaryReading.Kind.TURBIDITY, "F9", new BigDecimal("0.4"), "uT", TestSupport.T0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiaryReading(
                DiaryReading.Kind.LEVEL, "F1", new BigDecimal("1"), "m", TestSupport.T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handoverClosesTheDiaryWithIncomingOperatorAndPendingItems() {
        ShiftLog log = openLog();
        log.handover(new ShiftLog.Handover("operador-1", "operador-2",
                TestSupport.T0.plusSeconds(12 * 3600), List.of("trocar o dosador"), null));
        assertThat(log.isHandedOver()).isTrue();
        assertThat(log.handover().incomingOperatorRef()).isEqualTo("operador-2");
        assertThat(log.handover().pendingItems()).containsExactly("trocar o dosador");
        assertThatThrownBy(() -> log.addReading(new DiaryReading(
                DiaryReading.Kind.LEVEL, null, new BigDecimal("1"), "m", TestSupport.T0)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> log.handover(new ShiftLog.Handover(
                "operador-2", "operador-3", TestSupport.T0.plusSeconds(13 * 3600), List.of(), null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unitValidationRapidFiltrationNeedsFiltersAndFiltrationIsWtpOnly() {
        assertThatThrownBy(() -> new StationUnit(java.util.UUID.randomUUID(), TestSupport.TENANT,
                "ETA-02", StationUnit.UnitKind.WTP, "SISTEMA-A",
                StationUnit.Filtration.RAPID, List.of(), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StationUnit(java.util.UUID.randomUUID(), TestSupport.TENANT,
                "ETE-01", StationUnit.UnitKind.WWTP, "SISTEMA-A",
                StationUnit.Filtration.SLOW, List.of(), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TestSupport.reservatorio().rapidFiltration()).isFalse();
    }
}
