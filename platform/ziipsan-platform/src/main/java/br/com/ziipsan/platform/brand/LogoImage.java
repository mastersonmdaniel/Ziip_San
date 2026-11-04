package br.com.ziipsan.platform.brand;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.infra.scan.ContentTypeDetector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/**
 * Validação e reencode do logo do tenant (P1.19, MARCA-E-TEMAS §3.2).
 *
 * <p>Entrada aceita: PNG ou WebP, até 512 KB, de 128 a 2.048 px no maior
 * lado. Tipo conferido pelos bytes iniciais (nunca pela extensão ou pelo
 * tipo declarado). Arquivo poliglota — dados após o fim do contêiner da
 * imagem (após o IEND do PNG, ou além do tamanho RIFF do WebP) — é
 * RECUSADO, não higienizado: o reencode remove metadados e cargas
 * escondidas em chunks, mas o trailing é o poliglota clássico (PNG+ZIP) e
 * a aceitação dele seria a porta que o §3.2 manda fechar.
 *
 * <p>Saída: PNG reencodado pelo ImageIO do JDK (só pixels — sem metadados,
 * sem chunks ancilares, sem bytes do original), com dimensões e SHA-256
 * do resultado. O JDK não decodifica WebP: a leitura usa o leitor
 * TwelveMonkeys (dependência de leitura do módulo, licença BSD-3-Clause);
 * a escrita é sempre PNG, então um WebP de entrada vira PNG na saída —
 * decisão documentada: o §3.2 exige o reencode, não a preservação do
 * formato, e um único formato canônico simplifica a entrega com
 * {@code Cache-Control: immutable}. Se a preservação do WebP for exigida
 * depois, ela entra como outra frente, sem mudar esta validação.
 *
 * <p>Proteção contra bomba de descompressão: as dimensões são lidas do
 * cabeçalho (IHDR do PNG; VP8/VP8L/VP8X do WebP) e conferidas ANTES de
 * decodificar — um cabeçalho alegando mais de 2.048 px no maior lado é
 * recusado sem alocar o bitmap.
 */
public final class LogoImage {

    /** Teto do upload (MARCA-E-TEMAS §3.2): 512 KiB, sobre os bytes de entrada. */
    public static final int MAX_BYTES = 512 * 1024;

    /** Maior lado: mínimo e máximo em px (MARCA-E-TEMAS §3.2). */
    public static final int MIN_SIDE = 128;

    /** Maior lado: mínimo e máximo em px (MARCA-E-TEMAS §3.2). */
    public static final int MAX_SIDE = 2048;

    private LogoImage() {
    }

    /** Logo reencodado, pronto para gravar em {@code tenant_assets}. */
    public record ReencodedLogo(byte[] pngBytes, int width, int height, String sha256) {
    }

