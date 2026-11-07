package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.commercial.billing.content.DefaultBillContentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste do aceite da P2.8 (ADR-0022): o codigo de vinculacao ao portal
 * aparece SOMENTE nas linhas de registro do arquivo da grafica — nao no
 * cabecalho, nao no trailer, nao no manifesto (o arquivo intermediario), nao
 * no resumo de log, nao na prova de emissao e nao no toString de nenhum
 * objeto que circula fora do arquivo.
 */
class LinkingCodeLeakTest {

    private static final String SECRET_CODE = "VINC-SECRETO-SINTETICO-987654";
    private static final UUID BILL = UUID.fromString("00000000-0000-0000-0000-0000000000a9");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000e9");

    private PrintFile generated() {
        PrintFileService service = new DefaultPrintFileService(new DefaultBillContentService());
        return service.generate(PrintingFixtures.request(true,
                PrintingFixtures.bill(BILL, UNIT, "000009-9", AccessiblePrintFormat.NONE, SECRET_CODE)));
    }

    @Test
    @DisplayName("o codigo esta no arquivo final, uma vez, na linha da conta")
    void codeIsInFinalFileOnlyOnRecordLine() {
        PrintFile file = generated();
        String[] lines = file.content().split("\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).contains("VINCULO_PORTAL=" + SECRET_CODE);
        assertThat(lines[0]).doesNotContain(SECRET_CODE);
        assertThat(lines[2]).doesNotContain(SECRET_CODE);
        assertThat(file.content().split(SECRET_CODE, -1)).hasSize(2);
    }

    @Test
    @DisplayName("manifesto, resumo de log, prova e toString nao contem o codigo")
    void intermediatesAndLogsDoNotContainCode() {
        PrintFile file = generated();
        assertThat(file.manifest().toJson()).doesNotContain(SECRET_CODE);
        assertThat(file.manifest().toString()).doesNotContain(SECRET_CODE);
        assertThat(file.manifest().describeForLog()).doesNotContain(SECRET_CODE);
        assertThat(file.toString()).doesNotContain(SECRET_CODE);
        assertThat(file.proofs().toString()).doesNotContain(SECRET_CODE);
        assertThat(file.proofs().get(0).toString()).doesNotContain(SECRET_CODE);
    }

    @Test
    @DisplayName("a origem da conta e o proprio codigo tem toString redigido")
    void sourceAndCodeToStringAreRedacted() {
        PortalLinkingCode code = new PortalLinkingCode(SECRET_CODE);
        assertThat(code.toString()).isEqualTo("PortalLinkingCode[REDACTED]").doesNotContain(SECRET_CODE);
        PrintBillSource source = PrintingFixtures.bill(BILL, UNIT, "000009-9",
                AccessiblePrintFormat.NONE, SECRET_CODE);
        assertThat(source.toString()).doesNotContain(SECRET_CODE).contains("REDACTED");
    }
}
