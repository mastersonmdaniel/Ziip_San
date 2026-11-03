package br.com.ziipsan.platform.infra.scan;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.20: o tipo vem dos bytes, nunca da extensao (a extensao nem chega aqui). */
class ContentTypeDetectorTest {

    @Test
    @DisplayName("PDF, PNG, JPEG, GIF, WebP, ZIP, GZIP e XML sao detectados pela assinatura")
    void magicBytesAreDetected() {
        assertThat(ContentTypeDetector.detect("%PDF-1.7 resto".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(ContentTypeDetector.PDF);
        assertThat(ContentTypeDetector.detect(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A, 0x00}))
                .isEqualTo(ContentTypeDetector.PNG);
        assertThat(ContentTypeDetector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
                .isEqualTo(ContentTypeDetector.JPEG);
        assertThat(ContentTypeDetector.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(ContentTypeDetector.GIF);
        byte[] webp = "RIFF____WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        assertThat(ContentTypeDetector.detect(webp)).isEqualTo(ContentTypeDetector.WEBP);
        assertThat(ContentTypeDetector.detect(new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00}))
                .isEqualTo(ContentTypeDetector.ZIP);
        assertThat(ContentTypeDetector.detect(new byte[]{0x1F, (byte) 0x8B, 0x08, 0x00}))
                .isEqualTo(ContentTypeDetector.GZIP);
        assertThat(ContentTypeDetector.detect("<?xml version=\"1.0\"?><nfe/>".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ContentTypeDetector.XML);
        assertThat(ContentTypeDetector.detect("  \n <?xml version=\"1.0\"?>".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ContentTypeDetector.XML);
    }

    @Test
    @DisplayName("texto UTF-8 imprimivel e texto; binario sem assinatura e octet-stream")
    void textAndUnknownFallbacks() {
        assertThat(ContentTypeDetector.detect("oficio simples, sem acento".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ContentTypeDetector.TEXT);
        assertThat(ContentTypeDetector.detect("relatório com acentuação".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ContentTypeDetector.TEXT);
        assertThat(ContentTypeDetector.detect(new byte[]{0x00, 0x01, 0x02, (byte) 0xFF}))
                .isEqualTo(ContentTypeDetector.OCTET_STREAM);
        assertThat(ContentTypeDetector.detect(new byte[0])).isEqualTo(ContentTypeDetector.OCTET_STREAM);
        assertThat(ContentTypeDetector.detect(null)).isEqualTo(ContentTypeDetector.OCTET_STREAM);
    }

    @Test
    @DisplayName("bytes de PNG com nome .pdf seriam image/png: a extensao nao participa")
    void extensionNeverParticipates() {
        // O detector so recebe bytes: o mesmo conteudo detecta igual qualquer
        // que seja o nome do arquivo — a prova de ponta a ponta (nome .pdf com
        // bytes PNG) esta no DocumentServiceTest / DocumentPipelineIT.
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(ContentTypeDetector.detect(png)).isEqualTo(ContentTypeDetector.PNG);
    }
}
