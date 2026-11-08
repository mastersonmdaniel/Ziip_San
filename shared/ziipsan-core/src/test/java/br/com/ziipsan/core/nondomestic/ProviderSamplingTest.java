package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.effluent.EffluentPorts;
import br.com.ziipsan.core.effluent.EffluentStandard;
import br.com.ziipsan.core.quality.Sample;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plano de amostragem por intervalo do tenant e analise do prestador
 * sobre a cadeia de custodia REAL da ED-50 (via porta da ED-105).
 */
class ProviderSamplingTest {

    @Test
    void planDerivesDueDatesFromTenantInterval() {
        ProviderSamplingPlan plan = new ProviderSamplingPlan(UUID.randomUUID(), "PONTO-1", 30,
                LocalDate.of(2026, 1, 1));
        assertEquals(LocalDate.of(2026, 1, 31), plan.firstDueDate());
        assertEquals(LocalDate.of(2026, 3, 2), plan.nextDueOnOrAfter(LocalDate.of(2026, 2, 15)));
        assertEquals(List.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 2), LocalDate.of(2026, 4, 1)),
                plan.dueDatesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)));
        assertTrue(plan.overdueOn(LocalDate.of(2026, 2, 1), null));
        assertFalse(plan.overdueOn(LocalDate.of(2026, 1, 31), null));
        assertTrue(plan.overdueOn(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 1, 31)));
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderSamplingPlan(UUID.randomUUID(), "P", 0, LocalDate.of(2026, 1, 1)));
    }

    /** Amostra REAL da ED-50 recebida pelo laboratorio (custodia completa). */
    private static Sample receivedEd50Sample(UUID tenantId) {
        Sample s = Sample.planned(UUID.randomUUID(), tenantId, UUID.randomUUID(),
                Sample.SampleType.ROUTINE, "ETQ-ND-1", UUID.randomUUID(), null);
        s.collect("COLETOR-1", TestSupport.T0);
        s.sealAndDispatch("COLETOR-1", TestSupport.T0.plusSeconds(600));
        s.receiveByLab("LAB-1", TestSupport.T0.plusSeconds(3600));
        return s;
    }

    private static ProviderSampling samplingWrapping(Sample sample) {
        return new ProviderSampling((tenantId, sampleId) -> sampleId.equals(sample.id())
                ? new EffluentPorts.LabReceipt(sample.id(), sample.receivedByLab(), sample.labReceiverRef())
                : new EffluentPorts.LabReceipt(sampleId, false, null));
    }

    @Test
    void analysisJudgedAgainstProviderCatalogAfterLabReceipt() {
        DischargerRegistration d = TestSupport.authorizedDischarger();
        Sample sample = receivedEd50Sample(d.tenantId());
        ProviderSampling sampling = samplingWrapping(sample);
        UserStandardCatalog catalog = TestSupport.userCatalog(
                TestSupport.federalMax("PRESTADOR-1", "PARAM_A", "5", true), null);
        var ok = sampling.registerAnalysis(d,
                new ProviderSampling.CollectedSample(sample.id(), "PARAM_A"),
                new BigDecimal("4"), null, catalog, EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1));
        assertEquals(UserStandardCatalog.Assessment.Verdict.CONFORMING, ok.verdict());
        var bad = sampling.registerAnalysis(d,
                new ProviderSampling.CollectedSample(sample.id(), "PARAM_A"),
                new BigDecimal("9"), null, catalog, EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1));
        assertEquals(UserStandardCatalog.Assessment.Verdict.NON_CONFORMING, bad.verdict());
    }

    @Test
    void analysisRefusedWithoutLabReceiptInEd50Custody() {
        DischargerRegistration d = TestSupport.authorizedDischarger();
        Sample plannedOnly = Sample.planned(UUID.randomUUID(), d.tenantId(), UUID.randomUUID(),
                Sample.SampleType.ROUTINE, "ETQ-ND-2", UUID.randomUUID(), null);
        ProviderSampling sampling = samplingWrapping(plannedOnly);
        UserStandardCatalog catalog = TestSupport.userCatalog(
                TestSupport.federalMax("PRESTADOR-1", "PARAM_A", "5", true), null);
        assertThrows(IllegalStateException.class, () -> sampling.registerAnalysis(d,
                new ProviderSampling.CollectedSample(plannedOnly.id(), "PARAM_A"),
                new BigDecimal("4"), null, catalog, EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1)));
    }

    @Test
    void analysisRefusedWhenDischargeNotAuthorizedOnCollectionDate() {
        DischargerRegistration d = new DischargerRegistration(UUID.randomUUID(), TestSupport.TENANT,
                TestSupport.UNIT_ID, DischargerRegistration.Route.COMMERCIAL_UNIT,
                DischargerRegistration.Kind.HOSPITAL, null, "PONTO-H-1");
        Sample sample = receivedEd50Sample(d.tenantId());
        ProviderSampling sampling = samplingWrapping(sample);
        UserStandardCatalog catalog = TestSupport.userCatalog(
                TestSupport.federalMax("PRESTADOR-1", "PARAM_A", "5", true), null);
        assertThrows(IllegalStateException.class, () -> sampling.registerAnalysis(d,
                new ProviderSampling.CollectedSample(sample.id(), "PARAM_A"),
                new BigDecimal("4"), null, catalog, EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1)));
    }
}
