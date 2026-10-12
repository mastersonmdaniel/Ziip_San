package br.com.sigsan.grs.mtr.service;

import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.grs.mtr.domain.WasteCdf;
import br.com.sigsan.grs.mtr.domain.WasteGenerator;
import br.com.sigsan.grs.mtr.domain.WasteMtr;
import br.com.sigsan.security.crypto.BlindIndex;
import br.com.sigsan.security.crypto.FieldCipher;
import br.com.sigsan.security.crypto.FieldProtector;
import br.com.sigsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisão S2 do plano: no PDF, CPF (motorista e gerador pessoa física) sai mascarado; CNPJ sai
 * completo. O escritor PDF é mínimo e não comprime os streams, então o texto impresso pode ser
 * procurado direto nos bytes.
 */
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
        m.setQuantityKg(new BigDecimal("10"));
        m.setVehiclePlate("ABC1D23");
        m.setIssuedAt(OffsetDateTime.now());
        documents.assignDriverCpf(m, driverCpf);
        return m;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("PDF do MTR: CPF do motorista e CPF do gerador pessoa física mascarados")
    void mtrPdfMasksCpfs() {
        String content = text(pdf.generateMtrPdf(mtr("529.982.247-25"), CPF));

        assertThat(content).doesNotContain(CPF).doesNotContain("529.982.247-25");
        assertThat(content).contains("Motorista: CPF ***.982.247-**");
        assertThat(content).contains("CNPJ/CPF ***.982.247-**");
    }

    @Test
    @DisplayName("PDF do CDF: CNPJ do gerador pessoa jurídica completo, CPF do motorista mascarado")
    void cdfPdfPrintsFullCnpj() {
        WasteCdf cdf = new WasteCdf();
        cdf.setIssuedAt(OffsetDateTime.now());
        String content = text(pdf.generateCdfPdf(cdf, mtr(CPF), CNPJ));

        assertThat(content).contains("CNPJ/CPF 11.222.333/0001-81");
        assertThat(content).doesNotContain(CPF).contains("Motorista: CPF ***.982.247-**");
    }

    @Test
    @DisplayName("sem motorista ou com documento ausente/inválido, nada é impresso no lugar do documento")
    void missingDocumentsPrintNothing() {
        String content = text(pdf.generateMtrPdf(mtr(null), null));
        assertThat(content).contains("Motorista: CPF N/A").contains("CNPJ/CPF N/A");
        assertThat(MtrPdfGenerator.printableGeneratorDocument("12345")).isEqualTo("N/A");
    }
}
