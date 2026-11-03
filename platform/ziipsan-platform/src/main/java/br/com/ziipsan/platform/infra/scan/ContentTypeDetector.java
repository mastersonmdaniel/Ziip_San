package br.com.ziipsan.platform.infra.scan;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * P1.20: tipo do arquivo detectado PELO CONTEUDO, nunca pela extensao.
 * O nome original e o tipo declarado pelo remetente sao guardados como
 * declaracao (documents.original_filename, declared_content_type); o tipo
 * que vale para o pipeline e o detectado aqui (detected_content_type).
 *
 * <p>Ordem deterministica: assinaturas binarias conhecidas, depois XML
 * (apos BOM e espacos), depois texto UTF-8 imprimivel; o resto e
 * application/octet-stream. Um arquivo chamado .pdf com bytes de PNG e
 * detectado como image/png, e um .png com bytes de PDF como application/pdf
 * (testes em ContentTypeDetectorTest).
 */
public final class ContentTypeDetector {

    public static final String PDF = "application/pdf";
    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String GIF = "image/gif";
    public static final String WEBP = "image/webp";
    public static final String ZIP = "application/zip";
    public static final String GZIP = "application/gzip";
    public static final String XML = "application/xml";
    public static final String TEXT = "text/plain";
    public static final String OCTET_STREAM = "application/octet-stream";

    private ContentTypeDetector() {
    }

    /** Detecta o tipo dos bytes. Nunca devolve null. */
    public static String detect(byte[] content) {
        if (content == null || content.length == 0) {
            return OCTET_STREAM;
        }
        if (startsWith(content, "%PDF-")) {
            return PDF;
        }
        if (startsWith(content, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return PNG;
        }
        if (startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return JPEG;
        }
        if (startsWith(content, "GIF87a") || startsWith(content, "GIF89a")) {
            return GIF;
        }
        if (content.length >= 12 && startsWith(content, "RIFF")
                && new String(content, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return WEBP;
        }
        if (startsWith(content, new byte[]{0x50, 0x4B, 0x03, 0x04})
                || startsWith(content, new byte[]{0x50, 0x4B, 0x05, 0x06})
                || startsWith(content, new byte[]{0x50, 0x4B, 0x07, 0x08})) {
            return ZIP;
        }
        if (startsWith(content, new byte[]{0x1F, (byte) 0x8B})) {
            return GZIP;
        }
        if (startsWith(skipBomAndWhitespace(content), "<?xml")) {
            return XML;
        }
        if (isPrintableUtf8(content)) {
            return TEXT;
        }
        return OCTET_STREAM;
    }

    private static boolean startsWith(byte[] content, String prefix) {
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);
        return startsWith(content, prefixBytes);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] skipBomAndWhitespace(byte[] content) {
        int i = 0;
        if (content.length >= 3 && content[0] == (byte) 0xEF && content[1] == (byte) 0xBB
                && content[2] == (byte) 0xBF) {
            i = 3;
        }
        while (i < content.length && (content[i] == ' ' || content[i] == '\t'
                || content[i] == '\n' || content[i] == '\r')) {
            i++;
        }
        return Arrays.copyOfRange(content, i, content.length);
    }

    private static boolean isPrintableUtf8(byte[] content) {
        String text;
        try {
            text = new String(content, StandardCharsets.UTF_8);
            // Decodificacao leniente do JDK substitui sequencias invalidas por
            // U+FFFD: a presenca dele indica bytes que nao sao UTF-8 valido.
            if (text.indexOf('\uFFFD') >= 0) {
                return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                return false;
            }
        }
        return true;
    }
}
