package br.com.ziipsan.commercial.registry.legacy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * P2.10 (correção A4 da revisão cruzada): construção das referências de
 * legado gravadas em {@code debit_receivables.legacy_reference}, coluna
 * {@code VARCHAR(100)} desde a V4_4 da P2.5.
 *
 * <p>O formato natural do resíduo — {@code <batchId>:<legacyKey>} — soma
 * 36 (UUID do lote) + 1 (separador) + até 100 (a {@code legacy_key} do
 * staging da P1.2 é {@code VARCHAR(100)} na V14_1) = até 137 caracteres:
 * ESTOURA a coluna no pior caso e o INSERT do débito de resíduo falharia
 * justamente nas matrículas de chave longa. A referência da prestação
 * não tem o prefixo do lote (é a referência pura do acordo, cuja coluna
 * de origem {@code legacy_agreement_ref} já é {@code VARCHAR(100)}),
 * portanto cabe — mas passa pela mesma função de teto, como defesa: se
 * a origem um dia aceitar referência maior, o débito não quebra.
 *
 * <p>Regra: referência que cabe em 100 caracteres é gravada INTACTA
 * (formato histórico, conferível contra a linhagem da P1.2). A que não
 * cabe é compactada SEM perder a rastreabilidade: mantém o prefixo
 * identificável (no resíduo, o {@code batchId} integral — o lote se
 * recupera pela linhagem da P1.2 — mais o início da chave) e acrescenta
 * {@code #} + 8 hex do SHA-256 da referência COMPLETA original, que a
 * distingue de qualquer outra chave com o mesmo prefixo.
 */
public final class LegacyReferences {

    /** Teto de {@code debit_receivables.legacy_reference} (V4_4, P2.5). */
    public static final int MAX_LEGACY_REFERENCE_LENGTH = 100;

    private static final int HASH_HEX_CHARS = 8;

    private LegacyReferences() {
    }

    /**
     * Referência do débito de resíduo: {@code <batchId>:<legacyKey>}, ou a
     * forma compactada {@code <batchId>:<54 chars da chave>#<hash8>} quando
     * a forma natural passaria de 100 caracteres. Nunca devolve mais de
     * {@link #MAX_LEGACY_REFERENCE_LENGTH} caracteres.
     */
    public static String residualReference(UUID batchId, String legacyKey) {
        if (batchId == null || legacyKey == null || legacyKey.isBlank()) {
            throw new IllegalArgumentException("batchId e legacyKey são obrigatórios"
                    + " para a referência do resíduo.");
        }
        String full = batchId + ":" + legacyKey;
        if (full.length() <= MAX_LEGACY_REFERENCE_LENGTH) {
            return full;
        }
        String batchPrefix = batchId + ":";
        int keyBudget = MAX_LEGACY_REFERENCE_LENGTH - batchPrefix.length() - 1 - HASH_HEX_CHARS;
        return batchPrefix + legacyKey.substring(0, keyBudget) + "#" + shortHash(full);
    }

    /**
     * Referência do débito de prestação: a referência do acordo do legado,
     * intacta quando cabe em 100 caracteres (é o caso de toda referência
     * vinda do staging, cuja coluna é {@code VARCHAR(100)}); acima disso,
     * {@code <91 chars da referência>#<hash8>}. Nunca devolve mais de
     * {@link #MAX_LEGACY_REFERENCE_LENGTH} caracteres.
     */
    public static String installmentReference(String legacyAgreementRef) {
        if (legacyAgreementRef == null || legacyAgreementRef.isBlank()) {
            throw new IllegalArgumentException("legacyAgreementRef é obrigatório"
                    + " para a referência da prestação.");
        }
        if (legacyAgreementRef.length() <= MAX_LEGACY_REFERENCE_LENGTH) {
            return legacyAgreementRef;
        }
        int prefixBudget = MAX_LEGACY_REFERENCE_LENGTH - 1 - HASH_HEX_CHARS;
        return legacyAgreementRef.substring(0, prefixBudget) + "#" + shortHash(legacyAgreementRef);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_HEX_CHARS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM.", e);
        }
    }
}
