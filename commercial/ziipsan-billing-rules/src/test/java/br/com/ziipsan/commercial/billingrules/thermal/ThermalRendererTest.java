package br.com.ziipsan.commercial.billingrules.thermal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThermalRendererTest {

    private ThermalTemplate template(ThermalDocumentType type, ThermalChannel channel) {
        return new ThermalTemplate(UUID.randomUUID(), "v3", type, channel, List.of(
                "CONTA DE AGUA",
                "Unidade: {{unitCode}}",
                "Consumo: {{consumption}} m3",
                "Total: R$ {{total}}"));
    }

    private ThermalDocument document() {
        return new ThermalDocument(Map.of(
                "unitCode", "UC-123",
                "consumption", "12",
                "total", "27,90"),
                "1234567890", "000201pixpayload6304ABCD");
    }

    @Test
    void zplHasTextBarcodeAndQr() {
        String zpl = ThermalRenderer.render(
                template(ThermalDocumentType.FIELD_BILL, ThermalChannel.ZPL), document());
        assertThat(zpl).startsWith("^XA").endsWith("^XZ");
        assertThat(zpl).contains("CONTA DE AGUA").contains("UC-123").contains("27,90");
        assertThat(zpl).contains("^BCN,90,Y,N,N^FD1234567890^FS");
        assertThat(zpl).contains("^BQN,2,6^FDQA,000201pixpayload6304ABCD^FS");
        assertThat(zpl).doesNotContain("{{");
    }

    @Test
    void cpclHasTextBarcodeAndQr() {
        String cpcl = ThermalRenderer.render(
                template(ThermalDocumentType.FIELD_BILL, ThermalChannel.CPCL), document());
        assertThat(cpcl).startsWith("! 0 200 200 ");
        assertThat(cpcl).contains("TEXT 4 0 40 30 CONTA DE AGUA");
        assertThat(cpcl).contains("BARCODE 128 2 2 90 40");
        assertThat(cpcl).contains("1234567890");
        assertThat(cpcl).contains("B QR 40");
        assertThat(cpcl).contains("MA,000201pixpayload6304ABCD");
        assertThat(cpcl).contains("ENDQR").endsWith("FORM\r\nPRINT\r\n");
    }

    @Test
    void sameRendererServesDebitAndExcessNotices() {
        // Comunicados de debito e de excesso: mesmo motor/renderizador,
        // modelo diferente (o corpo vem do modelo da P2.30 de cada tipo).
        for (ThermalDocumentType type : List.of(
                ThermalDocumentType.DEBIT_NOTICE, ThermalDocumentType.EXCESS_NOTICE)) {
            String out = ThermalRenderer.render(template(type, ThermalChannel.ZPL), document());
            assertThat(out).startsWith("^XA").contains("UC-123");
        }
    }

    @Test
    void missingVariableIsAnErrorNeverPrintedRaw() {
        ThermalDocument incomplete = new ThermalDocument(Map.of("unitCode", "UC-1"), null, null);
        assertThatThrownBy(() -> ThermalRenderer.render(
                template(ThermalDocumentType.FIELD_BILL, ThermalChannel.ZPL), incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{consumption}}");
    }

    @Test
    void zplControlCharactersInValuesAreNeutralized() {
        ThermalDocument hostile = new ThermalDocument(Map.of(
                "unitCode", "UC^XA~", "consumption", "1", "total", "1,00"), null, null);
        String zpl = ThermalRenderer.render(
                template(ThermalDocumentType.FIELD_BILL, ThermalChannel.ZPL), hostile);
        assertThat(zpl).doesNotContain("UC^XA");
        assertThat(zpl).contains("UC XA");
    }

    @Test
    void headTestPatternExistsForBothChannels() {
        assertThat(ThermalRenderer.headTestPattern(ThermalChannel.ZPL))
                .startsWith("^XA").contains("TESTE DE CABECOTE").endsWith("^XZ");
        assertThat(ThermalRenderer.headTestPattern(ThermalChannel.CPCL))
                .contains("TESTE DE CABECOTE").endsWith("PRINT\r\n");
    }
}
