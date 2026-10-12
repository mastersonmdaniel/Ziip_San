package br.com.sigsan.security.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Índice cego: HMAC-SHA-256 do valor normalizado, com chave por tenant.
 *
 * <p>Serve para <b>buscar e garantir unicidade sem decifrar</b> (por exemplo, "já existe cliente
 * com este CPF?"). O banco guarda só o hash; quem não tem a chave do tenant não consegue calcular
 * o hash de um CPF para testar candidatos, o que um SHA-256 simples permitiria (o espaço de CPFs é
 * pequeno e enumerável).
 *
 * <p>Limitação inerente: o índice só permite igualdade exata. Quem chama é responsável por
 * normalizar o valor antes (por exemplo, só dígitos e letras maiúsculas num documento), senão
 * "123.456.789-09" e "12345678909" gerariam índices diferentes. O {@code contexto} separa os
 * espaços de valores: o mesmo número em campos diferentes dá índices diferentes.
 */
public final class BlindIndex {

    private final TenantKeyProvider keys;

    public BlindIndex(TenantKeyProvider keys) {
        this.keys = keys;
    }

    /** 64 caracteres hexadecimais minúsculos. */
    public String of(UUID tenantId, String context, String normalizedValue) {
        if (tenantId == null || context == null || context.isBlank() || normalizedValue == null) {
            throw new CryptoException("Tenant, contexto e valor são obrigatórios para o índice cego");
        }
        byte[] key = keys.blindIndexKey(tenantId);
        if (key == null || key.length != 32) {
            throw new CryptoException("A chave do índice cego do tenant deve ter 32 bytes");
        }
        return hmacSha256Hex(key, context + "|" + normalizedValue);
    }

    static String hmacSha256Hex(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Falha ao calcular o índice cego", e);
        }
    }
}
