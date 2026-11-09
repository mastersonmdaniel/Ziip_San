package br.com.ziipsan.core.quality;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de captura da CORRECAO pos-revisao da ED-50 (defeitos M-1, M-2,
 * M-3 e m-2 da REVISAO-ED-50, sondas B6/D1/E1 do revisor). Cada teste
 * foi escrito para FALHAR contra o codigo original e passar com a
 * correcao; massa sintetica generica, como o restante do pacote.
 */
class CorrectionEd50Test {

    // ------------------------------------------------------------ M-1
    @Test void m1AnalysisResultHasNoPublicConstructor() {
        // Sonda B6: o construtor publico do record criava resultado sobre
        // amostra PLANNED nunca recebida, furando o portao JOR-37.
        // Correcao: fabrica unica (firstVersion) com construtor privado —
        // nenhuma construcao publica pode existir.
        assertEquals(0, AnalysisResult.class.getConstructors().length,
                "AnalysisResult nao pode ter construtor publico: a unica via de "
                        + "nascimento e firstVersion, com a amostra recebida (JOR-37)");
    }

    @Test void m1LegitimateUsesStillWorkThroughFactory() {
        Sample s = TestSupport.receivedSample("ETQ-C01");
        AnalysisResult v1 = AnalysisResult.firstVersion(UUID.randomUUID(), s, "PARAM_A",
                new BigDecimal("5"), null, "u", "METODO-FIXTURE", "ANALISTA-1",
                TestSupport.T0.plusSeconds(7200));
        assertFalse(v1.validated());
        AnalysisResult v2 = v1.corrected(UUID.randomUUID(), new BigDecimal("6"), null,
                "transcricao corrigida pelo supervisor", "A-2", TestSupport.T0.plusSeconds(9000));
        assertEquals(2, v2.version());
        assertEquals(v1.id(), v2.supersedesId());
        AnalysisResult validated = v2.validate("SUPERVISOR-1");
        assertTrue(validated.validated());
        assertEquals("SUPERVISOR-1", validated.validatorRef());
        // E o portao segue de pe na fabrica:
        Sample planned = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, TestSupport.POINT,
                Sample.SampleType.ROUTINE, "ETQ-C02", null, null);
        assertThrows(IllegalStateException.class, () -> AnalysisResult.firstVersion(
                UUID.randomUUID(), planned, "PARAM_A", new BigDecimal("1"), null, "u", "M",
                "A-1", TestSupport.T0));
    }

    // ------------------------------------------------------------ M-2
    private SamplingPlan plan4() {
        return new SamplingPlan(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 4)));
    }

    @Test void m2CollectedCountsSampleCollectedWithoutValidatedResult() {
        // Sonda D1: 4 planejados, 1 amostra coletada+recebida com
        // resultado ainda NAO validado (logo, sem Entry) — collected
        // tem de ser 1, nao 0.
        Sample received = TestSupport.receivedSample("ETQ-C10");
        AnalysisResult draft = TestSupport.numericResult(received, "PARAM_A", "5", false);
        assertFalse(draft.validated());
        MonthlyClosing closing = MonthlyClosing.assemble(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), plan4(), List.of(received), List.of());
        MonthlyClosing.ParameterLine a = closing.lines().get(0);
        assertEquals(4, a.planned());
        assertEquals(1, a.collected());
        assertEquals(0, a.analyzed());
        assertEquals(25.0, a.coveragePercent());
    }

    @Test void m2CollectedCountsCollectedOnlyAndIgnoresPlannedAndCancelled() {
        Sample collectedOnly = Sample.planned(UUID.randomUUID(), TestSupport.TENANT,
                TestSupport.POINT, Sample.SampleType.ROUTINE, "ETQ-C11", null, null);
        collectedOnly.collect("COLETOR-1", TestSupport.T0);
        Sample plannedOnly = Sample.planned(UUID.randomUUID(), TestSupport.TENANT,
                TestSupport.POINT, Sample.SampleType.ROUTINE, "ETQ-C12", null, null);
        Sample cancelled = Sample.planned(UUID.randomUUID(), TestSupport.TENANT,
                TestSupport.POINT, Sample.SampleType.ROUTINE, "ETQ-C13", null, null);
        cancelled.collect("COLETOR-1", TestSupport.T0);
        cancelled.cancel("frasco quebrado");
        MonthlyClosing closing = MonthlyClosing.assemble(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), plan4(),
                List.of(collectedOnly, plannedOnly, cancelled), List.of());
        MonthlyClosing.ParameterLine a = closing.lines().get(0);
        assertEquals(1, a.collected());
        assertEquals(0, a.analyzed());
    }

    // ------------------------------------------------------------ M-3
    private ParameterStandard std(String max, LocalDate from, LocalDate to) {
        return new ParameterStandard("PARAM_A", "Parametro PARAM_A", "u",
                ParameterStandard.Comparator.MAX, null, new BigDecimal(max), null,
                "NORMA-SINTETICA [FIXTURE]", false, true, "RT-FIXTURE", from, to);
    }

    @Test void m3OverlappingVigenciasAreRefusedAtCatalogLoad() {
        // Sonda E1: limite 10 desde jan (sem fim) + limite 5 desde jun —
        // sobrepostas. Antes, resolvia pela ordem de insercao e o valor 7
        // em julho saia CONFORMING contra o limite errado. Correcao
        // escolhida: a carga RECUSA a sobreposicao.
        ParameterStandard jan = std("10", LocalDate.of(2026, 1, 1), null);
        ParameterStandard jun = std("5", LocalDate.of(2026, 6, 1), null);
        assertThrows(IllegalArgumentException.class,
                () -> new NormativeCatalog("FIXTURE-2", List.of(jan, jun)));
        // Tambem com a linha antiga parcialmente sobreposta:
        ParameterStandard janToAug = std("10", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31));
        assertThrows(IllegalArgumentException.class,
                () -> new NormativeCatalog("FIXTURE-2", List.of(janToAug, jun)));
        // E na ordem inversa de insercao (a recusa nao depende da ordem):
        assertThrows(IllegalArgumentException.class,
                () -> new NormativeCatalog("FIXTURE-2", List.of(jun, jan)));
    }

    @Test void m3NonOverlappingSuccessionStillResolvesByVigencia() {
        // Sucessao legitima (linha antiga com effectiveTo) segue valida:
        // 7 em janeiro conforme contra 10; 7 em julho nao conforme contra 5.
        NormativeCatalog catalog = new NormativeCatalog("FIXTURE-2", List.of(
                std("10", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
                std("5", LocalDate.of(2026, 7, 1), null)));
        Sample s = TestSupport.receivedSample("ETQ-C20");
        AnalysisResult r = TestSupport.numericResult(s, "PARAM_A", "7", true);
        assertEquals(ConformityEvaluator.Verdict.CONFORMING,
                ConformityEvaluator.evaluate(r, catalog, LocalDate.of(2026, 1, 10)).verdict());
        assertEquals(ConformityEvaluator.Verdict.NON_CONFORMING,
                ConformityEvaluator.evaluate(r, catalog, LocalDate.of(2026, 7, 10)).verdict());
    }

    // ------------------------------------------------------------ m-2
    @Test void m2ReturnedIsNotACustodyKind() {
        // RETURNED era enum morto (nenhum fluxo o emitia) + valor no CHECK
        // do SQL. O aceite da ED-50 nao preve devolucao de amostra —
        // removido do enum e do CHECK, sem peca morta.
        assertThrows(IllegalArgumentException.class, () -> Sample.CustodyKind.valueOf("RETURNED"));
        assertEquals(4, Sample.CustodyKind.values().length);
    }

    // ------------------------------------------------------------ B3-2 (Rodada 3)
    private SamplingPlan planAB() {
        return new SamplingPlan(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 1),
                        new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_B", 1)));
    }

    private static MonthlyClosing.ParameterLine line(MonthlyClosing c, String key) {
        return c.lines().stream().filter(l -> l.parameterKey().equals(key)).findFirst().orElseThrow();
    }

    @Test void b32_partialResultCountsCollectedForMissingParameter() {
        // Sonda do revisor da Rodada 3 (02-backend-c3-rodada3.md, B3-2): plano com
        // PARAM_A e PARAM_B no mesmo ponto (1 planejado cada); amostra coletada +
        // recebida; resultado validado SO para PARAM_A (5 <= 10, CONFORMING).
        // Esperado: PARAM_A collected=1 analyzed=1; PARAM_B collected=1 analyzed=0.
        // Antes da correcao, PARAM_B saia collected=0 (a amostra era excluida do
        // balde (b) por ter QUALQUER entrada).
        NormativeCatalog catalog = TestSupport.catalog(
                TestSupport.maxStandard("PARAM_A", "10", true, false),
                TestSupport.maxStandard("PARAM_B", "10", true, false));
        Sample s = TestSupport.receivedSample("ETQ-B32");
        AnalysisResult rA = TestSupport.numericResult(s, "PARAM_A", "5", true);
        List<MonthlyClosing.Entry> entries = List.of(
                new MonthlyClosing.Entry(s, rA, ConformityEvaluator.evaluate(rA, catalog, LocalDate.of(2026, 1, 10))));
        MonthlyClosing closing = MonthlyClosing.assemble(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), planAB(), List.of(s), entries);
        MonthlyClosing.ParameterLine a = line(closing, "PARAM_A");
        assertEquals(1, a.planned());
        assertEquals(1, a.collected());
        assertEquals(1, a.analyzed());
        assertEquals(1, a.conforming());
        MonthlyClosing.ParameterLine b = line(closing, "PARAM_B");
        assertEquals(1, b.planned());
        assertEquals(1, b.collected(), "amostra coletada sem entrada PARA PARAM_B conta em collected_B");
        assertEquals(0, b.analyzed());
        assertEquals(100.0, b.coveragePercent());
    }

    @Test void b32_sampleWithEntriesForBothParametersIsNotDoubleCounted() {
        // Guarda de regressao: amostra com entrada validada para A E para B conta
        // exatamente 1 vez em cada linha (balde (a) apenas — o balde (b) nao a
        // reconta em nenhuma das chaves).
        NormativeCatalog catalog = TestSupport.catalog(
                TestSupport.maxStandard("PARAM_A", "10", true, false),
                TestSupport.maxStandard("PARAM_B", "10", true, false));
        Sample s = TestSupport.receivedSample("ETQ-B32B");
        AnalysisResult rA = TestSupport.numericResult(s, "PARAM_A", "5", true);
        AnalysisResult rB = TestSupport.numericResult(s, "PARAM_B", "7", true);
        LocalDate d = LocalDate.of(2026, 1, 10);
        List<MonthlyClosing.Entry> entries = List.of(
                new MonthlyClosing.Entry(s, rA, ConformityEvaluator.evaluate(rA, catalog, d)),
                new MonthlyClosing.Entry(s, rB, ConformityEvaluator.evaluate(rB, catalog, d)));
        MonthlyClosing closing = MonthlyClosing.assemble(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), planAB(), List.of(s), entries);
        assertEquals(1, line(closing, "PARAM_A").collected());
        assertEquals(1, line(closing, "PARAM_A").analyzed());
        assertEquals(1, line(closing, "PARAM_B").collected());
        assertEquals(1, line(closing, "PARAM_B").analyzed());
    }
}
