package br.com.ziipsan.intelligence.gis.importer;

import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.BatchView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Relatorio ISO 19157-1 em PDF (GIS-07, §9.1 etapa 6 e linha GIS-07 do
 * §11: "relatorio ISO 19157 em PDF no pacote") — correcao M3, item A1.
 *
 * <p>PDF 1.4 minimo so com o JDK (Helvetica/WinAnsi, paginacao por
 * linhas), mesmo padrao do {@code PdfReportWriter} da P3.22: nenhuma
 * biblioteca de PDF nova entra no POM do modulo.
 *
 * <p><b>De onde saem os numeros (nada inventado):</b>
 * <ul>
 *   <li>cabecalho: o registro do lote ({@link BatchView}) — arquivo,
 *       hashes, SRS de origem e sua fonte, transformacao de datum e
 *       referencia da grade do IBGE registradas, tolerancia de snap;</li>
 *   <li>limpeza topologica: as contagens REAIS que a funcao
 *       {@code gis_import_clean} (V13_11) gravou no lote
 *       ({@code counts_before}/{@code counts_after}); o aproveitamento
 *       e aritmetica sobre essas duas contagens, com a formula escrita
 *       na propria linha;</li>
 *   <li>elementos ISO 19157-1 (§9.1 etapa 6): o que estiver registrado
 *       no JSON do relatorio do lote ({@code gis_quality_report},
 *       gravado por {@code saveQualityReport}). O pacote NAO computa
 *       sozinho completude contra a contagem da origem, acuracia
 *       posicional por amostra RTK nem acuracia tematica por amostra
 *       de campo — elemento sem medida registrada sai impresso como
 *       NAO INFORMADO, nunca com valor presumido.</li>
 * </ul>
 *
 * <p>Texto fora do WinAnsi e substituido por '?' (idem P3.22).
 */
public final class GisQualityReportPdf {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final int LINES_PER_PAGE = 45;
    private static final int MAX_LINE = 110;

    /** Elementos da ISO 19157-1 na ordem do §9.1 etapa 6. */
    private static final List<String[]> ISO_ELEMENTS = List.of(
            new String[]{"completude", "completeness", "completude"},
            new String[]{"consistencia logica", "logicalConsistency", "consistencia logica"},
            new String[]{"acuracia posicional", "positionalAccuracy", "acuracia posicional"},
            new String[]{"acuracia tematica", "thematicAccuracy", "acuracia tematica"},
            new String[]{"qualidade temporal", "temporalQuality", "qualidade temporal"});

    private GisQualityReportPdf() {
    }

    /** Gera o PDF do lote a partir do relatorio registrado e das contagens reais. */
    public static byte[] render(BatchView batch, Map<String, Object> report) {
        List<String> lines = new ArrayList<>();
        lines.add("Relatorio de Qualidade ISO 19157-1 - GIS-07 (P3.26)");
        lines.add("Lote: " + batch.id());
        lines.add("Arquivo: " + batch.originalFileName() + " (" + batch.sourceFormat() + ")");
        lines.add("Hash do original (SHA-256): " + batch.originalSha256());
        if (batch.convertedSha256() != null) {
            lines.add("Conversao assistida: " + batch.conversionTool() + " -> "
                    + batch.convertedFormat() + " hash " + batch.convertedSha256());
        }
        lines.add("SRS de origem: " + (batch.sourceSrsEpsg() == null
                ? "NAO REGISTRADO" : "EPSG " + batch.sourceSrsEpsg()
                + " (" + batch.srsSource() + ")"));
        lines.add("Transformacao de datum: " + batch.datumTransform()
                + "; grade do IBGE: " + (batch.ibgeGridRef() == null
                ? "nao registrada" : batch.ibgeGridRef()));
        lines.add("Tolerancia de snap do lote (m): " + batch.snapToleranceM());
        lines.add("Estado do lote: " + batch.status()
                + "; pacote IMPORT: " + (batch.changeSetId() == null
                ? "nao publicado" : batch.changeSetId().toString()));
        lines.add("");
        lines.addAll(cleaningLines(batch));
        lines.add("");
        lines.add("Elementos ISO 19157-1 (medidas registradas no relatorio do lote):");
        Map<String, Object> safeReport = report == null ? Map.of() : report;
        for (String[] element : ISO_ELEMENTS) {
            Object value = find(safeReport, element[1], element[2]);
            if (value == null) {
                lines.add("- " + element[0] + ": NAO INFORMADO no relatorio do lote");
            } else {
                lines.add("- " + element[0] + ":");
                flatten(lines, "  ", element[1], value);
            }
        }
        lines.add("");
        lines.add("Medida sem registro NAO e estimada por este relatorio:");
        lines.add("completude contra a contagem da origem, acuracia por amostra RTK");
        lines.add("e acuracia tematica por amostra de campo so aparecem acima quando");
        lines.add("registradas no relatorio do lote (gis_quality_report).");
        return assemble(paginate(lines));
    }

