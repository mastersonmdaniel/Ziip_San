package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.effluent.EffluentPorts;
import br.com.ziipsan.core.effluent.EffluentStandard;
import br.com.ziipsan.core.quality.Sample;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de ponta a ponta com gerador SINTETICO (aceite da ED-83):
 * cadastro -> caracterizacao -> termo -> autorizacao -> plano de
 * amostragem -> coleta do prestador na custodia REAL da ED-50 ->
 * veredito -> automonitoramento -> vistoria por OS -> notificacao ->
 * penalidade -> resolucao -> componente de cobranca para a P2.5.
 * Todos os valores sao fixture calculados a mao.
 */
class EndToEndNonDomesticTest {

    @Test
    void syntheticGeneratorFromAuthorizationToCharge() {
        // Portas falsas nomeadas (o laboratorio e a custodia REAIS da ED-50 por baixo).
        Sample sample = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                Sample.SampleType.ROUTINE, "ETQ-E2E-1", UUID.randomUUID(), null);
        sample.collect("COLETOR-1", TestSupport.T0);
        sample.sealAndDispatch("COLETOR-1", TestSupport.T0.plusSeconds(600));
        sample.receiveByLab("LAB-1", TestSupport.T0.plusSeconds(3600));

        var commercial = new NonDomesticPorts.CommercialRegistrationPort() {
            public boolean hasCommercialRegistration(UUID t, UUID u) { return true; }
            public boolean isCasualUnitOfEd22(UUID t, UUID u) { return false; }
        };
        ProviderSampling sampling = new ProviderSampling((t, s) -> s.equals(sample.id())
                ? new EffluentPorts.LabReceipt(sample.id(), sample.receivedByLab(), sample.labReceiverRef())
                : new EffluentPorts.LabReceipt(s, false, null));
        NonDomesticService svc = new NonDomesticService(commercial,
                (t, d, point, reason) -> UUID.randomUUID(),
                (t, d, caseId, reason) -> "NOTIF-E2E-1",
                sampling);

        // 1) Cadastro + marca + caracterizacao + termo + autorizacao.
        DischargerRegistration d = svc.register(UUID.randomUUID(), TestSupport.TENANT, TestSupport.UNIT_ID,
                DischargerRegistration.Kind.INDUSTRY, null, "PONTO-E2E-1");
        assertEquals("non_domestic_discharger", d.consumerUnitFlag());
        d.recordCharacterization(TestSupport.characterization());
        d.recordCommitmentTerm("TERMO-E2E [FIXTURE]");
        d.authorize("AUT-E2E [FIXTURE]", LocalDate.of(2026, 1, 1), null);
        assertTrue(d.authorizedOn(LocalDate.of(2026, 3, 1)));

        // 2) Catalogo do prestador sobre a ED-105 + plano por intervalo do tenant.
        String version = NonDomesticParameters.userStandardCatalogVersion("PRESTADOR-1");
        assertEquals("PRESTADOR-1", version);
        UserStandardCatalog catalog = TestSupport.userCatalog(
                TestSupport.federalMax("PRESTADOR-1", "PARAM_A", "5", true),
                TestSupport.federalMax("BASE-1", "PARAM_A", "10", true));
        ProviderSamplingPlan plan = svc.samplingPlanFor(d, 30);
        assertEquals(LocalDate.of(2026, 1, 31), plan.firstDueDate());

