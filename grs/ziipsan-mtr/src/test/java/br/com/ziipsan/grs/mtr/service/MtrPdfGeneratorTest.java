package br.com.ziipsan.grs.mtr.service;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E11: o teste foi reescrito
// porque o gerador mudou de escritor byte a byte (streams em claro, texto procurável nos
// bytes) para biblioteca PDFBox (streams comprimidos): o texto passa a ser provado por
// EXTRACÇÃO com a própria biblioteca, que é também a prova da acentuação. As asserções da
// regra S2 (CPF mascarado, CNPJ completo) mantêm-se com a mesma força.
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteCdf;
import br.com.ziipsan.grs.mtr.domain.WasteDestination;
import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MtrPdfGeneratorTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CPF = "52998224725";
    private static final String CNPJ = "11222333000181";

    private final MtrPdfGenerator pdf = new MtrPdfGenerator();
    private MtrDocumentProtection documents;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        var keys = new EnvDerivedTenantKeyProvider(new byte[32]);
        documents = new MtrDocumentProtection(new FieldProtector(new FieldCipher(keys), new BlindIndex(keys)));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private WasteMtr mtr(String driverCpf) {
        WasteMtr m = new WasteMtr();
        m.setMtrNumber("2026.00000001.TENA.01");
        WasteGenerator g = new WasteGenerator();
        g.setCorporateName("Gerador de Teste");
        m.setGenerator(g);
        WasteDestination d = new WasteDestination();
        d.setCorporateName("Destinador de Teste");
        d.setCnpj("11222333000181");
        m.setDestination(d);
        m.setQuantityKg(new BigDecimal("10"));
        m.setVehiclePlate("ABC1D23");
        m.setIssuedAt(OffsetDateTime.now());
        documents.assignDriverCpf(m, driverCpf);
        return m;
    }

    private static String text(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    @DisplayName("PDF do MTR: CPF do motorista e CPF do gerador pessoa física mascarados (S2)")
    void mtrPdfMasksCpfs() throws IOException {
        String content = text(pdf.generateMtrPdf(mtr("529.982.247-25"), CPF));

        assertThat(content).doesNotContain(CPF).doesNotContain("529.982.247-25");
        assertThat(content).contains("Motorista: CPF ***.***.247-**");
        assertThat(content).contains("CNPJ/CPF ***.***.247-**");
    }

    @Test
    @DisplayName("PDF do CDF: CNPJ do gerador pessoa jurídica completo, CPF do motorista mascarado (S2)")
    void cdfPdfPrintsFullCnpj() throws IOException {
        WasteCdf cdf = new WasteCdf();
        cdf.setIssuedAt(OffsetDateTime.now());
        cdf.setPeriodStart(LocalDate.of(2026, 10, 1));
        cdf.setPeriodEnd(LocalDate.of(2026, 10, 31));
        WasteMtr m = mtr(CPF);
        cdf.setDestination(m.getDestination());
        String content = text(pdf.generateCdfPdf(cdf, List.of(m), List.of(CNPJ)));

        assertThat(content).contains("CNPJ/CPF 11.222.333/0001-81");
        assertThat(content).doesNotContain(CPF).contains("Motorista: CPF ***.***.247-**");
    }

    @Test
    @DisplayName("sem motorista ou com documento ausente/inválido, nada é impresso no lugar do documento")
    void missingDocumentsPrintNothing() throws IOException {
        String content = text(pdf.generateMtrPdf(mtr(null), null));
        assertThat(content).contains("Motorista: CPF N/A").contains("CNPJ/CPF N/A");
        assertThat(MtrPdfGenerator.printableGeneratorDocument("12345")).isEqualTo("N/A");
    }

    @Test
    @DisplayName("ED-104/COD-33: biblioteca, acentuação pt-BR extraível e declaração PDF/A-1b com OutputIntent")
    void pdfIsLibraryMadeWithAccentsAndPdfA() throws IOException {
        WasteMtr m = mtr(CPF);
        m.getGenerator().setCorporateName("Destinação e Resíduos Acentuados Ltda");
        byte[] bytes = pdf.generateMtrPdf(m, CNPJ);

        String content = text(bytes);
        assertThat(content).contains("MANIFESTO DE TRANSPORTE DE RESÍDUOS");
        assertThat(content).contains("Destinação e Resíduos Acentuados");
        assertThat(content).contains("Sincronização SINIR");

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String xmp;
            try (java.io.InputStream xin = doc.getDocumentCatalog().getMetadata()
                    .getCOSObject().createInputStream()) {
                xmp = new String(xin.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            assertThat(xmp).contains("<pdfaid:part>1</pdfaid:part>");
            assertThat(xmp).contains("<pdfaid:conformance>B</pdfaid:conformance>");
            assertThat(doc.getDocumentCatalog().getOutputIntents()).isNotEmpty();
            // fonte embebida de verdade: o descritor da fonte da página tem o ficheiro da fonte
            var page = doc.getPage(0);
            org.apache.pdfbox.pdmodel.font.PDType0Font type0 = null;
            for (var name : page.getResources().getFontNames()) {
                var f = page.getResources().getFont(name);
                if (f instanceof org.apache.pdfbox.pdmodel.font.PDType0Font t0) {
                    type0 = t0;
                    break;
                }
            }
            assertThat(type0).isNotNull();
            assertThat(type0.getDescendantFont().getFontDescriptor().getFontFile2()).isNotNull();
        }
        assertThat(new String(bytes, 0, 8, java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF-1.4");
    }
}
