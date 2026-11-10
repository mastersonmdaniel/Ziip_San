package br.com.ziipsan.grs.mtr.service;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E11/COD-33.
import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.grs.mtr.domain.WasteCdf;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.springframework.stereotype.Service;

import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Geração dos documentos em PDF do MTR e do CDF — por BIBLIOTECA (ED-104, COD-33).
 *
 * <p>Substitui o escritor PDF byte a byte que aqui vivia (que substituía qualquer carácter
 * fora de ASCII por "?" — a acentuação pt-BR era destruída). Agora: Apache PDFBox, fonte
 * DejaVu Sans EMBEBIDA (recurso do módulo, licença em fonts/DejaVu-LICENSE.txt) e saída
 * declarada PDF/A-1b: metadados XMP com pdfaid:part=1/conformance=B, OutputIntent sRGB e
 * versão PDF 1.4. A conformidade é declarada e verificada por teste sobre os artefactos
 * produzidos (XMP + OutputIntent + fonte embebida); validação externa com veraPDF é ato de
 * integração (pendência P-ED104-05).
 *
 * <p>Documentos das partes (decisão S2 do plano, PRESERVADA): o PDF circula fora do sistema,
 * então o CPF do motorista e o CPF de gerador pessoa física saem MASCARADOS; o CNPJ sai
 * completo. A decisão de mascarar continua tomada aqui, num só lugar
 * ({@link #printableGeneratorDocument}).
 *
 * <p>Natureza dos documentos: o PDF do MTR é o manifesto INTERNO provisório (o oficial é o do
 * sistema emissor) e o do CDF é o registo interno do destinador (E2) — ambos o dizem em texto,
 * para nenhum papel local se apresentar como documento oficial.
 */
@Slf4j
@Service
public class MtrPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final float PAGE_WIDTH = 595f; // A4
    private static final float PAGE_HEIGHT = 842f;
    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 16f;
    private static final float TITLE_SIZE = 14f;
    private static final float BODY_SIZE = 10f;

    /** Gera o PDF do MTR interno (número provisório; o oficial é o do sistema emissor). */
    public byte[] generateMtrPdf(WasteMtr mtr, String generatorDocument) {
        if (mtr == null) {
            throw new IllegalArgumentException("MTR é obrigatório para gerar o PDF");
        }
        List<String> lines = new ArrayList<>();
        lines.add("MANIFESTO DE TRANSPORTE DE RESÍDUOS (MTR) — DOCUMENTO INTERNO");
        lines.add("Número interno provisório: " + safe(mtr.getMtrNumber()));
        lines.add("Número oficial (sistema emissor): "
                + (mtr.getOfficialMtrNumber() == null ? "ainda não atribuído" : mtr.getOfficialMtrNumber()));
        lines.add("");
        lines.add("Gerador: " + safe(mtr.getGenerator() != null ? mtr.getGenerator().getCorporateName() : null)
                + " - CNPJ/CPF " + printableGeneratorDocument(generatorDocument));
        lines.add("CADRI: " + safe(mtr.getGenerator() != null ? mtr.getGenerator().getCadriNumber() : null));
        lines.add("Transportador: " + safe(mtr.getTransporter() != null ? mtr.getTransporter().getCorporateName() : null)
                + " - CNPJ " + safe(mtr.getTransporter() != null ? mtr.getTransporter().getCnpj() : null));
        lines.add("RNTRC: " + safe(mtr.getTransporter() != null ? mtr.getTransporter().getRntrc() : null));
        lines.add("Destinador: " + safe(mtr.getDestination() != null ? mtr.getDestination().getCorporateName() : null)
                + " - CNPJ " + safe(mtr.getDestination() != null ? mtr.getDestination().getCnpj() : null));
        if (mtr.getTemporaryStorage() != null) {
            lines.add("Armazenador temporário: " + safe(mtr.getTemporaryStorage().getCorporateName())
                    + " - CNPJ " + safe(mtr.getTemporaryStorage().getCnpj()));
        }
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
        lines.add("Sincronização SINIR: " + safe(mtr.getSinirSyncStatus() != null ? mtr.getSinirSyncStatus().name() : null));
        lines.add("Base legal: Lei 12.305/2010 (PNRS); Decreto 10.936/2022; Portaria MMA 280/2020.");

        log.info("PDF do MTR gerado: {}", mtr.getMtrNumber());
        return buildPdf("MTR " + safe(mtr.getMtrNumber()), lines);
    }

    /**
     * Gera o PDF do CDF interno do destinador (E2): agrega os MTRs do período.
     *
     * @param generatorDocuments documentos dos geradores na mesma ordem de {@code mtrs}
     *                           (decifrados por quem chama; a máscara S2 é aplicada aqui)
     */
    public byte[] generateCdfPdf(WasteCdf cdf, List<WasteMtr> mtrs, List<String> generatorDocuments) {
        if (cdf == null || mtrs == null || mtrs.isEmpty()) {
            throw new IllegalArgumentException("CDF e os MTRs agregados são obrigatórios para gerar o PDF do CDF");
        }
        if (generatorDocuments == null || generatorDocuments.size() != mtrs.size()) {
            throw new IllegalArgumentException("documentos dos geradores têm de acompanhar os MTRs agregados");
        }
        List<String> lines = new ArrayList<>();
        lines.add("CERTIFICADO DE DESTINAÇÃO FINAL (CDF) — REGISTO INTERNO");
        lines.add("Lei 12.305/2010 - Política Nacional de Resíduos Sólidos");
        lines.add("Este registo interno não substitui o CDF oficial do sistema emissor (SINIR/IMA).");
        lines.add("Número oficial do CDF: "
                + (cdf.getOfficialCdfNumber() == null ? "ainda não atribuído" : cdf.getOfficialCdfNumber()));
        lines.add("Período: " + safe(cdf.getPeriodStart()) + " a " + safe(cdf.getPeriodEnd()));
        lines.add("Destinador: " + safe(cdf.getDestination() != null ? cdf.getDestination().getCorporateName() : null)
                + " - CNPJ " + safe(cdf.getDestination() != null ? cdf.getDestination().getCnpj() : null));
        lines.add("");
        lines.add("MTRs agregados (" + mtrs.size() + "):");
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < mtrs.size(); i++) {
            WasteMtr mtr = mtrs.get(i);
            total = total.add(mtr.getQuantityKg() == null ? BigDecimal.ZERO : mtr.getQuantityKg());
            lines.add("- MTR " + safe(mtr.getMtrNumber())
                    + " | Gerador: " + safe(mtr.getGenerator() != null ? mtr.getGenerator().getCorporateName() : null)
                    + " - CNPJ/CPF " + printableGeneratorDocument(generatorDocuments.get(i))
                    + " | Classe: " + safe(mtr.getWasteClass() != null ? mtr.getWasteClass().getCode() : null)
                    + " | Quantidade: " + safe(mtr.getQuantityKg()) + " kg"
                    + " | Motorista: CPF " + safe(mtr.getDriverCpfMasked()));
        }
        lines.add("Quantidade total destinada: " + total.toPlainString() + " kg");
        lines.add("");
        lines.add("Método de destinação: " + safe(cdf.getDestinationMethod() != null ? cdf.getDestinationMethod().name() : null));
        lines.add("Detalhes do processo: " + safe(cdf.getProcessDetails()));
        lines.add("Responsável técnico: " + safe(cdf.getTechnicianName())
                + (cdf.getTechnicianCrea() != null ? " - CREA " + cdf.getTechnicianCrea() : ""));
        lines.add("Emissão: " + formatDate(cdf.getIssuedAt()));
        lines.add("");
        lines.add("Hash do documento: " + safe(cdf.getHashChain()));

        log.info("PDF do CDF gerado para {} MTRs do período {} a {}", mtrs.size(), cdf.getPeriodStart(), cdf.getPeriodEnd());
        return buildPdf("CDF " + safe(cdf.getPeriodStart()) + " a " + safe(cdf.getPeriodEnd()), lines);
    }

    // ------------------------------------------------------------------
    // Construção PDF/A-1b com PDFBox
    // ------------------------------------------------------------------

    private byte[] buildPdf(String title, List<String> lines) {
        try (PDDocument doc = new PDDocument()) {
            doc.setVersion(1.4f);
            PDFont font = loadFont(doc, "/fonts/DejaVuSans.ttf");
            PDFont fontBold = loadFont(doc, "/fonts/DejaVuSans-Bold.ttf");

            List<List<String>> pages = paginate(lines);
            for (List<String> pageLines : pages) {
                PDPage page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    float y = PAGE_HEIGHT - MARGIN;
                    cs.beginText();
                    cs.setFont(fontBold, TITLE_SIZE);
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText(title);
                    cs.endText();
                    y -= LINE_HEIGHT + 10;
                    for (String line : pageLines) {
                        cs.beginText();
                        cs.setFont(font, BODY_SIZE);
                        cs.newLineAtOffset(MARGIN, y);
                        cs.showText(line);
                        cs.endText();
                        y -= LINE_HEIGHT;
                    }
                }
            }

            addPdfAMetadata(doc, title);
            addOutputIntent(doc);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return pinPdf14Header(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao escrever o PDF do documento", e);
        }
    }

    /**
     * O PDFBox só SOBE a versão do documento (nunca desce dos seus 1.6), mas o PDF/A-1 é
     * definido sobre o PDF 1.4 e todas as funcionalidades usadas aqui (fonte Type0 embebida,
     * XMP, OutputIntent) existem no 1.4. A versão é afixada por substituição de MESMO
     * COMPRIMENTO no cabeçalho e na entrada /Version do catálogo — os offsets do xref não
     * se movem. Se um dia a biblioteca passar a usar um recurso pós-1.4, este passo tem de
     * ser revisto antes de continuar a declarar 1.4.
     */
    static byte[] pinPdf14Header(byte[] pdf) {
        String raw = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        String pinned = raw.replaceFirst("%PDF-1\\.\\d", "%PDF-1.4")
                .replace("/Version /1.6", "/Version /1.4");
        return pinned.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private PDFont loadFont(PDDocument doc, String resource) throws IOException {
        try (InputStream in = MtrPdfGenerator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("fonte embebida não encontrada no classpath: " + resource);
            }
            return PDType0Font.load(doc, in, true);
        }
    }

    /** XMP com a identificação PDF/A-1b (pdfaid:part=1, conformance=B). */
    private void addPdfAMetadata(PDDocument doc, String title) throws IOException {
        String xmp = "<?xpacket begin=\"\ufeff\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                + "<rdf:Description rdf:about=\"\" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\">"
                + "<pdfaid:part>1</pdfaid:part><pdfaid:conformance>B</pdfaid:conformance>"
                + "</rdf:Description>"
                + "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                + "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">" + escapeXml(title)
                + "</rdf:li></rdf:Alt></dc:title></rdf:Description>"
                + "</rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";
        PDMetadata metadata = new PDMetadata(doc);
        metadata.importXMPMetadata(xmp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        doc.getDocumentCatalog().setMetadata(metadata);
        doc.getDocumentInformation().setTitle(title);
    }

    /** OutputIntent sRGB (perfil ICC embutido a partir do perfil sRGB padrão do JDK). */
    private void addOutputIntent(PDDocument doc) throws IOException {
        ICC_Profile icc = ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB);
        PDOutputIntent intent = new PDOutputIntent(doc, new ByteArrayInputStream(icc.getData()));
        intent.setInfo("sRGB IEC61966-2.1");
        intent.setOutputCondition("sRGB IEC61966-2.1");
        intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
        intent.setRegistryName("http://www.color.org");
        doc.getDocumentCatalog().setOutputIntents(List.of(intent));
        // COSName import mantido para leitura do catálogo em testes/leitores externos.
        if (COSName.OUTPUT_INTENTS == null) {
            throw new IllegalStateException("PDFBox sem suporte a OutputIntents");
        }
    }

    private List<List<String>> paginate(List<String> lines) {
        int linesPerPage = Math.max(1, (int) ((PAGE_HEIGHT - 2 * MARGIN - 30) / LINE_HEIGHT));
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += linesPerPage) {
            pages.add(new ArrayList<>(lines.subList(i, Math.min(lines.size(), i + linesPerPage))));
        }
        if (pages.isEmpty()) {
            pages.add(new ArrayList<>());
        }
        return pages;
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * CNPJ completo e formatado; CPF mascarado; documento ausente ou inválido não é impresso.
     * (Decisão S2 — comportamento preservado do gerador anterior.)
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

    private String safe(Object value) {
        return value == null ? "N/A" : value.toString();
    }

    private String safe(BigDecimal value) {
        return value == null ? "N/A" : value.toPlainString();
    }

    private String safe(Integer value) {
        return value == null ? "N/A" : value.toString();
    }
}
