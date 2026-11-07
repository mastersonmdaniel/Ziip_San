package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.registry.ReportDefinition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PDF 1.4 minimo so com o JDK: titulo, cabecalho e linhas de texto (fonte
 * Helvetica/WinAnsi), com paginacao por linhas. Sem biblioteca nova. Texto
 * fora do WinAnsi e substituido por '?' - declarado no relatorio da tarefa.
 */
public final class PdfReportWriter implements ReportWriter {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final int LINES_PER_PAGE = 45;

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public byte[] write(ReportDefinition definition, List<Map<String, Object>> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(definition.code() + " - " + definition.title());
        lines.add(String.join(" | ", definition.columns().stream().map(c -> c.name()).toList()));
        for (Map<String, Object> row : rows) {
            lines.add(String.join(" | ", definition.columns().stream()
                    .map(c -> ReportWriter.cellText(row.get(c.name()))).toList()));
        }
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += LINES_PER_PAGE) {
            pages.add(lines.subList(i, Math.min(lines.size(), i + LINES_PER_PAGE)));
        }
        if (pages.isEmpty()) {
            pages.add(List.of(definition.title()));
        }
        return assemble(pages);
    }

    private byte[] assemble(List<List<String>> pages) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        int objects = 3 + pages.size() * 2;
        offsets.add(0);
        offsets.add(out.size());
        write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets.add(out.size());
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            kids.append(3 + i * 2).append(" 0 R ");
        }
        write(out, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>\nendobj\n");
        for (int i = 0; i < pages.size(); i++) {
            int pageObject = 3 + i * 2;
            int contentObject = pageObject + 1;
            while (offsets.size() <= contentObject) {
                offsets.add(0);
            }
            offsets.set(pageObject, out.size());
            write(out, pageObject + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding >> >> >> /Contents " + contentObject + " 0 R >>\nendobj\n");
            StringBuilder content = new StringBuilder("BT /F1 8 Tf 40 800 Td 12 TL\n");
            for (String line : pages.get(i)) {
                content.append("(").append(escape(line)).append(") Tj T*\n");
            }
            content.append("ET");
            byte[] contentBytes = content.toString().getBytes(WIN_ANSI);
            offsets.set(contentObject, out.size());
            write(out, contentObject + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
            out.write(contentBytes);
            write(out, "\nendstream\nendobj\n");
        }
        int xref = out.size();
        StringBuilder trailer = new StringBuilder("xref\n0 " + (objects + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i <= objects; i++) {
            trailer.append(String.format("%010d 00000 n \n", offsets.get(i)));
        }
        trailer.append("trailer\n<< /Size ").append(objects + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF");
        write(out, trailer.toString());
        return out.toByteArray();
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

    private void write(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(WIN_ANSI));
    }
}
