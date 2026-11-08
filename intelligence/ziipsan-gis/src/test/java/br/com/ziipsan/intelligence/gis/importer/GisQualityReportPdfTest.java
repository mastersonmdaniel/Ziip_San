package br.com.ziipsan.intelligence.gis.importer;

import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.BatchView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correcao M3, item A1: o PDF do relatorio ISO 19157 (linha GIS-07 do
 * §11) sai das medidas REAIS registradas no lote — contagens da limpeza
 * de gis_import_clean e o JSON de gis_quality_report. Elemento sem
 * medida registrada e declarado NAO INFORMADO, nunca estimado.
 */
class GisQualityReportPdfTest {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final String HASH_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static BatchView batch(Map<String, Object> before, Map<String, Object> after) {
        return new BatchView(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                null, "DXF", "rede (trecho 1).dxf", HASH_A,
                null, null, null, "QUALITY_REPORTED",
                4674, "DETECTED", "NONE", null,
                new BigDecimal("0.50"), false, before, after, null,
                Instant.parse("2026-11-08T00:00:00Z"),
                Instant.parse("2026-11-08T00:00:00Z"));
    }

    private static String text(byte[] pdf) {
        return new String(pdf, WIN_ANSI);
    }

    @Test
    @DisplayName("PDF valido (cabecalho, xref e EOF) com as contagens reais da limpeza")
    void pdfCarregaContagensReaisDaLimpeza() {
        byte[] pdf = GisQualityReportPdf.render(
                batch(Map.of("total", 100, "invalid_geometry", 2, "without_geometry", 1),
                      Map.of("total", 97, "invalid_fixed", 2,
                             "duplicates_removed", 2, "short_segments_removed", 1)),
                Map.of("completeness", Map.of("omission", 1, "commission", 0)));
        String s = text(pdf);
        assertThat(s).startsWith("%PDF-1.4");
        assertThat(s).contains("startxref").contains("%%EOF");
        assertThat(s).contains("invalid_fixed=2");
        assertThat(s).contains("duplicates_removed=2");
        assertThat(s).contains("short_segments_removed=1");
        // Aproveitamento e aritmetica sobre as contagens reais: 97/100.
        // (No fluxo de texto do PDF os parenteses sao escapados — ver o
        // teste de escape abaixo; a assercao usa a forma do fluxo.)
        assertThat(s).contains("aproveitamento \\(depois/antes\\): 97.0%");
        // Medida registrada no relatorio do lote aparece com o seu valor.
        assertThat(s).contains("omission = 1");
    }

    @Test
    @DisplayName("elemento ISO sem medida registrada sai NAO INFORMADO, sem valor inventado")
    void elementoAusenteEhDeclaradoNaoInformado() {
        byte[] pdf = GisQualityReportPdf.render(
                batch(Map.of("total", 10), Map.of("total", 10)),
                Map.of("completeness", Map.of("omission", 0)));
        String s = text(pdf);
        assertThat(s).contains("- acuracia posicional: NAO INFORMADO no relatorio do lote");
        assertThat(s).contains("- acuracia tematica: NAO INFORMADO no relatorio do lote");
        assertThat(s).contains("- qualidade temporal: NAO INFORMADO no relatorio do lote");
        assertThat(s).doesNotContain("- completude: NAO INFORMADO");
    }

    @Test
    @DisplayName("lote ainda nao limpo declara contagens nao registradas")
    void semLimpezaDeclaraAusenciaDeContagens() {
        byte[] pdf = GisQualityReportPdf.render(batch(null, null), Map.of());
        assertThat(text(pdf)).contains("contagens nao registradas \\(lote ainda nao limpo\\)");
    }

    @Test
    @DisplayName("parenteses e barra do nome do arquivo sao escapados no fluxo do PDF")
    void escapaCaracteresDoFluxoPdf() {
        String s = text(GisQualityReportPdf.render(
                batch(Map.of("total", 1), Map.of("total", 1)), Map.of()));
        assertThat(s).contains("rede \\(trecho 1\\).dxf");
        assertThat(GisQualityReportPdf.escape("a(b)c\\d")).isEqualTo("a\\(b\\)c\\\\d");
    }
}
