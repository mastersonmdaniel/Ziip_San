package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.registry.ReportDefinition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * XLSX minimo valido (OOXML) so com o JDK: workbook de uma planilha com
 * strings inline e numeros. Sem biblioteca nova (licenca/CVE) - o Excel e o
 * LibreOffice abrem o arquivo; formatacao visual e do consumidor, nao daqui.
 */
public final class XlsxReportWriter implements ReportWriter {

    @Override
    public ExportFormat format() {
        return ExportFormat.XLSX;
    }

    @Override
    public byte[] write(ReportDefinition definition, List<Map<String, Object>> rows) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.workbook.main+xml\"/>"
                            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                            + "</Types>");
            put(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                            + "</Relationships>");
            put(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<sheets><sheet name=\"Relatorio\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                            + "</Relationships>");
            put(zip, "xl/worksheets/sheet1.xml", sheetXml(definition, rows));
        }
        return buffer.toByteArray();
    }

    private String sheetXml(ReportDefinition definition, List<Map<String, Object>> rows) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        List<String> names = definition.columns().stream().map(c -> c.name()).toList();
        xml.append(rowXml(1, names.stream().map(n -> (Object) n).toList(), true));
        int line = 2;
        for (Map<String, Object> row : rows) {
            List<Object> values = names.stream().map(name -> row.get(name)).toList();
            xml.append(rowXml(line++, values, false));
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private String rowXml(int line, List<Object> values, boolean header) {
        StringBuilder row = new StringBuilder("<row r=\"" + line + "\">");
        for (int i = 0; i < values.size(); i++) {
            String ref = columnLetters(i) + line;
            Object value = values.get(i);
            if (!header && value instanceof Number number) {
                row.append("<c r=\"").append(ref).append("\"><v>")
                        .append(number).append("</v></c>");
            } else {
                row.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                        .append(xmlEscape(ReportWriter.cellText(value))).append("</t></is></c>");
            }
        }
        return row.append("</row>").toString();
    }

    static String columnLetters(int index) {
        StringBuilder letters = new StringBuilder();
        int n = index;
        do {
            letters.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return letters.toString();
    }

    static String xmlEscape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
