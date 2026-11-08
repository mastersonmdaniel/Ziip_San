package br.com.ziipsan.commercial.billing.servicecatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Política de assinatura por tipo de documento emitido ({@code doc.signature_policy}, ED-34),
 * nos níveis da Lei 14.063/2020 (simples, avançada, qualificada — níveis definidos pela
 * P3.34) e código verificador de autenticidade que a ED-38 expõe em certidões/declarações.
 *
 * <p>O código verificador é determinístico (SHA-256 sobre tipo+número+data de emissão,
 * TRUNCADO aos 20 primeiros hex = 80 bits — R2 da revisão cruzada: não é o hash completo):
 * conferir é recalcular; qualquer adulteração de campo muda o código. Não é assinatura
 * ICP-Brasil — o nível QUALIFIED exige a fiação da P3.34 na integração (pendência declarada).
 */
public final class SignaturePolicy {

    public enum Level { SIMPLE, ADVANCED, QUALIFIED }

    private final Map<String, Level> byDocumentType;

    public SignaturePolicy(Map<String, Level> byDocumentType) {
        if (byDocumentType == null || byDocumentType.isEmpty())
            throw new IllegalArgumentException("politica de assinatura vazia: nenhum tipo de documento coberto");
        this.byDocumentType = Map.copyOf(byDocumentType);
    }

    public Level levelFor(String documentType) {
        Level l = byDocumentType.get(documentType);
        if (l == null) throw new IllegalStateException("sem politica de assinatura para o documento " + documentType);
        return l;
    }

    public Map<String, Level> byDocumentType() { return byDocumentType; }

    public static String verificationCode(String documentType, String documentNumber, String issuedOnIso) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest((documentType + "|" + documentNumber + "|" + issuedOnIso).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, 20).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    public static boolean verifies(String documentType, String documentNumber, String issuedOnIso, String code) {
        return code != null && verificationCode(documentType, documentNumber, issuedOnIso).equalsIgnoreCase(code.trim());
    }
}
