package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.effluent.EffluentPorts;
import br.com.ziipsan.core.effluent.EffluentStandard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rota da ED-22, automonitoramento avaliado e vistoria por OS. */
class NonDomesticServiceTest {

    private static NonDomesticService service(
            NonDomesticPorts.CommercialRegistrationPort commercial,
            AtomicReference<UUID> lastInspection) {
        return new NonDomesticService(commercial,
                (tenantId, dischargerId, point, reason) -> {
                    UUID os = UUID.randomUUID();
                    if (lastInspection != null) lastInspection.set(os);
                    return os;
                },
                (tenantId, dischargerId, caseId, reason) -> "NOTIF-" + caseId,
                new ProviderSampling((t, s) -> new EffluentPorts.LabReceipt(s, true, "LAB-1")));
    }

    @Test
    void userWithoutCommercialRegistrationIsServedViaEd22CasualUnit() {
        var commercial = new NonDomesticPorts.CommercialRegistrationPort() {
            public boolean hasCommercialRegistration(UUID t, UUID u) { return false; }
            public boolean isCasualUnitOfEd22(UUID t, UUID u) { return u.equals(TestSupport.CASUAL_UNIT_ID); }
        };
        NonDomesticService svc = service(commercial, null);
        DischargerRegistration d = svc.register(UUID.randomUUID(), TestSupport.TENANT,
                TestSupport.CASUAL_UNIT_ID, DischargerRegistration.Kind.FUEL_STATION, null, "PONTO-POSTO-1");
        assertEquals(DischargerRegistration.Route.CASUAL_VIA_ED22, d.route());
        assertEquals("non_domestic_discharger", d.consumerUnitFlag());
    }

    @Test
    void userWithNeitherCommercialNorCasualUnitIsRefusedWithEd22Path() {
        var commercial = new NonDomesticPorts.CommercialRegistrationPort() {
            public boolean hasCommercialRegistration(UUID t, UUID u) { return false; }
            public boolean isCasualUnitOfEd22(UUID t, UUID u) { return false; }
        };
        NonDomesticService svc = service(commercial, null);
        assertThrows(IllegalStateException.class, () -> svc.register(UUID.randomUUID(), TestSupport.TENANT,
                TestSupport.UNIT_ID, DischargerRegistration.Kind.HOSPITAL, null, "PONTO-H-1"));
    }

    @Test
    void commercialUserRegistersOnOwnUnit() {
        var commercial = new NonDomesticPorts.CommercialRegistrationPort() {
            public boolean hasCommercialRegistration(UUID t, UUID u) { return true; }
            public boolean isCasualUnitOfEd22(UUID t, UUID u) { return false; }
        };
        NonDomesticService svc = service(commercial, null);
        DischargerRegistration d = svc.register(UUID.randomUUID(), TestSupport.TENANT,
                TestSupport.UNIT_ID, DischargerRegistration.Kind.AGROINDUSTRY, null, "PONTO-AGRO-1");
        assertEquals(DischargerRegistration.Route.COMMERCIAL_UNIT, d.route());
    }

    @Test
    void selfMonitoringIsJudgedAgainstProviderCatalog() {
        var commercial = new NonDomesticPorts.CommercialRegistrationPort() {
            public boolean hasCommercialRegistration(UUID t, UUID u) { return true; }
            public boolean isCasualUnitOfEd22(UUID t, UUID u) { return false; }
        };
        NonDomesticService svc = service(commercial, null);
        DischargerRegistration d = TestSupport.authorizedDischarger();
        UserStandardCatalog catalog = TestSupport.userCatalog(
                TestSupport.federalMax("PRESTADOR-1", "PARAM_A", "5", true), null);
        var report = new SelfMonitoringReport(UUID.randomUUID(), d.id(),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(new SelfMonitoringReport.ReportedResult("PARAM_A", new BigDecimal("9"), null)),
                "USUARIO-1", TestSupport.T0);
        var verdicts = svc.evaluateSelfMonitoring(d, report, catalog,
                EffluentStandard.Jurisdiction.FEDERAL, null);
        assertEquals(1, verdicts.size());
        assertEquals(UserStandardCatalog.Assessment.Verdict.NON_CONFORMING, verdicts.get(0).verdict());
        // E o caso de nao conformidade nasce desse veredito; INDETERMINATE nao abriria.
        NonConformityCase kase = svc.openNonConformity(d, NonConformityCase.Origin.SELF_MONITORING,
                verdicts.get(0), TestSupport.T0);
        assertEquals(NonConformityCase.Status.DETECTED, kase.status());
        var indeterminate = catalog.evaluate("PARAM_Z", new BigDecimal("1"), null,
                EffluentStandard.Jurisdiction.FEDERAL, null, LocalDate.of(2026, 3, 31));
        assertThrows(IllegalArgumentException.class, () -> svc.openNonConformity(d,
                NonConformityCase.Origin.SELF_MONITORING, indeterminate, TestSupport.T0));
    }

    @Test
    void inspectionOpensServiceOrderAtDischargePoint() {
        var commercial = new NonDomesticPorts.CommercialRegistrationPort() {
            public boolean hasCommercialRegistration(UUID t, UUID u) { return true; }
            public boolean isCasualUnitOfEd22(UUID t, UUID u) { return false; }
        };
        AtomicReference<UUID> last = new AtomicReference<>();
        NonDomesticService svc = service(commercial, last);
        DischargerRegistration d = TestSupport.authorizedDischarger();
        UUID os = svc.orderInspection(d, "divergencia automonitoramento x amostragem [FIXTURE]");
        assertTrue(os != null && os.equals(last.get()));
        assertThrows(IllegalArgumentException.class, () -> svc.orderInspection(d, " "));
    }

    @Test
    void samplingPlanNeedsAuthorizationAndTenantInterval() {
        var commercial = new NonDomesticPorts.CommercialRegistrationPort() {
            public boolean hasCommercialRegistration(UUID t, UUID u) { return true; }
            public boolean isCasualUnitOfEd22(UUID t, UUID u) { return false; }
        };
        NonDomesticService svc = service(commercial, null);
        ProviderSamplingPlan plan = svc.samplingPlanFor(TestSupport.authorizedDischarger(), 30);
        assertEquals(LocalDate.of(2026, 1, 31), plan.firstDueDate());
        assertThrows(br.com.ziipsan.core.parameters.MissingParameterException.class,
                () -> svc.samplingPlanFor(TestSupport.authorizedDischarger(), null));
    }
}
