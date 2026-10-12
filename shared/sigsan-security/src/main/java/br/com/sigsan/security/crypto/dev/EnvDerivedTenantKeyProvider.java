package br.com.sigsan.security.crypto.dev;

import br.com.sigsan.security.crypto.CryptoException;
import br.com.sigsan.security.crypto.TenantKeyProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;

/**
 * Chaves de tenant derivadas de uma chave mestra, SÓ PARA DESENVOLVIMENTO E TESTE.
 *
 * <p>Deriva por HKDF-SHA256 (RFC 5869) duas chaves por tenant a partir de uma chave mestra de 32
 * bytes: salt = UUID do tenant, info = {@code sigsan/enc/v1} (cifra) ou {@code sigsan/bidx/v1}
 * (índice cego). As duas são diferentes entre si e entre tenants.
 *
 * <p>Não serve para produção: uma chave mestra única, numa variável de ambiente, protege todos os
 * tenants, sem rotação nem custódia. Produção usa o Vault (envelope com KEK no Transit; ver
 * {@link TenantKeyProvider}). Quem monta a aplicação recusa este provider em perfil de produção.
 * Até o Vault existir, só dados sintéticos.
 */
public final class EnvDerivedTenantKeyProvider implements TenantKeyProvider {

    /** Variável de ambiente com a chave mestra, em Base64 (32 bytes). Nunca no repositório. */
    public static final String ENV_VAR = "SIGSAN_DEV_MASTER_KEY";

    private static final int KEY_BYTES = 32;
    private static final byte[] INFO_ENCRYPTION = "sigsan/enc/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] INFO_BLIND_INDEX = "sigsan/bidx/v1".getBytes(StandardCharsets.US_ASCII);

    private final byte[] masterKey;

    public EnvDerivedTenantKeyProvider(byte[] masterKey) {
        if (masterKey == null || masterKey.length != KEY_BYTES) {
            throw new CryptoException("chave mestra de desenvolvimento precisa ter " + KEY_BYTES + " bytes");
        }
        this.masterKey = masterKey.clone();
    }

    /** Lê a chave mestra de {@value #ENV_VAR}; ausente ou com tamanho errado, falha. */
    public static EnvDerivedTenantKeyProvider fromEnvironment() {
        return fromBase64(System.getenv(ENV_VAR));
    }

    public static EnvDerivedTenantKeyProvider fromBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new CryptoException(ENV_VAR + " não definida: sem chave, nenhum campo protegido é gravado");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new CryptoException(ENV_VAR + " não é Base64 válido");
        }
        return new EnvDerivedTenantKeyProvider(key);
    }

    @Override
    public byte[] encryptionKey(UUID tenantId) {
        return hkdf(masterKey, salt(tenantId), INFO_ENCRYPTION, KEY_BYTES);
    }

    @Override
    public byte[] blindIndexKey(UUID tenantId) {
        return hkdf(masterKey, salt(tenantId), INFO_BLIND_INDEX, KEY_BYTES);
    }

    private static byte[] salt(UUID tenantId) {
        if (tenantId == null) {
            throw new CryptoException("sem tenant: não há chave a derivar");
        }
        return ByteBuffer.allocate(16)
                .putLong(tenantId.getMostSignificantBits())
                .putLong(tenantId.getLeastSignificantBits())
                .array();
    }

    /** HKDF-SHA256 (RFC 5869): extract + expand. */
    static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt.length == 0 ? new byte[32] : salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);

            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] okm = new byte[length];
            byte[] t = new byte[0];
            int offset = 0;
            for (int i = 1; offset < length; i++) {
                mac.update(t);
                mac.update(info);
                mac.update((byte) i);
                t = mac.doFinal();
                int n = Math.min(t.length, length - offset);
                System.arraycopy(t, 0, okm, offset, n);
                offset += n;
            }
            return okm;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("falha ao derivar chave (HKDF)", e);
        }
    }
}
