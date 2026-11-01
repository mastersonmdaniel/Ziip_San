package br.com.ziipsan.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Cifra de campo AES-256-GCM, com chave por tenant.
 *
 * <p>Formato do valor gravado (texto, para caber em coluna {@code TEXT}):
 * {@code base64( versão(1 byte) | nonce(12 bytes) | texto cifrado + tag(16 bytes) )}.
 *
 * <p><b>Dados autenticados adicionais (AAD):</b> o par {@code tenantId + contexto} entra na tag. O
 * contexto identifica o campo (por exemplo {@code customers.document}). Assim, um valor cifrado
 * copiado para outro tenant, ou para outro campo do mesmo tenant, <b>não decifra</b>: falha na
 * verificação da tag. Isso impede que um erro de aplicação ou uma cópia direta no banco exponha o
 * dado de um tenant a outro.
 *
 * <p>O nonce é aleatório por operação (96 bits). Com a chave de um tenant, o limite seguro do GCM
 * com nonce aleatório é da ordem de 2^32 mensagens por chave; a rotação de chave precisa ser
 * tratada pelo provedor antes disso (o byte de versão existe para permitir isso).
 */
public final class FieldCipher {

    private static final byte VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final TenantKeyProvider keys;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(TenantKeyProvider keys) {
        this.keys = keys;
    }

    public String encrypt(UUID tenantId, String context, String plaintext) {
        if (plaintext == null) {
            throw new CryptoException("Valor a cifrar é obrigatório");
        }
        // Valida tenant e contexto ANTES de pedir a chave: um tenant invalido nunca chega ao provedor.
        byte[] aad = aad(tenantId, context);
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(tenantId), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer out = ByteBuffer.allocate(1 + NONCE_BYTES + sealed.length);
            out.put(VERSION).put(nonce).put(sealed);
            return Base64.getEncoder().encodeToString(out.array());
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Falha ao cifrar o campo " + context, e);
        }
    }

    public String decrypt(UUID tenantId, String context, String ciphertext) {
        if (ciphertext == null) {
            throw new CryptoException("Valor a decifrar é obrigatório");
        }
        byte[] aad = aad(tenantId, context);
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(ciphertext);
        } catch (IllegalArgumentException e) {
            throw new CryptoException("Valor cifrado malformado para o campo " + context);
        }
        if (raw.length < 1 + NONCE_BYTES + TAG_BITS / 8) {
            throw new CryptoException("Valor cifrado curto demais para o campo " + context);
        }
        if (raw[0] != VERSION) {
            throw new CryptoException("Versão de cifra desconhecida (" + raw[0] + ") no campo " + context);
        }
        try {
            ByteBuffer in = ByteBuffer.wrap(raw);
            in.get();
            byte[] nonce = new byte[NONCE_BYTES];
            in.get(nonce);
            byte[] sealed = new byte[in.remaining()];
            in.get(sealed);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(tenantId), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Tag inválida: valor adulterado, chave errada, tenant errado ou campo errado.
            throw new CryptoException("Falha ao decifrar o campo " + context
                    + " (adulterado, ou de outro tenant/campo)", e);
        }
    }

    private SecretKeySpec key(UUID tenantId) {
        byte[] k = keys.encryptionKey(tenantId);
        if (k == null || k.length != KEY_BYTES) {
            throw new CryptoException("A chave de cifra do tenant deve ter " + KEY_BYTES + " bytes");
        }
        return new SecretKeySpec(k, "AES");
    }

    private static byte[] aad(UUID tenantId, String context) {
        if (tenantId == null || context == null || context.isBlank()) {
            throw new CryptoException("Tenant e contexto do campo são obrigatórios");
        }
        return (tenantId + "|" + context).getBytes(StandardCharsets.UTF_8);
    }
}