    /**
     * Valida e reencoda o upload. Falha de validação é
     * {@link ApiException.Validation} (422) com um erro no campo
     * {@code logo}; falha do escritor de PNG (após decodificação válida)
     * é erro interno.
     */
    public static ReencodedLogo reencode(byte[] input) {
        if (input == null || input.length == 0) {
            throw validation("logo", "empty", "O logo está vazio.");
        }
        if (input.length > MAX_BYTES) {
            throw validation("logo", "image-too-large",
                    "O logo excede 512 KB.");
        }
        String detected = ContentTypeDetector.detect(input);
        if (!ContentTypeDetector.PNG.equals(detected) && !ContentTypeDetector.WEBP.equals(detected)) {
            throw validation("logo", "unsupported-type",
                    "O logo precisa ser PNG ou WebP.");
        }
        int[] headerDims;
        if (ContentTypeDetector.PNG.equals(detected)) {
            checkPngStructure(input);
            headerDims = pngHeaderDims(input);
        } else {
            checkWebpStructure(input);
            headerDims = webpHeaderDims(input);
        }
        int maxSide = Math.max(headerDims[0], headerDims[1]);
        if (maxSide > MAX_SIDE) {
            throw validation("logo", "image-too-large",
                    "O logo excede 2.048 px no maior lado.");
        }
        if (maxSide < MIN_SIDE) {
            throw validation("logo", "image-too-small",
                    "O logo precisa ter ao menos 128 px no maior lado.");
        }
        // Garante o registro do leitor WebP mesmo com classloader incomum;
        // upload de logo é operação rara de admin, não caminho quente.
        ImageIO.scanForPlugins();
        java.awt.image.BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(input));
        } catch (IOException e) {
            throw validation("logo", "invalid-image",
                    "O logo não pôde ser lido como imagem.");
        }
        if (image == null) {
            throw validation("logo", "invalid-image",
                    "O logo não pôde ser lido como imagem.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean written;
        try {
            written = ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao reencodar o logo como PNG.", e);
        }
        if (!written) {
            throw new IllegalStateException("Nenhum escritor PNG disponível para o reencode do logo.");
        }
        byte[] png = out.toByteArray();
        return new ReencodedLogo(png, image.getWidth(), image.getHeight(), sha256Hex(png));
    }

    /** SHA-256 hexadecimal (mesmo formato da coluna {@code sha256}). */
    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível no JDK.", e);
        }
    }

    // ------------------------------------------------------------------
    // PNG
    // ------------------------------------------------------------------

    private static void checkPngStructure(byte[] input) {
        // Assinatura + caminhada por chunks: (tamanho BE, tipo, dados, CRC).
        // Trailing após o IEND = poliglota (PNG+ZIP clássico): recusado.
        long pos = 8;
        boolean sawIend = false;
        while (pos + 8 <= input.length) {
            long chunkLen = uint32Be(input, (int) pos);
            String type = new String(input, (int) pos + 4, 4, StandardCharsets.US_ASCII);
            if (pos + 12 + chunkLen > input.length) {
                throw validation("logo", "invalid-image", "O PNG está truncado.");
            }
            if ("IEND".equals(type)) {
                sawIend = true;
                if (pos + 12 + chunkLen != input.length) {
                    throw validation("logo", "polyglot",
                            "O arquivo contém dados após o fim da imagem (poliglota) e foi recusado.");
                }
                break;
            }
            pos += 12 + chunkLen;
        }
        if (!sawIend) {
            throw validation("logo", "invalid-image", "O PNG não tem o marcador de fim (IEND).");
        }
    }

    private static int[] pngHeaderDims(byte[] input) {
        // IHDR é o primeiro chunk: comprimento (4) + "IHDR" (4) + largura (4 BE) + altura (4 BE).
        if (input.length < 24 || !"IHDR".equals(new String(input, 12, 4, StandardCharsets.US_ASCII))) {
            throw validation("logo", "invalid-image", "O PNG não tem o cabeçalho IHDR.");
        }
        long w = uint32Be(input, 16);
        long h = uint32Be(input, 20);
        if (w <= 0 || h <= 0 || w > Integer.MAX_VALUE || h > Integer.MAX_VALUE) {
            throw validation("logo", "invalid-image", "As dimensões do PNG são inválidas.");
        }
        return new int[]{(int) w, (int) h};
    }

    // ------------------------------------------------------------------
    // WebP
    // ------------------------------------------------------------------

    private static void checkWebpStructure(byte[] input) {
        // RIFF: "RIFF" + tamanho LE (arquivo − 8) + "WEBP" + chunks.
        if (input.length < 20) {
            throw validation("logo", "invalid-image", "O WebP está truncado.");
        }
        long riffSize = uint32Le(input, 4);
        if (riffSize + 8 < input.length) {
            throw validation("logo", "polyglot",
                    "O arquivo contém dados após o fim da imagem (poliglota) e foi recusado.");
        }
        if (riffSize + 8 > input.length) {
            throw validation("logo", "invalid-image", "O WebP está truncado.");
        }
        String chunk = new String(input, 12, 4, StandardCharsets.US_ASCII);
        if (!"VP8 ".equals(chunk) && !"VP8L".equals(chunk) && !"VP8X".equals(chunk)) {
            throw validation("logo", "invalid-image", "O WebP não tem um chunk de imagem VP8/VP8L/VP8X.");
        }
    }

    private static int[] webpHeaderDims(byte[] input) {
        String chunk = new String(input, 12, 4, StandardCharsets.US_ASCII);
        // Dados do chunk começam no offset 20 (tamanho do chunk em 16, LE).
        if ("VP8 ".equals(chunk)) {
            // Tag (3) + magic 9D 01 2A (3) + largura 14 bits LE (2) + altura 14 bits LE (2).
            if (input.length < 30 || input[23] != (byte) 0x9D || input[24] != (byte) 0x01
                    || input[25] != (byte) 0x2A) {
                throw validation("logo", "invalid-image", "O cabeçalho VP8 é inválido.");
            }
            int w = (uint16Le(input, 26)) & 0x3FFF;
            int h = (uint16Le(input, 28)) & 0x3FFF;
            return dimsOrInvalid(w, h);
        }
        if ("VP8L".equals(chunk)) {
            // Assinatura 2F (1) + 4 bytes LE: largura−1 (14 bits) + altura−1 (14 bits).
            if (input.length < 25 || input[20] != (byte) 0x2F) {
                throw validation("logo", "invalid-image", "O cabeçalho VP8L é inválido.");
            }
            long packed = uint32Le(input, 21);
            int w = (int) (packed & 0x3FFF) + 1;
            int h = (int) ((packed >> 14) & 0x3FFF) + 1;
            return dimsOrInvalid(w, h);
        }
        // VP8X: flags (1) + reservado (3) + largura−1 LE 24 bits (3) + altura−1 LE 24 bits (3).
        if (input.length < 30) {
            throw validation("logo", "invalid-image", "O cabeçalho VP8X é inválido.");
        }
        int w = (int) uint24Le(input, 24) + 1;
        int h = (int) uint24Le(input, 27) + 1;
        return dimsOrInvalid(w, h);
    }

    private static int[] dimsOrInvalid(int w, int h) {
        if (w <= 0 || h <= 0) {
            throw validation("logo", "invalid-image", "As dimensões do WebP são inválidas.");
        }
        return new int[]{w, h};
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static long uint32Be(byte[] b, int off) {
        return ((long) (b[off] & 0xFF) << 24) | ((long) (b[off + 1] & 0xFF) << 16)
                | ((long) (b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static long uint32Le(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }

    private static int uint16Le(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static long uint24Le(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8) | ((b[off + 2] & 0xFFL) << 16);
    }

    private static ApiException.Validation validation(String field, String code, String message) {
        return ApiException.Validation.of(new ApiException.FieldError(field, code, message));
    }
}