    /** Contagens reais da limpeza (gis_import_clean, gravadas no lote). */
    private static List<String> cleaningLines(BatchView batch) {
        List<String> lines = new ArrayList<>();
        lines.add("Limpeza topologica (contagens reais de gis_import_clean):");
        Map<String, Object> before = batch.countsBefore();
        Map<String, Object> after = batch.countsAfter();
        if (before == null || after == null) {
            lines.add("- contagens nao registradas (lote ainda nao limpo)");
            return lines;
        }
        lines.add("- antes: " + flatMap(before));
        lines.add("- depois: " + flatMap(after));
        Object beforeTotal = before.get("total");
        Object afterTotal = after.get("total");
        if (beforeTotal instanceof Number b && afterTotal instanceof Number a
                && b.doubleValue() > 0) {
            lines.add(String.format(Locale.ROOT,
                    "- aproveitamento (depois/antes): %.1f%%",
                    100.0 * a.doubleValue() / b.doubleValue()));
        }
        return lines;
    }

    private static Object find(Map<String, Object> report, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, Object> e : report.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static String flatMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(map).forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void flatten(List<String> lines, String indent, String key, Object value) {
        if (value instanceof Map<?, ?> map) {
            new TreeMap<>((Map<String, Object>) map).forEach((k, v) ->
                    flatten(lines, indent, k, v));
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                flatten(lines, indent, key, item);
            }
        } else {
            for (String part : wrap(indent + key + " = " + value)) {
                lines.add(part);
            }
        }
    }

    private static List<String> wrap(String line) {
        List<String> out = new ArrayList<>();
        String rest = line;
        while (rest.length() > MAX_LINE) {
            int cut = rest.lastIndexOf(' ', MAX_LINE);
            if (cut < 20) {
                cut = MAX_LINE;
            }
            out.add(rest.substring(0, cut));
            rest = "  " + rest.substring(cut).trim();
        }
        out.add(rest);
        return out;
    }

    private static List<List<String>> paginate(List<String> lines) {
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += LINES_PER_PAGE) {
            pages.add(lines.subList(i, Math.min(lines.size(), i + LINES_PER_PAGE)));
        }
        if (pages.isEmpty()) {
            pages.add(List.of("Relatorio de Qualidade ISO 19157-1"));
        }
        return pages;
    }

    private static byte[] assemble(List<List<String>> pages) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();
            write(out, "%PDF-1.4\n");
            int objects = 2 + pages.size() * 2;
            offsets.add(0);
            offsets.add(out.size());
            write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
            offsets.add(out.size());
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                kids.append(3 + i * 2).append(" 0 R ");
            }
            write(out, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count "
                    + pages.size() + " >>\nendobj\n");
            for (int i = 0; i < pages.size(); i++) {
                int pageObject = 3 + i * 2;
                int contentObject = pageObject + 1;
                while (offsets.size() <= contentObject) {
                    offsets.add(0);
                }
                offsets.set(pageObject, out.size());
                write(out, pageObject + " 0 obj\n<< /Type /Page /Parent 2 0 R "
                        + "/MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 << /Type /Font /Subtype /Type1 "
                        + "/BaseFont /Helvetica /Encoding /WinAnsiEncoding >> >> >> "
                        + "/Contents " + contentObject + " 0 R >>\nendobj\n");
                StringBuilder content = new StringBuilder("BT /F1 8 Tf 40 800 Td 12 TL\n");
                for (String line : pages.get(i)) {
                    content.append('(').append(escape(line)).append(") Tj T*\n");
                }
                content.append("ET");
                byte[] contentBytes = content.toString().getBytes(WIN_ANSI);
                offsets.set(contentObject, out.size());
                write(out, contentObject + " 0 obj\n<< /Length " + contentBytes.length
                        + " >>\nstream\n");
                out.write(contentBytes);
                write(out, "\nendstream\nendobj\n");
            }
            int xref = out.size();
            StringBuilder trailer = new StringBuilder("xref\n0 " + (objects + 1)
                    + "\n0000000000 65535 f \n");
            for (int i = 1; i <= objects; i++) {
                trailer.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
            }
            trailer.append("trailer\n<< /Size ").append(objects + 1)
                    .append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
            write(out, trailer.toString());
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String escape(String text) {
        StringBuilder safe = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') {
                safe.append('\\');
            }
            safe.append(WIN_ANSI.newEncoder().canEncode(c) ? c : '?');
        }
        return safe.toString();
    }

    private static void write(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(WIN_ANSI));
    }
}