        // 3) Amostragem do prestador: valor 9 contra MAX 5 do prestador = nao conforme.
        var verdict = sampling.registerAnalysis(d,
                new ProviderSampling.CollectedSample(sample.id(), "PARAM_A"),
                new BigDecimal("9"), null, catalog, EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1));
        assertEquals(UserStandardCatalog.Assessment.Verdict.NON_CONFORMING, verdict.verdict());
        assertEquals(UserStandardCatalog.Source.PROVIDER, verdict.source());

        // 4) Automonitoramento do usuario coerente com a amostragem (tambem nao conforme).
        var report = new SelfMonitoringReport(UUID.randomUUID(), d.id(),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(new SelfMonitoringReport.ReportedResult("PARAM_A", new BigDecimal("9"), null)),
                "USUARIO-1", TestSupport.T0);
        var self = svc.evaluateSelfMonitoring(d, report, catalog,
                EffluentStandard.Jurisdiction.FEDERAL, null);
        assertEquals(UserStandardCatalog.Assessment.Verdict.NON_CONFORMING, self.get(0).verdict());

        // 5) Vistoria por OS + caso + notificacao + penalidade + resolucao.
        UUID inspectionOs = svc.orderInspection(d, "nao conformidade PARAM_A [FIXTURE]");
        assertTrue(inspectionOs != null);
        NonConformityCase kase = svc.openNonConformity(d, NonConformityCase.Origin.PROVIDER_SAMPLING,
                verdict, TestSupport.T0);
        svc.notifyNonConformity(d, kase, TestSupport.T0.plusSeconds(3600));
        assertEquals(NonConformityCase.Status.NOTIFIED, kase.status());
        assertEquals("NOTIF-E2E-1", kase.notificationRef());
        kase.applyPenalty(new BigDecimal("250.00"), "TABELA-PENALIDADES [FIXTURE]",
                TestSupport.T0.plusSeconds(7200));
        assertEquals(NonConformityCase.Status.PENALIZED, kase.status());
        UUID reinspectionOs = svc.orderInspection(d, "conferencia da correcao [FIXTURE]");
        kase.resolve("EVID-CORRECAO [FIXTURE]", reinspectionOs, TestSupport.T0.plusSeconds(86400));
        assertEquals(NonConformityCase.Status.RESOLVED, kase.status());

        // 6) Cobranca complementar pelo modelo configurado (VOLUME_PERCENT):
        //    100 m3 x 80% = 80 m3; 80 x 2,50 = 200,00 para a conta via P2.5.
        NonDomesticChargeModel model = NonDomesticParameters.chargeModel("VOLUME_PERCENT");
        assertEquals(NonDomesticChargeModel.VOLUME_PERCENT, model);
        var component = NonDomesticChargeCalculator.byVolumePercent(d, YearMonth.of(2026, 3),
                new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("2.50"));
        assertEquals("NONDOMESTIC_EFFLUENT", component.componentCode());
        assertEquals(new BigDecimal("200.00"), component.amount());
        assertEquals(d.id(), component.dischargerId());
    }

    @Test
    void syntheticCasualGeneratorFromAuthorizationToFixedCharge() {
        // Mesmo fluxo pela rota da ED-22 (sem cadastro comercial), fechando em taxa fixa por classe.
        var commercial = new NonDomesticPorts.CommercialRegistrationPort() {
            public boolean hasCommercialRegistration(UUID t, UUID u) { return false; }
            public boolean isCasualUnitOfEd22(UUID t, UUID u) { return true; }
        };
        NonDomesticService svc = new NonDomesticService(commercial,
                (t, dd, point, reason) -> UUID.randomUUID(),
                (t, dd, caseId, reason) -> "NOTIF-X",
                new ProviderSampling((t, s) -> new EffluentPorts.LabReceipt(s, true, "LAB-1")));
        DischargerRegistration d = svc.register(UUID.randomUUID(), TestSupport.TENANT,
                TestSupport.CASUAL_UNIT_ID, DischargerRegistration.Kind.LAUNDRY, null, "PONTO-LAV-E2E");
        assertEquals(DischargerRegistration.Route.CASUAL_VIA_ED22, d.route());
        d.recordCharacterization(TestSupport.characterization());
        d.recordCommitmentTerm("TERMO-LAV [FIXTURE]");
        d.authorize("AUT-LAV [FIXTURE]", LocalDate.of(2026, 1, 1), null);
        var component = NonDomesticChargeCalculator.byFixedClass(d, YearMonth.of(2026, 3),
                java.util.Map.of("LAUNDRY", new BigDecimal("150.00")));
        assertEquals(new BigDecimal("150.00"), component.amount());
    }
}
