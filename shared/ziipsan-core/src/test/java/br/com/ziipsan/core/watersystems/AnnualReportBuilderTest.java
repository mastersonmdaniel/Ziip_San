package br.com.ziipsan.core.watersystems;

import br.com.ziipsan.core.chemicals.ChemReports;
import br.com.ziipsan.core.quality.BillQualityBlockSource;
import br.com.ziipsan.core.quality.MonthlyClosing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnnualReportBuilderTest {

    private List<AnnualQualityReport.MonthlyQualitySummary> fullYear(String param) {
        List<AnnualQualityReport.MonthlyQualitySummary> rows = new ArrayList<>();
        for (int m = 1; m <= 12; m++) rows.add(SystemsTestSupport.summary(m, param, 10, 10, 10, 9, 1, 0));
        return rows;
    }

    @Test void fullYearAggregatesAndBecomesReady() {
        AnnualQualityReport report = AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                SystemsTestSupport.UNIT, "SAA-01", 2026, fullYear("PARAM_A"), List.of(), List.of());
        assertEquals(AnnualQualityReport.Status.READY, report.status());
        assertTrue(report.monthsMissing().isEmpty());
        assertEquals(12, report.monthsPresent().size());
        assertEquals(1, report.lines().size());
        AnnualQualityReport.AnnualLine line = report.lines().get(0);
        assertEquals(120, line.planned());
        assertEquals(120, line.analyzed());
        assertEquals(108, line.conforming());
        assertEquals(12, line.nonConforming());
        assertEquals(90.0, line.conformityPercent());
        assertEquals(AnnualQualityReport.LEGAL_BASIS, report.legalBasis());
    }

    @Test void missingMonthsStayDraftAndAreNamed() {
        List<AnnualQualityReport.MonthlyQualitySummary> rows = fullYear("PARAM_A");
        rows.removeIf(s -> s.month().getMonthValue() == 3 || s.month().getMonthValue() == 7);
        AnnualQualityReport report = AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                SystemsTestSupport.UNIT, "SAA-01", 2026, rows, List.of(), List.of());
        assertEquals(AnnualQualityReport.Status.DRAFT, report.status());
        assertEquals(List.of(YearMonth.of(2026, 3), YearMonth.of(2026, 7)), report.monthsMissing());
        assertEquals(100, report.lines().get(0).analyzed());
        assertEquals(10, report.lines().get(0).monthsPresent());
    }

    @Test void emptyYearIsDraftWithAllMonthsMissing() {
        AnnualQualityReport report = AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                SystemsTestSupport.UNIT, "SAA-01", 2026, List.of(), List.of(), List.of());
        assertEquals(AnnualQualityReport.Status.DRAFT, report.status());
        assertEquals(12, report.monthsMissing().size());
        assertTrue(report.lines().isEmpty());
    }

    @Test void summaryFromOtherYearRejected() {
        List<AnnualQualityReport.MonthlyQualitySummary> rows = List.of(
                new AnnualQualityReport.MonthlyQualitySummary(YearMonth.of(2025, 12), "PARAM_A", 1, 1, 1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> AnnualReportBuilder.build(UUID.randomUUID(),
                SystemsTestSupport.TENANT, SystemsTestSupport.UNIT, "SAA-01", 2026, rows, List.of(), List.of()));
    }

    @Test void blockAndCleaningFromOtherYearRejected() {
        var block = new AnnualQualityReport.MonthlyBlockLine(LocalDate.of(2025, 5, 1), "PARAM_A", "Param A", null, "resultado", null);
        assertThrows(IllegalArgumentException.class, () -> AnnualReportBuilder.build(UUID.randomUUID(),
                SystemsTestSupport.TENANT, SystemsTestSupport.UNIT, "SAA-01", 2026,
                fullYear("PARAM_A"), List.of(block), List.of()));
        var cleaning = new AnnualQualityReport.CleaningLine("RES-01", "Reservatorio", "SAA-01", LocalDate.of(2025, 5, 1), "LAUDO-1", "PROD-A");
        assertThrows(IllegalArgumentException.class, () -> AnnualReportBuilder.build(UUID.randomUUID(),
                SystemsTestSupport.TENANT, SystemsTestSupport.UNIT, "SAA-01", 2026,
                fullYear("PARAM_A"), List.of(), List.of(cleaning)));
    }

    @Test void fromClosingLineUsesRealEd50Line() {
        MonthlyClosing.ParameterLine ed50Line = new MonthlyClosing.ParameterLine("PARAM_A", 10, 9, 8, 6, 1, 1);
        AnnualQualityReport.MonthlyQualitySummary s =
                AnnualQualityReport.MonthlyQualitySummary.fromClosingLine(YearMonth.of(2026, 4), ed50Line);
        assertEquals(10, s.planned());
        assertEquals(9, s.collected());
        assertEquals(8, s.analyzed());
        assertEquals(6, s.conforming());
        assertEquals(1, s.indeterminate());
    }

    @Test void fromBillBlockLineUsesRealEd50SourceLine() {
        BillQualityBlockSource.BillBlockLine src = new BillQualityBlockSource.BillBlockLine(
                LocalDate.of(2026, 4, 1), "SAA-01", "PARAM_A", "Param A", "un", "resultado de teste", "referencia de teste");
        AnnualQualityReport.MonthlyBlockLine line = AnnualQualityReport.MonthlyBlockLine.fromBillBlockLine(src);
        assertEquals("PARAM_A", line.parameterKey());
        assertEquals("resultado de teste", line.resultText());
        assertEquals(LocalDate.of(2026, 4, 1), line.referenceMonth());
    }

    @Test void fromPublicRecordUsesRealEd89Record() {
        ChemReports.PublicCleaningRecord rec = new ChemReports.PublicCleaningRecord(
                "RES-01", "Reservatorio Teste", "SAA-01", LocalDate.of(2026, 6, 15),
                "LAUDO-2026-06", "PROD-A", new BigDecimal("1.50"), new BigDecimal("120.00"));
        AnnualQualityReport.CleaningLine line = AnnualQualityReport.CleaningLine.fromPublicRecord(rec);
        assertEquals("RES-01", line.reservoirCode());
        // Correcao D2: o sistema de origem da ED-89 NAO e mais descartado na conversao.
        assertEquals("SAA-01", line.supplySystem());
        assertEquals("LAUDO-2026-06", line.reportRef());
        AnnualQualityReport report = AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                SystemsTestSupport.UNIT, "SAA-01", 2026, fullYear("PARAM_A"), List.of(), List.of(line));
        assertEquals(1, report.cleanings().size());
        assertEquals(AnnualQualityReport.Status.READY, report.status());
    }

    @Test void cleaningWithoutReportRefRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AnnualQualityReport.CleaningLine(
                "RES-01", "Reservatorio", "SAA-01", LocalDate.of(2026, 6, 15), " ", "PROD-A"));
    }

    @Test void conformityPercentNullWhenOnlyIndeterminate() {
        AnnualQualityReport report = AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                SystemsTestSupport.UNIT, "SAA-01", 2026,
                List.of(SystemsTestSupport.summary(1, "PARAM_A", 5, 5, 5, 0, 0, 5)), List.of(), List.of());
        assertNull(report.lines().get(0).conformityPercent());
        assertEquals(5, report.lines().get(0).indeterminate());
    }

    @Test void verdictsExceedingAnalyzedRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SystemsTestSupport.summary(1, "PARAM_A", 5, 5, 4, 3, 2, 0));
    }

    @Test void readyReportCannotCarryMissingMonths() {
        // Correcao D1: o construtor publico nao existe mais (classe selada) —
        // a garantia equivalente e a do montador: com meses faltantes o
        // relatorio nasce DRAFT, nunca READY (ver tambem Ed79SealingTest).
        List<AnnualQualityReport.MonthlyQualitySummary> rows = fullYear("PARAM_A");
        rows.removeIf(x -> x.month().getMonthValue() == 2);
        AnnualQualityReport report = AnnualReportBuilder.build(UUID.randomUUID(),
                SystemsTestSupport.TENANT, SystemsTestSupport.UNIT, "SAA-01", 2026, rows, List.of(), List.of());
        assertEquals(AnnualQualityReport.Status.DRAFT, report.status());
        assertEquals(List.of(YearMonth.of(2026, 2)), report.monthsMissing());
    }

    // Correcao D2 (sondas A6/A7): pertencimento e duplicidade das limpezas.
    @Test void cleaningFromAnotherSystemRejected() {
        ChemReports.PublicCleaningRecord foreign = new ChemReports.PublicCleaningRecord(
                "RES-99", "Reservatorio Alheio", "SAA-OUTRO", LocalDate.of(2026, 6, 15),
                "LAUDO-X", "PROD-A", new BigDecimal("1.00"), new BigDecimal("10.00"));
        var line = AnnualQualityReport.CleaningLine.fromPublicRecord(foreign);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                        SystemsTestSupport.UNIT, "SAA-01", 2026, fullYear("PARAM_A"), List.of(), List.of(line)));
        assertTrue(ex.getMessage().contains("SAA-OUTRO"));
    }

    @Test void cleaningWithoutIdentifiedSystemRejected() {
        var line = new AnnualQualityReport.CleaningLine("RES-01", "Reservatorio", null,
                LocalDate.of(2026, 6, 15), "LAUDO-1", "PROD-A");
        assertThrows(IllegalArgumentException.class,
                () -> AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                        SystemsTestSupport.UNIT, "SAA-01", 2026, fullYear("PARAM_A"), List.of(), List.of(line)));
    }

    @Test void duplicatedCleaningRejected() {
        var line = new AnnualQualityReport.CleaningLine("RES-01", "Reservatorio", "SAA-01",
                LocalDate.of(2026, 6, 15), "LAUDO-1", "PROD-A");
        assertThrows(IllegalArgumentException.class,
                () -> AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                        SystemsTestSupport.UNIT, "SAA-01", 2026, fullYear("PARAM_A"), List.of(), List.of(line, line)));
    }
}
