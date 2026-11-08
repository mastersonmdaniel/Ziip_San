package br.com.ziipsan.reporting.exportprofile;

import br.com.ziipsan.reporting.indicator.IndicatorClosing;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PnqsExportTest {

    private final ExportProfileService service = new ExportProfileService();
    private final Instant now = Instant.parse("2026-10-22T00:00:00Z");

    @Test void exportsNeutralStructureWithNatureNotices() {
        ExportDocument doc = service.exportPnqs(ExportProfileFixtures.TENANT,
                List.of(ExportProfileFixtures.series("COBERTURA.IAA", "2023", "2024", "2025")), now);
        assertEquals(ExportProfileKind.PNQS, doc.kind());
        assertTrue(doc.notices().contains(ExportReferences.PNQS_NATURE_NOTICE));
        assertTrue(doc.notices().contains(ExportReferences.PNQS_NEUTRAL_STRUCTURE_NOTICE));
        assertNull(doc.lines().get(0).externalReference());
        assertEquals(3, doc.lines().get(0).points().size());
    }

    @Test void seriesIsSortedChronologically() {
        IndicatorSeries s = ExportProfileFixtures.series("COBERTURA.IAA", "2025", "2023", "2024");
        assertEquals(2023, s.points().get(0).periodStart().getYear());
        assertEquals(2025, s.points().get(2).periodStart().getYear());
    }

    @Test void refusesReopenedClosingInSeries() {
        ExportValidationException ex = assertThrows(ExportValidationException.class, () ->
                SeriesPoint.fromClosing(ExportProfileFixtures.closing("X", "2025-01-01", "1",
                        IndicatorClosing.Status.REOPENED)));
        assertEquals("EXPORT_CLOSING_NOT_CLOSED", ex.code());
    }

    @Test void refusesIndicatorWithoutSeries() {
        ExportValidationException ex = assertThrows(ExportValidationException.class, () ->
                new IndicatorSeries(ExportProfileFixtures.sheet("COBERTURA.IAA"), List.of()));
        assertEquals("EXPORT_SERIES_EMPTY", ex.code());
    }

    @Test void refusesDuplicatedPeriod() {
        IndicatorSeries a = ExportProfileFixtures.series("COBERTURA.IAA", "2024");
        ExportValidationException ex = assertThrows(ExportValidationException.class, () ->
                new IndicatorSeries(ExportProfileFixtures.sheet("COBERTURA.IAA"),
                        List.of(a.points().get(0), a.points().get(0))));
        assertEquals("EXPORT_SERIES_DUPLICATED_PERIOD", ex.code());
    }
}
