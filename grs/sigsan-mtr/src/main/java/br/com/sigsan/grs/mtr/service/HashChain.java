package br.com.sigsan.grs.mtr.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Cadeia de hash do MTR: cada elo é {@code SHA-256(hashAnterior | "\n" | conteúdoCanônico)}.
 *
 * <p>Serve para <b>detectar adulteração do histórico</b>: alterar o conteúdo de qualquer elo muda o
 * hash dele e de todos os seguintes. Não é assinatura (não prova quem escreveu) e não substitui a
 * assinatura qualificada; é um selo de integridade interno.
 *
 * <p>Três decisões que a versão anterior errava:
 * <ul>
 *   <li><b>Conteúdo canônico:</b> a versão anterior serializava a entidade JPA inteira em JSON,
 *       incluindo {@code version}, {@code updatedAt} e o próprio hash anterior. O resultado dependia
 *       de campos que mudam sem alterar o manifesto e da ordem de serialização, então o hash não era
 *       reproduzível para conferência. Aqui só entra o conteúdo declarado em {@link MtrCanonical}.</li>
 *   <li><b>UTF-8 explícito:</b> {@code String.getBytes()} usa o charset da plataforma; o mesmo MTR
 *       teria hashes diferentes em máquinas diferentes.</li>
 *   <li><b>Falha alta:</b> se o SHA-256 não existir, o código antigo devolvia um UUID aleatório como
 *       "hash". Uma cadeia de integridade que aceita um valor qualquer não protege nada. Aqui lança
 *       exceção.</li>
 * </ul>
 */
public final class HashChain {

    private HashChain() {
    }

    /** Elo: hash anterior (nulo no primeiro) + conteúdo canônico → hash. */
    public static String next(String previousHash, String canonicalContent) {
        if (canonicalContent == null) {
            throw new IllegalArgumentException("O conteúdo do elo é obrigatório");
        }
        String input = (previousHash == null ? "" : previousHash) + "\n" + canonicalContent;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível: a cadeia de integridade não pode ser calculada", e);
        }
    }

    /** SHA-256 de bytes (por exemplo, o PDF do CDF), em hexadecimal minúsculo. */
    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** Um elo gravado: o conteúdo canônico e o hash que foi guardado para ele. */
    public record Link(String canonicalContent, String storedHash) {
    }

    /**
     * Confere a cadeia do início ao fim. Devolve o índice (base 0) do primeiro elo que não confere,
     * ou {@code -1} se a cadeia está íntegra.
     */
    public static int firstBrokenLink(List<Link> links) {
        String previous = null;
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            String expected = next(previous, link.canonicalContent());
            if (!expected.equals(link.storedHash())) {
                return i;
            }
            previous = link.storedHash();
        }
        return -1;
    }
}
