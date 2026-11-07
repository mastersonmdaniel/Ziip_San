package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.registry.ReportRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class WritersTest {

    private final ReportRegistry registry = new ReportRegistry();

    private List<Map<String, Object>> rows() {
        return List.of(
                Map.of("data", "2026-10-05", "canal", "PIX", "pagamentos", 2L,
                        "valor_bruto", new java.math.BigDecimal("350.50"),
                        "valor_liquido", new java.math.BigDecimal("345.50"),
                        "valor_retido", new java.math.BigDecimal("5.00"),
                        "tributo_segregado", new java.math.BigDecimal("0")),
                Map.of("data", "2026-10-05", "canal", "BOLETO, \"especial\"", "pagamentos", 1L,
                        "valor_bruto", new java.math.BigDecimal("10.00"),
                        "valor_liquido", new java.math.BigDecimal("10.00"),
                        "valor_retido", new java.math.BigDecimal("0"),
                        "tributo_segregado", new java.math.BigDecimal("0")));
    }

    @Test
    void csvEscapaSeparadorAspasEQuebra() throws Exception {
        var definition = registry.find("RPT-001").orElseThrow();
        String csv = new String(new CsvReportWriter().write(definition, rows()), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("data,canal,pagamentos,valor_bruto");
        assertThat(csv).contains("\"BOLETO, \"\"especial\"\"\"");
        assertThat(csv).contains("350.50");
    }

    @Test
    void xlsxEUmZipOoxmlComAPlanilha() throws Exception {
        var definition = registry.find("RPT-001").orElseThrow();
        byte[] xlsx = new XlsxReportWriter().write(definition, rows());
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');
        String sheet = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            var entry = zip.getNextEntry();
            java.util.Set<String> names = new java.util.HashSet<>();
            while (entry != null) {
                names.add(entry.getName());
                if (entry.getName().equals("xl/worksheets/sheet1.xml")) {
                    sheet = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
                entry = zip.getNextEntry();
            }
            assertThat(names).contains("[Content_Types].xml", "xl/workbook.xml",
                    "xl/worksheets/sheet1.xml");
        }
        assertThat(sheet).contains("PIX").contains("345.50");
    }

    @Test
    void pdfTemCabecalhoAssinaturaEFimValidos() throws Exception {
        var definition = registry.find("RPT-001").orElseThrow();
        byte[] pdf = new PdfReportWriter().write(definition, rows());
        String text = new String(pdf, CharsetHolder.WIN_ANSI);
        assertThat(text).startsWith("%PDF-1.4");
        assertThat(text).contains("%%EOF");
        assertThat(text).contains("RPT-001");
        assertThat(PdfReportWriter.escape("a(b)c\\d")).isEqualTo("a\\(b\\)c\\\\d");
    }

    private static final class CharsetHolder {
        static final java.nio.charset.Charset WIN_ANSI = java.nio.charset.Charset.forName("windows-1252");
    }
}
