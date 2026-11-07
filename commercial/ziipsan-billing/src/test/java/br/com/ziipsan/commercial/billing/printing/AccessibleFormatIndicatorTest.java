package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.commercial.billing.content.DefaultBillContentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Indicador de formato acessivel (P2.8; CAD-81/RAS-10 da P1.3): conta de
 * unidade marcada em braile ou fonte ampliada sai no arquivo com o indicador
 * do formato pedido; conta sem marcacao sai NENHUM; o manifesto conta os
 * formatos para a grafica separar a producao.
 */
class AccessibleFormatIndicatorTest {

    private final PrintFileService service = new DefaultPrintFileService(new DefaultBillContentService());

    @Test
    @DisplayName("braile, fonte ampliada e comum saem com o indicador de cada unidade")
    void indicatorPerUnitInFileAndManifest() {
        PrintFile file = service.generate(PrintingFixtures.request(true,
                PrintingFixtures.bill(UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                        UUID.fromString("00000000-0000-0000-0000-0000000000e1"), "000001-1",
                        AccessiblePrintFormat.BRAILLE, "VINC-EXEMPLO-1"),
                PrintingFixtures.bill(UUID.fromString("00000000-0000-0000-0000-0000000000a2"),
                        UUID.fromString("00000000-0000-0000-0000-0000000000e2"), "000002-2",
                        AccessiblePrintFormat.LARGE_PRINT, "VINC-EXEMPLO-2"),
                PrintingFixtures.bill(UUID.fromString("00000000-0000-0000-0000-0000000000a3"),
                        UUID.fromString("00000000-0000-0000-0000-0000000000e3"), "000003-3",
                        AccessiblePrintFormat.NONE, "VINC-EXEMPLO-3")));
        String[] lines = file.content().split("\n");
        assertThat(lines[1]).contains("ACESSIVEL=BRAILE").doesNotContain("FONTE_AMPLIADA");
        assertThat(lines[2]).contains("ACESSIVEL=FONTE_AMPLIADA");
        assertThat(lines[3]).contains("ACESSIVEL=NENHUM")
                .doesNotContain("BRAILE").doesNotContain("FONTE_AMPLIADA");
        assertThat(file.manifest().brailleCount()).isEqualTo(1);
        assertThat(file.manifest().largePrintCount()).isEqualTo(1);
        assertThat(file.manifest().normalCount()).isEqualTo(1);
        assertThat(file.manifest().recordCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("leitura do cadastro: chaves da P1.3 mapeiam; digital e desconhecido sao erro explicito")
    void fromCadastroMapping() {
        assertThat(AccessiblePrintFormat.fromCadastro("BRAILLE")).isEqualTo(AccessiblePrintFormat.BRAILLE);
        assertThat(AccessiblePrintFormat.fromCadastro("LARGE_PRINT")).isEqualTo(AccessiblePrintFormat.LARGE_PRINT);
        assertThat(AccessiblePrintFormat.fromCadastro(null)).isEqualTo(AccessiblePrintFormat.NONE);
        assertThat(AccessiblePrintFormat.fromCadastro(" ")).isEqualTo(AccessiblePrintFormat.NONE);
        assertThatThrownBy(() -> AccessiblePrintFormat.fromCadastro("AUDIO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digital");
        assertThatThrownBy(() -> AccessiblePrintFormat.fromCadastro("ACCESSIBLE_PDF"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccessiblePrintFormat.fromCadastro("LETRA_GIGANTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do catalogo");
    }
}
