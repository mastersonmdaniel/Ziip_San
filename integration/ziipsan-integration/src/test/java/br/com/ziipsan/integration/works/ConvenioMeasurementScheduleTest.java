package br.com.ziipsan.integration.works;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** C1 (convenios ligados ao GIS), C2 (medicao + consistencia), C3 (cronograma). */
class ConvenioMeasurementScheduleTest {

    // ---------- C1 ----------
    @Test void federalAndStateAgreementsRegisterLinkedToGis() {
        for (FundingAgreement.Kind kind : FundingAgreement.Kind.values()) {
            var s = new WorksAgreementService(WorksFixtures.gis());
            var a = WorksFixtures.agreement(kind);
            assertSame(a, s.register(a));
            assertEquals(new BigDecimal("900000.00"), a.grantorValue());
        }
    }

    @Test void agreementWithoutGisLinkIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new FundingAgreement(
                WorksFixtures.TENANT, UUID.randomUUID(), FundingAgreement.Kind.FEDERAL_CONVENIO,
                "REF", "Concedente", new BigDecimal("10"), BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                FundingAgreement.Status.DRAFT, Set.of()));
    }

    @Test void unknownGisProjectIsRefused() {
        var s = new WorksAgreementService(WorksFixtures.gis());
        var a = new FundingAgreement(WorksFixtures.TENANT, UUID.randomUUID(),
                FundingAgreement.Kind.ESTADUAL_CONVENIO, "REF", "Concedente",
                new BigDecimal("10"), BigDecimal.ZERO, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1), FundingAgreement.Status.ACTIVE,
                Set.of(new FundingAgreement.GisLink("GIS-INEXISTENTE", null)));
        assertThrows(IllegalArgumentException.class, () -> s.register(a));
    }

    @Test void crossTenantAccessIsRefused() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONVENIO);
        var s = WorksFixtures.serviceWith(a);
        assertThrows(IllegalArgumentException.class,
                () -> s.requireAgreement(WorksFixtures.OTHER_TENANT, a.id()));
    }

    // ---------- C3 ----------
    @Test void scheduleMustSum100PercentAndTotalValue() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONTRATO_REPASSE);
        var s = new WorksAgreementService(WorksFixtures.gis());
        s.register(a);
        var bad = new Schedule(WorksFixtures.TENANT, a.id(), java.util.List.of(
                new Schedule.Stage("ET-1", "x", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                        new BigDecimal("40"), new BigDecimal("400000.00"))));
        assertThrows(IllegalArgumentException.class, () -> s.defineSchedule(bad));
        assertNotNull(s.defineSchedule(WorksFixtures.schedule(a.id())));
    }

    @Test void progressViewComparesPlannedAndExecuted() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.ESTADUAL_CONTRATO_REPASSE);
        var s = WorksFixtures.serviceWith(a);
        s.addMeasurement(WorksFixtures.measurement(a.id(), 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "50", "500000.00"));
        var view = s.progressThrough(WorksFixtures.TENANT, a.id(), LocalDate.of(2026, 6, 30));
        assertEquals(0, new BigDecimal("50").compareTo(view.plannedPercent()));
        assertEquals(0, new BigDecimal("50").compareTo(view.executedPercent()));
        assertEquals(0, new BigDecimal("500000.00").compareTo(view.executedAmount()));
    }

    // ---------- C2 + consistencia ----------
    @Test void validMeasurementsAccumulate() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONVENIO);
        var s = WorksFixtures.serviceWith(a);
        s.addMeasurement(WorksFixtures.measurement(a.id(), 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "50", "500000.00"));
        s.addMeasurement(WorksFixtures.measurement(a.id(), 2,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "50", "500000.00"));
        assertEquals(0, new BigDecimal("100").compareTo(s.accumulatedPercent(a.id())));
        assertEquals(0, new BigDecimal("1000000.00").compareTo(s.accumulatedAmount(a.id())));
    }

    @Test void measurementExceedingScheduleIsRefused() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONVENIO);
        var s = WorksFixtures.serviceWith(a);
        // 60% no 1o semestre: o cronograma so preve 50% ate 30/06.
        assertThrows(IllegalArgumentException.class, () -> s.addMeasurement(
                WorksFixtures.measurement(a.id(), 1, LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 30), "60", "500000.00")));
    }

    @Test void measurementExceedingContractValueIsRefused() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONVENIO);
        var s = WorksFixtures.serviceWith(a);
        // 50% fisico mas valor acima do previsto acumulado (500 mil).
        assertThrows(IllegalArgumentException.class, () -> s.addMeasurement(
                WorksFixtures.measurement(a.id(), 1, LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 30), "50", "600000.00")));
    }

    @Test void accumulatedOver100AndOverlapAndDuplicateSequenceAreRefused() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONVENIO);
        var s = WorksFixtures.serviceWith(a);
        s.addMeasurement(WorksFixtures.measurement(a.id(), 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "50", "500000.00"));
        assertThrows(IllegalArgumentException.class, () -> s.addMeasurement(
                WorksFixtures.measurement(a.id(), 1, LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 12, 31), "10", "100000.00"))); // seq duplicada
        assertThrows(IllegalArgumentException.class, () -> s.addMeasurement(
                WorksFixtures.measurement(a.id(), 2, LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 12, 31), "10", "100000.00"))); // sobreposicao
        assertThrows(IllegalArgumentException.class, () -> s.addMeasurement(
                WorksFixtures.measurement(a.id(), 2, LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 12, 31), "60", "400000.00"))); // acumulado 110%
    }

    @Test void measurementOnNonActiveAgreementIsRefused() {
        var draft = new FundingAgreement(WorksFixtures.TENANT, UUID.randomUUID(),
                FundingAgreement.Kind.FEDERAL_CONVENIO, "REF", "Concedente",
                new BigDecimal("1000000.00"), BigDecimal.ZERO, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), FundingAgreement.Status.DRAFT,
                Set.of(new FundingAgreement.GisLink("GIS-PRJ-001", null)));
        var s = new WorksAgreementService(WorksFixtures.gis());
        s.register(draft);
        s.defineSchedule(WorksFixtures.schedule(draft.id()));
        assertThrows(IllegalStateException.class, () -> s.addMeasurement(
                WorksFixtures.measurement(draft.id(), 1, LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 30), "10", "100000.00")));
    }
}
