package br.com.ziipsan.platform.infra.storage;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * P0.45: assinatura AWS Signature Version 4 para o {@link S3ObjectStorage}, feita a
 * mao sobre o HttpClient do JDK — sem SDK de provedor no classpath (ver o teste de
 * fronteira). Cobre o necessario para um emulador de S3 local (SeaweedFS, perfil
 * {@code storage} do compose): PUT/GET/HEAD/DELETE com endereco por caminho
 * ({@code /<bucket>/<chave>}) e corpo com hash SHA-256. O teste nao afirma um vetor
 * externo decorado (nenhum vetor foi copiado de memoria): afirma formato do cabecalho,
 * determinismo e sensibilidade ao corpo — a prova contra o emulador e o ensaio do
 * ambiente local (docs/operacao/local-env.md), nao um numero decorado.
 */
public final class S3SigV4Signer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);

    private final String accessKey;
    private final byte[] secretKey;
    private final String region;

    public S3SigV4Signer(String accessKey, String secretKey, String region) {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()
                || region == null || region.isBlank()) {
            throw new IllegalArgumentException("credenciais S3 e regiao sao obrigatorias");
        }
        this.accessKey = accessKey;
        this.secretKey = secretKey.getBytes(StandardCharsets.UTF_8);
        this.region = region;
    }

    /** Assina e devolve os cabecalhos a enviar (Authorization, x-amz-date, x-amz-content-sha256). */
    public Map<String, String> sign(String method, String host, String path, byte[] body,
                                    java.time.Instant now) {
        String amzDate = DATE.format(now);
        String day = DAY.format(now);
        String payloadHash = HexFormat.of().formatHex(sha256(body));
        Map<String, String> headers = new TreeMap<>();
        headers.put("host", host);
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", amzDate);
        String signedHeaders = String.join(";", headers.keySet());
        String canonical = method + "\n" + encodePath(path) + "\n\n"
                + headers.entrySet().stream()
                        .map(e -> e.getKey() + ":" + e.getValue().trim() + "\n")
                        .reduce("", String::concat)
                + "\n" + signedHeaders + "\n" + payloadHash;
        String scope = day + "/" + region + "/s3/aws4_request";
        String toSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + HexFormat.of().formatHex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
        byte[] signingKey = signingKey(day);
        String signature = HexFormat.of().formatHex(hmac(signingKey, toSign.getBytes(StandardCharsets.UTF_8)));
        Map<String, String> out = new TreeMap<>(headers);
        out.put("Authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        return out;
    }

    private byte[] signingKey(String day) {
        byte[] kDate = hmac(("AWS4" + new String(secretKey, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8), day.getBytes(StandardCharsets.UTF_8));
        byte[] kRegion = hmac(kDate, region.getBytes(StandardCharsets.UTF_8));
        byte[] kService = hmac(kRegion, "s3".getBytes(StandardCharsets.UTF_8));
        return hmac(kService, "aws4_request".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("falha no HMAC-SHA256", e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("falha no SHA-256", e);
        }
    }

    private static String encodePath(String path) {
        String[] segments = path.split("/", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            if (!segments[i].isEmpty()) {
                out.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        return out.isEmpty() ? "/" : out.toString();
    }
}
