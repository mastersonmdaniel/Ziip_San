package br.com.ziipsan.platform.portal.verify;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Hash do documento da fatura (P3.35): SHA-256 sobre a forma canonica dos
 * campos que a verificacao publica confere — tenant, conta e versao, valor,
 * vencimento, competencia e recebedor (razao social e CNPJ do tenant).
 * Calculado na emissao e gravado junto do codigo de verificacao (V8_3);
 * na consulta, o servico RECALCULA a partir dos dados gravados da fatura e
 * compara em tempo constante: divergencia = documento adulterado ou dado
 * alterado depois da emissao, e a resposta e "nao autentica".
 *
 * <p>A forma canonica NAO contem dado pessoal do titular: titular, endereco,
 * documento do titular e consumo nao entram no hash nem na verificacao.
 */
public final class BillDocumentHash {

    private BillDocumentHash() { }

    public static String compute(UUID tenantId, UUID billId, int billVersionNo, BigDecimal amount,
                                 LocalDate dueDate, YearMonth referenceMonth,
                                 String receiverName, String receiverTaxId) {
        if (tenantId == null || billId == null || amount == null || dueDate == null
                || referenceMonth == null || receiverName == null || receiverName.isBlank()
                || receiverTaxId == null || receiverTaxId.isBlank()) {
            throw new IllegalArgumentException(
                    "hash do documento exige tenant, conta, valor, vencimento, competencia e recebedor");
        }
        String amountCanonical;
        try {
            amountCanonical = amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("valor da fatura com mais de 2 casas decimais nao fecha em centavos", e);
        }
        String canonical = String.join("|",
                "ziipsan/bill-document/v1",
                tenantId.toString(), billId.toString(), Integer.toString(billVersionNo),
                amountCanonical, dueDate.toString(), referenceMonth.toString(),
                receiverName.strip(), receiverTaxId.replaceAll("[^0-9A-Za-z]", ""));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }

    /** Comparacao em tempo constante de dois hashes hexadecimais. */
    public static boolean matches(String storedHash, String recomputedHash) {
        if (storedHash == null || recomputedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                recomputedHash.getBytes(StandardCharsets.UTF_8));
    }
}
