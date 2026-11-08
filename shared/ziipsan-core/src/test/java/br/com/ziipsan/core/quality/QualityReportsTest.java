package br.com.ziipsan.core.quality;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QualityReportsTest {

    @Test void tenSamaeReports117To126AreDefinedWithSources() {
        List<QualityReports.ReportDefinition> defs = QualityReports.definitions();
        assertEquals(10, defs.size());
        for (int i = 0; i < 10; i++) {
            QualityReports.ReportDefinition d = defs.get(i);
            assertEquals("2.28." + (117 + i), d.samaeItem());
            assertEquals("SG-" + (1007 + i), d.code());
            assertFalse(d.title().isBlank());
            assertFalse(d.source().isBlank());
        }
    }
}
