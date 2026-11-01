package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.grs.mtr.domain.WasteCdf;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Geração dos documentos em PDF do MTR e do CDF.
 *
 * Escritor PDF mínimo e autocontido, montado byte a byte conforme a estrutura
 * do arquivo (catálogo, páginas, streams de conteúdo, fonte, xref, trailer).
 * Não há dependência externa de biblioteca PDF.
 *
 * Chameado por MtrService.destinateMtr para produzir o PDF do CDF e o PDF do MTR.
 *
 * Documentos das partes (decisão S2 do plano): o PDF circula fora do sistema (transportador,
 * destinador, fiscalização), então o CPF do motorista e o CPF de gerador pessoa física saem
 * MASCARADOS; o CNPJ (pessoa jurídica) sai completo. O CPF do motorista vem da máscara gravada no
 * MTR; o documento do gerador é decifrado pelo chamador e a decisão de mascarar é tomada aqui, num
 * só lugar, para nenhum chamador imprimir um CPF inteiro por engano.
 */
@Slf4j
@Service
public class MtrPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int PAGE_HEIGHT = 842; // A4
    private static final int PAGE_WIDTH = 595;
    private static final int MARGIN = 50;
    private static final int LINE_HEIGHT = 16;
    private static final int FIRST_PAGE_CONTENT_START = PAGE_HEIGHT - MARGIN - 30;

    /**
     * Gera o PDF do Certificado de Destinação Final (CDF).
     */
    public byte[] generateCdfPdf(WasteCdf cdf, WasteMtr mtr, String generatorDocument) {
        if (cdf == null || mtr == null) {
            throw new IllegalArgumentException("CDF e MTR são obrigatórios para gerar o PDF do CDF");
        }
        List<String> lines = new ArrayList<>();
        lines.add("CERTIFICADO DE DESTINACAO FINAL (CDF)");
        lines.add("Lei 12.305/2010 - Politica Nacional de Residuos Solidos");
        lines.add("");
        lines.add("Numero do MTR: " + safe(mtr.getMtrNumber()));
        lines.add("Gerador: " + safe(mtr.getGenerator() != null ? mtr.getGenerator().getCorporateName() : null)
                + " - CNPJ/CPF " + printableGeneratorDocument(generatorDocument));
        lines.add("Transportador: " + safe(mtr.getTransporter() != null ? mtr.getTransporter().getCorporateName() : null)
                + " - CNPJ " + safe(mtr.getTransporter() != null ? mtr.getTransporter().getCnpj() : null)
                + " - RNTRC " + safe(mtr.getTransporter() != null ? mtr.getTransporter().getRntrc() : null));
        lines.add("Destinador: " + safe(mtr.getDestination() != null ? mtr.getDestination().getCorporateName() : null)
                + " - CNPJ " + safe(mtr.getDestination() != null ? mtr.getDestination().getCnpj() : null));
        lines.add("Classe do residuo: " + safe(mtr.getWasteClass() != null ? mtr.getWasteClass().getCode() : null)
                + " - " + safe(mtr.getWasteClass() != null ? mtr.getWasteClass().getDescription() : null));
        lines.add("Quantidade: " + safe(mtr.getQuantityKg()) + " kg");
        lines.add("Embalagem: " + safe(mtr.getPackagingType()) + " x " + safe(mtr.getPackagingCount()));
        lines.add("Placa: " + safe(mtr.getVehiclePlate()) + " - Motorista: CPF " + safe(mtr.getDriverCpfMasked()));
        lines.add("");
        lines.add("METODO DE DESTINACAO: " + safe(cdf.getDestinationMethod() != null ? cdf.getDestinationMethod().name() : null));
        lines.add("Detalhes do processo: " + safe(cdf.getProcessDetails()));
        lines.add("Responsavel tecnico: " + safe(cdf.getTechnicianName())
                + (cdf.getTechnicianCrea() != null ? " - CREA " + cdf.getTechnicianCrea() : ""));
        lines.add("Emissao: " + formatDate(cdf.getIssuedAt()));
        lines.add("");
        lines.add("Hash do documento: " + safe(cdf.getHashChain()));

        log.info("PDF do CDF gerado para o MTR {}", mtr.getMtrNumber());
        return buildPdf("CDF", lines);
    }

    /**
     * Gera o PDF do MTR para registro.
     */
    public byte[] generateMtrPdf(WasteMtr mtr, String generatorDocument) {
        if (mtr == null) {
            throw new IllegalArgumentException("MTR e obrigatorio para gerar o PDF");
        }
        List<String> lines = new ArrayList<>();
        lines.add("MANIFESTO DE TRANSPORTE DE RESIDUOS (MTR)");
        lines.add("Padrao SINIR: " + safe(mtr.getMtrNumber()));
        lines.add("");
        lines.add("Gerador: " + safe(mtr.getGenerator() != null ? mtr.getGenerator().getCorporateName() : null)
                + " - CNPJ/CPF " + printableGeneratorDocument(generatorDocument));
        lines.add("CADRI: " + safe(mtr.getGenerator() != null ? mtr.getGenerator().getCadriNumber() : null));
        lines.add("Transportador: " + safe(mtr.getTransporter() != null ? mtr.getTransporter().getCorporateName() : null)
                + " - CNPJ " + safe(mtr.getTransporter() != null ? mtr.getTransporter().getCnpj() : null));
        lines.add("RNTRC: " + safe(mtr.getTransporter() != null ? mtr.getTransporter().getRntrc() : null));
        lines.add("Destinador: " + safe(mtr.getDestination() != null ? mtr.getDestination().getCorporateName() : null)
                + " - CNPJ " + safe(mtr.getDestination() != null ? mtr.getDestination().getCnpj() : null));
        lines.add("");
        lines.add("Classe: " + safe(mtr.getWasteClass() != null ? mtr.getWasteClass().getCode() : null)
                + " - Perigosidade " + safe(mtr.getWasteClass() != null && mtr.getWasteClass().getHazardClass() != null
                        ? mtr.getWasteClass().getHazardClass().name() : null));
        lines.add("Quantidade: " + safe(mtr.getQuantityKg()) + " kg");
        lines.add("Embalagem: " + safe(mtr.getPackagingType()) + " x " + safe(mtr.getPackagingCount()));
        lines.add("Placa: " + safe(mtr.getVehiclePlate()) + " - Motorista: CPF " + safe(mtr.getDriverCpfMasked()));
        lines.add("");
        lines.add("Status: " + safe(mtr.getStatus() != null ? mtr.getStatus().name() : null));
        lines.add("Emitido em: " + formatDate(mtr.getIssuedAt()));
        lines.add("Coletado em: " + formatDate(mtr.getCollectedAt()));
        lines.add("Recebido em: " + formatDate(mtr.getReceivedAt()));
        lines.add("Destinado em: " + formatDate(mtr.getDestinedAt()));
        lines.add("");
        lines.add("Cadeia de hash: " + safe(mtr.getHashChain()));
        lines.add("Sincronizacao SINIR: " + safe(mtr.getSinirSyncStatus() != null ? mtr.getSinirSyncStatus().name() : null));

        log.info("PDF do MTR gerado: {}", mtr.getMtrNumber());
        return buildPdf("MTR", lines);
    }

    // ------------------------------------------------------------------
    // Escritor PDF minimo
    // ------------------------------------------------------------------

    private byte[] buildPdf(String title, List<String> lines) {
        List<List<String>> pages = paginate(lines);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();
            int objectCount = 2 + pages.size() * 2 + 1; // catalog, pages, (page, content) por pagina, font

            write(out, "%PDF-1.4\n");

            // 1 = Catalog
            offsets.add(out.size());
            write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            // 2 = Pages
            offsets.add(out.size());
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                if (i > 0) {
                    kids.append(' ');
                }
                kids.append(3 + i * 2).append(" 0 R");
            }
            write(out, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>\nendobj\n");

            // Paginas e streams de conteudo
            for (int i = 0; i < pages.size(); i++) {
                int pageObj = 3 + i * 2;
                int contentObj = pageObj + 1;

                offsets.add(out.size());
                write(out, pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                        + PAGE_WIDTH + " " + PAGE_HEIGHT + "] /Resources << /Font << /F1 "
                        + objectCount + " 0 R >> >> /Contents " + contentObj + " 0 R >>\nendobj\n");

                byte[] content = buildContentStream(title, i == 0, pages.get(i));
                offsets.add(out.size());
                write(out, contentObj + " 0 obj\n<< /Length " + content.length + " >>\nstream\n");
                out.write(content);
                write(out, "\nendstream\nendobj\n");
            }

            // Fonte
            offsets.add(out.size());
            write(out, objectCount + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");

            // xref
            int xrefOffset = out.size();
            write(out, "xref\n0 " + (objectCount + 1) + "\n");
            write(out, "0000000000 65535 f \n");
            for (int offset : offsets) {
                write(out, String.format("%010d 00000 n \n", offset));
            }
            write(out, "trailer\n<< /Size " + (objectCount + 1) + " /Root 1 0 R >>\nstartxref\n"
                    + xrefOffset + "\n%%EOF\n");

            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao escrever o PDF do documento", e);
        }
    }

    private byte[] buildContentStream(String title, boolean firstPage, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("BT\n");
        sb.append("/F1 14 Tf\n");
        sb.append(MARGIN).append(' ').append(firstPage ? FIRST_PAGE_CONTENT_START : FIRST_PAGE_CONTENT_START + 30).append(" Td\n");
        sb.append(escape(title)).append(" Tj\n");
        sb.append("/F1 10 Tf\n");
        sb.append("0 -" + (LINE_HEIGHT + 6) + " Td\n");

        for (String line : lines) {
            sb.append(escape(line)).append(" Tj\n");
            sb.append("0 -").append(LINE_HEIGHT).append(" Td\n");
        }
        sb.append("ET\n");

        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private List<List<String>> paginate(List<String> lines) {
        int linesPerPage = (FIRST_PAGE_CONTENT_START - MARGIN) / LINE_HEIGHT;
        if (linesPerPage < 1) {
            linesPerPage = 1;
        }
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += linesPerPage) {
            pages.add(new ArrayList<>(lines.subList(i, Math.min(lines.size(), i + linesPerPage))));
        }
        if (pages.isEmpty()) {
            pages.add(new ArrayList<>());
        }
        return pages;
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        String sanitized = text.replaceAll("[^\\x20-\\x7E]", "?");
        return "(" + sanitized
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)") + ")";
    }

    private void write(ByteArrayOutputStream out, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.ISO_8859_1);
        out.write(data);
    }

    /**
     * CNPJ completo e formatado; CPF mascarado; documento ausente ou inválido não é impresso.
     */
    static String printableGeneratorDocument(String document) {
        return switch (DocumentNumber.kind(document)) {
            case CNPJ -> DocumentNumber.format(document);
            case CPF -> DocumentNumber.mask(document);
            case INVALID -> "N/A";
        };
    }

    private String formatDate(OffsetDateTime value) {
        return value == null ? "N/A" : value.format(DATE_FORMAT);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String safe(BigDecimal value) {
        return value == null ? "N/A" : value.toPlainString();
    }

    private String safe(Integer value) {
        return value == null ? "N/A" : value.toString();
    }
}