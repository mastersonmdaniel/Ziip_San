package br.com.ziipsan.commercial.billingrules;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Hash SHA-256 do artefato {@code ziipsan-billing-rules} (ED-19): o aceite
 * exige o MESMO artefato no servidor e no plugin Android, "hash conferido".
 *
 * <p>Quem confere: na montagem do pacote da rota, o servidor grava o hash do
 * JAR que ele usa em {@link RoutePackage#rulesArtifactSha256()}; o app, ao
 * baixar a rota, confere o hash do JAR embarcado no APK contra esse valor e
 * RECUSA a rota se divergir (ver docs/campo/ED-19-EMISSAO-SIMULTANEA.md e a
 * tarefa Gradle {@code verifyBillingRulesHash} em campo/billing-rules.gradle).
 * Esta classe e a funcao unica de hash dos dois lados — nao ha segunda
 * implementacao de SHA-256 no caminho da conferencia.
 */
public final class ArtifactHash {

    private ArtifactHash() {
    }

    /** SHA-256 em hexadecimal minusculo dos bytes dados (ex.: bytes do JAR). */
    public static String sha256Hex(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes para hash sao obrigatorios");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }

    /** Conferencia estrita: hash divergente e erro, nunca aviso ignoravel. */
    public static void requireMatch(String expectedSha256Hex, byte[] artifactBytes, String artifactLabel) {
        String actual = sha256Hex(artifactBytes);
        if (expectedSha256Hex == null || !expectedSha256Hex.equalsIgnoreCase(actual)) {
            throw new IllegalStateException("Hash do artefato " + artifactLabel + " diverge: esperado "
                    + expectedSha256Hex + ", calculado " + actual
                    + " — servidor e aparelho nao estao com o mesmo ziipsan-billing-rules; emissao recusada");
        }
    }
}
