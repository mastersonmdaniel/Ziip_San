package br.com.ziipsan.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * Envelope AES-256-GCM para o P0.28: cifra um segredo (uma DEK) com uma KEK.
 *
 * <p>Formato: {@code versao(1 byte) | nonce(12 bytes) | texto cifrado + tag(16 bytes)}.
 * Sem AAD: o vinculo com o tenant e a versao vive na linha de {@code tenant_key} (ou na
 * chave do cofre), nao no envelope — copiar um envelope para outro tenant nao adianta
 * sem a linha correspondente, e o provedor so desembrulha a linha ATIVA do tenant pedido.
 * O nonce e aleatorio por operacao.
 */
public final class AesGcmEnvelope {

    public static final byte VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcmEnvelope() {
    }

    public static byte[] wrap(byte[] kek, byte[] secret) {
        requireKey(kek, "KEK");
        if (secret == null || secret.length == 0) {
            throw new CryptoException("segredo a embrulhar e obrigatorio");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(secret);
            ByteBuffer out = ByteBuffer.allocate(1 + NONCE_BYTES + sealed.length);
            out.put(VERSION).put(nonce).put(sealed);
            return out.array();
        } catch (Exception e) {
            throw new CryptoException("falha ao embrulhar a chave com a KEK", e);
        }
    }

    public static byte[] unwrap(byte[] kek, byte[] wrapped) {
        requireKey(kek, "KEK");
        if (wrapped == null || wrapped.length < 1 + NONCE_BYTES + TAG_BITS / 8) {
            throw new CryptoException("envelope curto demais ou ausente");
        }
        if (wrapped[0] != VERSION) {
            throw new CryptoException("versao de envelope desconhecida: " + wrapped[0]);
        }
        try {
            ByteBuffer in = ByteBuffer.wrap(wrapped);
            in.get();
            byte[] nonce = new byte[NONCE_BYTES];
            in.get(nonce);
            byte[] sealed = new byte[in.remaining()];
            in.get(sealed);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(sealed);
        } catch (Exception e) {
            throw new CryptoException("falha ao desembrulhar a chave (KEK errada ou envelope adulterado)", e);
        }
    }

    private static void requireKey(byte[] key, String name) {
        if (key == null || key.length != KEY_BYTES) {
            throw new CryptoException(name + " precisa ter " + KEY_BYTES + " bytes");
        }
    }
}
