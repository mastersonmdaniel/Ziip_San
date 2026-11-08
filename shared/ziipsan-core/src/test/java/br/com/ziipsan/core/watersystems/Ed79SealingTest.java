package br.com.ziipsan.core.watersystems;

import br.com.ziipsan.core.quality.MonthlyClosing;
import br.com.ziipsan.core.quality.SamplingPlan;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Correcao D1/D2 da revisao da ED-79 (08/11/2026): as sondas de
 * ataque do revisor (A1/A2/A3/A8/A9/A10) passam a ser RECUSADAS —
 * os tipos de estado terminal sao classes finais SELADAS (padrao
 * M-1 da ED-50), e a via por fechamentos reais da ED-50 recusa
 * fechamento nao-ENCERRADO (A4).
 */
class Ed79SealingTest {

    private static void assertSealed(Class<?> type) {
        assertTrue(Modifier.isFinal(type.getModifiers()), type.getSimpleName() + " deve ser final");
        assertFalse(type.isRecord(), type.getSimpleName() + " nao pode ser record de construtor publico");
        for (Constructor<?> c : type.getDeclaredConstructors())
            assertFalse(Modifier.isPublic(c.getModifiers()),
                    type.getSimpleName() + " nao pode ter construtor publico: " + c);
    }

    @Test void terminalStateTypesAreSealed() {
        assertSealed(AnnualQualityReport.class);
        assertSealed(AnnualReportIssuanceService.IssuanceRecord.class);
        assertSealed(SystemsPorts.LegalEmissionProof.class);
        assertSealed(ServiceCharter.class);
        assertSealed(WaterSafetyPlan.Plan.class);
    }

    @Test void noPublicWithStatusOnReport() {
        for (Method m : AnnualQualityReport.class.getDeclaredMethods())
            assertFalse(m.getName().equals("withStatus") && Modifier.isPublic(m.getModifiers()),
                    "withStatus publico reabriria a sonda A2");
    }

    @Test void reportBornReadyCannotBeIssuedWithoutTheService() {
        // Sem o servico de emissao (prova P2.32 + entrega P3.5) nao ha
        // caminho publico de nascimento/transicao para ISSUED: o
        // montador so produz DRAFT/READY e markIssued e de pacote.
        List<AnnualQualityReport.MonthlyQualitySummary> rows = new ArrayList<>();
        for (int m = 1; m <= 12; m++) rows.add(SystemsTestSupport.summary(m, "PARAM_A", 10, 10, 10, 10, 0, 0));
        AnnualQualityReport ready = AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                SystemsTestSupport.UNIT, "SAA-01", 2026, rows, List.of(), List.of());
        assertEquals(AnnualQualityReport.Status.READY, ready.status());
        for (Method m : AnnualQualityReport.class.getDeclaredMethods())
            if (m.getName().equals("markIssued"))
                assertFalse(Modifier.isPublic(m.getModifiers()), "markIssued nao pode ser publico");
    }

    @Test void charterAndPlanTransitionsArePackageOnly() {
        for (Method m : ServiceCharter.class.getDeclaredMethods())
            if (m.getName().equals("published") || m.getName().equals("superseded"))
                assertFalse(Modifier.isPublic(m.getModifiers()), "transicao da Carta nao pode ser publica: " + m.getName());
        for (Method m : WaterSafetyPlan.Plan.class.getDeclaredMethods())
            if (m.getName().equals("approved") || m.getName().equals("superseded"))
                assertFalse(Modifier.isPublic(m.getModifiers()), "transicao do PSA nao pode ser publica: " + m.getName());
    }

    // ---------- D2/A4: fechamento da ED-50 nao-ENCERRADO ----------

    private MonthlyClosing closing(String system, YearMonth month, boolean close) {
        SamplingPlan plan = new SamplingPlan(UUID.randomUUID(), system,
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "catalogo-de-teste",
                List.of(new SamplingPlan.PlanLine(UUID.randomUUID(), "PARAM_A", 10)));
        MonthlyClosing closing = MonthlyClosing.assemble(UUID.randomUUID(), system, month, plan, List.of(), List.of());
        if (close) closing.close("responsavel-tecnico-de-teste", Instant.parse("2026-02-01T00:00:00Z"));
        return closing;
    }

    @Test void openClosingRejectedByFromClosing() {
        MonthlyClosing open = closing("SAA-01", YearMonth.of(2026, 1), false);
        assertFalse(open.isClosed());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AnnualQualityReport.MonthlyQualitySummary.fromClosing(open));
        assertTrue(ex.getMessage().contains("ENCERRADO"));
    }

    @Test void openClosingRejectedByBuilderFromClosings() {
        MonthlyClosing open = closing("SAA-01", YearMonth.of(2026, 1), false);
        assertThrows(IllegalArgumentException.class, () -> AnnualReportBuilder.buildFromClosings(
                UUID.randomUUID(), SystemsTestSupport.TENANT, SystemsTestSupport.UNIT, "SAA-01", 2026,
                List.of(open), List.of(), List.of()));
    }

    @Test void closingFromAnotherSystemRejected() {
        MonthlyClosing foreign = closing("SAA-OUTRO", YearMonth.of(2026, 1), true);
        assertThrows(IllegalArgumentException.class, () -> AnnualReportBuilder.buildFromClosings(
                UUID.randomUUID(), SystemsTestSupport.TENANT, SystemsTestSupport.UNIT, "SAA-01", 2026,
                List.of(foreign), List.of(), List.of()));
    }

    @Test void closedClosingsBuildReadyReport() {
        List<MonthlyClosing> closings = new ArrayList<>();
        for (int m = 1; m <= 12; m++) closings.add(closing("SAA-01", YearMonth.of(2026, m), true));
        AnnualQualityReport report = AnnualReportBuilder.buildFromClosings(UUID.randomUUID(),
                SystemsTestSupport.TENANT, SystemsTestSupport.UNIT, "SAA-01", 2026,
                closings, List.of(), List.of());
        assertEquals(AnnualQualityReport.Status.READY, report.status());
        assertEquals(12, report.monthsPresent().size());
        assertEquals(1, report.lines().size());
        assertEquals(120, report.lines().get(0).planned());
    }
}
