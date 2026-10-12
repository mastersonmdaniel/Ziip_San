package br.com.sigsan.security.crypto;

import java.util.Objects;
import java.util.UUID;

/**
 * Protege um campo identificador (CPF, CNPJ, NIS) do jeito que o projeto guarda: cifrado, com índice
 * cego para busca e unicidade, e com uma máscara para exibição. Nunca o valor em claro.
 *
 * <p>Junta {@link FieldCipher} e {@link BlindIndex} para que nenhum chamador esqueça uma das três
 * partes ou use contextos diferentes na cifra e no índice. O contexto (ex.: {@code customers.document})
 * entra na AAD da cifra e no HMAC do índice: um valor protegido para um campo não decifra nem casa
 * com outro, e um tenant não decifra o de outro.
 */
public final class FieldProtector {

    /** As três formas persistidas de um campo protegido. */
    public record ProtectedValue(String ciphertext, String hash, String masked) {
    }

    private final FieldCipher cipher;
    private final BlindIndex index;

    public FieldProtector(FieldCipher cipher, BlindIndex index) {
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.index = Objects.requireNonNull(index, "index");
    }

    /**
     * @param normalized valor já normalizado (ex.: só dígitos/maiúsculas): é ele que vai para o índice,
     *                   então "529.982.247-25" e "52998224725" precisam chegar iguais aqui
     * @param masked     forma de exibição, calculada pelo chamador (que conhece o tipo do documento)
     */
    public ProtectedValue protect(UUID tenantId, String context, String normalized, String masked) {
        requireTenant(tenantId);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("valor a proteger vazio");
        }
        return new ProtectedValue(
                cipher.encrypt(tenantId, context, normalized),
                index.of(tenantId, context, normalized),
                masked);
    }

    /** Índice cego para buscar um valor (já normalizado) sem decifrar nada. */
    public String indexOf(UUID tenantId, String context, String normalized) {
        requireTenant(tenantId);
        return index.of(tenantId, context, normalized);
    }

    /** Valor em claro. Quem chama é responsável por registrar o acesso (LGPD). */
    public String reveal(UUID tenantId, String context, String ciphertext) {
        requireTenant(tenantId);
        return cipher.decrypt(tenantId, context, ciphertext);
    }

    private static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto: campo protegido não pode ser processado");
        }
    }
}
