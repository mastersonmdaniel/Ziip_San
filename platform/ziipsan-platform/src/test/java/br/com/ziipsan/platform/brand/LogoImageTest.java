package br.com.ziipsan.platform.brand;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.19 — validação e reencode do logo (MARCA-E-TEMAS §3.2): PNG ou WebP,
 * até 512 KB, 128–2.048 px no maior lado, poliglota recusado, saída PNG
 * reencodada sem bytes do original.
 *
 * <p>O WebP de teste é um fixture sintético (sólido 200×200, lossless,
 * gerado por ffmpeg, sem dado real) em {@code src/test/resources/brand/}.
 */
class LogoImageTest {

    static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private static byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x03, 0x69, 0xA0));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] webpFixture() throws Exception {
        try (InputStream in = LogoImageTest.class.getResourceAsStream("/brand/logo-200.webp")) {
            assertThat(in).as("fixture WebP ausente").isNotNull();
            return in.readAllBytes();
        }
    }

    private static String fieldErrorCode(ApiException.Validation validation) {
        assertThat(validation.errors()).hasSize(1);
        return validation.errors().get(0).code();
    }

    @Test
    @DisplayName("PNG válido é reencodado como PNG, com dimensões e SHA-256 do resultado")
    void validPngIsReencoded() throws Exception {
        byte[] input = pngBytes(200, 200);
        LogoImage.ReencodedLogo logo = LogoImage.reencode(input);
        assertThat(logo.width()).isEqualTo(200);
        assertThat(logo.height()).isEqualTo(200);
        assertThat(Arrays.copyOf(logo.pngBytes(), 4)).containsExactly((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
        assertThat(logo.sha256()).hasSize(64);
        assertThat(logo.sha256()).isEqualTo(LogoImage.sha256Hex(logo.pngBytes()));
    }

    @Test
    @DisplayName("WebP válido é reencodado como PNG (formato canônico), sem bytes do original")
    void validWebpIsReencodedAsPng() throws Exception {
        byte[] input = webpFixture();
        assertThat(Arrays.copyOf(input, 4)).containsExactly((byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F');
        LogoImage.ReencodedLogo logo = LogoImage.reencode(input);
        assertThat(logo.width()).isEqualTo(200);
        assertThat(logo.height()).isEqualTo(200);
        assertThat(Arrays.copyOf(logo.pngBytes(), 4)).containsExactly((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
        assertThat(logo.pngBytes()).isNotEqualTo(input);
        // O PNG de saída é legível de volta.
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(logo.pngBytes()))).isNotNull();
    }

    @Test
    @DisplayName("PNG com trailer (poliglota PNG+ZIP) é recusado, não higienizado")
    void pngPolyglotIsRefused() throws Exception {
        byte[] input = pngBytes(200, 200);
        byte[] trailer = "PK\u0003\u0004conteudo-escondido".getBytes(StandardCharsets.US_ASCII);
        byte[] polyglot = Arrays.copyOf(input, input.length + trailer.length);
        System.arraycopy(trailer, 0, polyglot, input.length, trailer.length);
        assertThatThrownBy(() -> LogoImage.reencode(polyglot))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("polyglot"));
    }

    @Test
    @DisplayName("WebP com trailer (tamanho RIFF menor que o arquivo) é recusado")
    void webpPolyglotIsRefused() throws Exception {
        byte[] input = webpFixture();
        byte[] trailer = "PK\u0003\u0004conteudo-escondido".getBytes(StandardCharsets.US_ASCII);
        byte[] polyglot = Arrays.copyOf(input, input.length + trailer.length);
        System.arraycopy(trailer, 0, polyglot, input.length, trailer.length);
        assertThatThrownBy(() -> LogoImage.reencode(polyglot))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e)).isEqualTo("polyglot"));
    }

    @Test
    @DisplayName("EICAR como logo é recusado (não é imagem) e texto/GIF são recusados")
    void nonImageIsRefused() {
        assertThatThrownBy(() -> LogoImage.reencode(EICAR.getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e))
                        .isEqualTo("unsupported-type"));
        byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> LogoImage.reencode(gif))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e))
                        .isEqualTo("unsupported-type"));
    }

    @Test
    @DisplayName("lado menor que 128 px e maior que 2.048 px são recusados antes de decodificar")
    void sideLimitsAreEnforced() throws Exception {
        assertThatThrownBy(() -> LogoImage.reencode(pngBytes(64, 64)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e))
                        .isEqualTo("image-too-small"));
        assertThatThrownBy(() -> LogoImage.reencode(pngBytes(3000, 100)))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e))
                        .isEqualTo("image-too-large"));
    }

    @Test
    @DisplayName("acima de 512 KB é recusado antes de qualquer decodificação")
    void oversizeIsRefused() {
        byte[] big = new byte[LogoImage.MAX_BYTES + 1];
        assertThatThrownBy(() -> LogoImage.reencode(big))
                .isInstanceOf(ApiException.Validation.class)
                .satisfies(e -> assertThat(fieldErrorCode((ApiException.Validation) e))
                        .isEqualTo("image-too-large"));
        assertThatThrownBy(() -> LogoImage.reencode(null))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> LogoImage.reencode(new byte[0]))
                .isInstanceOf(ApiException.Validation.class);
    }
}
