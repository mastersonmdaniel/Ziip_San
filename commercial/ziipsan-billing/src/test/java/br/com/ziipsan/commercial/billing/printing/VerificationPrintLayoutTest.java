package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.commercial.billing.content.DefaultBillContentService;
import br.com.ziipsan.commercial.billing.content.LegalMessageEngine;
import br.com.ziipsan.commercial.billing.content.RequiredBillContent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.35 no leiaute da grafica (P2.8, leiaute v2): o codigo de verificacao
 * sai SOMENTE na linha do arquivo final, redigido em qualquer outra
 * representacao, e conta sem codigo nao entra no arquivo.
 * Massa sintetica, como nos testes da P2.8.
 */
class VerificationPrintLayoutTest {

    private static final UUID BILL = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final String VERIFY_CODE = "7K3M-9Q2X-AB4C-DE8F";

    private PrintBillSource bill(VerificationCode code) {
        return new PrintBillSource(BILL, 1, UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
                Set.of(RequiredBillContent.REGULATORY_MODEL_IDENTIFICATION,
                        RequiredBillContent.SUPPLIER_IDENTIFICATION_BLOCK,
                        RequiredBillContent.WATER_QUALITY_MONTHLY_BLOCK,
                        RequiredBillContent.ANNUAL_DISCHARGE_DECLARATION,
                        RequiredBillContent.HOLDER_PRINTED_NAME,
                        RequiredBillContent.CHARGES_DISCLOSURE,
                        RequiredBillContent.RECONNECTION_SLA_DISCLOSURE,
                        RequiredBillContent.CUT_NOTICE_DISCLOSURE),
                Set.of(RequiredBillContent.SUPPLIER_IDENTIFICATION_BLOCK),
                UNIT, "000001-1", "NOME EXEMPLO", "Endereco exemplo",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), new BigDecimal("123.45"),
                new LegalMessageEngine.EmissionContext(LocalDate.of(2026, 5, 10), null, null, 3,
                        true, true, null, null, Set.of()),
                AccessiblePrintFormat.NONE, new PortalLinkingCode("VINC-EXEMPLO-1"), code);
    }

    @Test
    void codigoDeVerificacaoSaiNaLinhaDoArquivoComLeiauteV2() {
        var service = new DefaultPrintFileService(new DefaultBillContentService());
        PrintFile file = service.generate(new PrintFileRequest(
                UUID.fromString("00000000-0000-0000-0000-00000000ba7c"),
                OffsetDateTime.of(2026, 5, 12, 10, 0, 0, 0, ZoneOffset.of("-03:00")),
                "ARESC_RES046_2016", true, List.of(bill(new VerificationCode(VERIFY_CODE)))));
        assertThat(PrintLayout.LAYOUT_VERSION).isEqualTo(2);
        assertThat(file.content()).contains("LAYOUT=2");
        assertThat(file.content()).contains("VERIFICACAO=" + VERIFY_CODE);
        // Manifesto e prova nao carregam o codigo:
        assertThat(file.manifest().toString()).doesNotContain(VERIFY_CODE);
        assertThat(file.proofs().toString()).doesNotContain(VERIFY_CODE);
    }

    @Test
    void codigoERedigidoForaDoArquivo() {
        var code = new VerificationCode(VERIFY_CODE);
        assertThat(code.toString()).doesNotContain(VERIFY_CODE).contains("REDACTED");
        assertThat(bill(code).toString()).doesNotContain(VERIFY_CODE).doesNotContain("VINC-EXEMPLO-1");
    }

    @Test
    void contaSemCodigoDeVerificacaoNaoEntraNoArquivo() {
        assertThatThrownBy(() -> bill(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verificationCode");
        assertThatThrownBy(() -> new VerificationCode(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerificationCode("ABC|DEF")).isInstanceOf(IllegalArgumentException.class);
    }
}
