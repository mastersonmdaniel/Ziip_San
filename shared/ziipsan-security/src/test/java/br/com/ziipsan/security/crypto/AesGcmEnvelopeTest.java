package br.com.ziipsan.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P0.28: envelope KEK/DEK (AES-256-GCM). */
class AesGcmEnvelopeTest {

    private static final byte[] KEK = new byte[32];
    private static final byte[] OTHER_KEK = new byte[32];
    private static final byte[] SECRET = new byte[32];

    static {
        for (int i = 0; i < 32; i++) {
            KEK[i] = (byte) (i + 1);
            OTHER_KEK[i] = (byte) (i + 50);
            SECRET[i] = (byte) (i * 3 + 7);
        }
    }

    @Test
    @DisplayName("embrulhar e desembrulhar com a mesma KEK devolve o segredo")
    void roundTrip() {
        byte[] wrapped = AesGcmEnvelope.wrap(KEK, SECRET);
        assertThat(wrapped).isNotEqualTo(SECRET);
        assertThat(wrapped[0]).isEqualTo(AesGcmEnvelope.VERSION);
        assertThat(AesGcmEnvelope.unwrap(KEK, wrapped)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("KEK errada ou envelope adulterado: desembrulhar falha, sem devolver nada")
    void wrongKekOrTamperFails() {
        byte[] wrapped = AesGcmEnvelope.wrap(KEK, SECRET);
        assertThatThrownBy(() -> AesGcmEnvelope.unwrap(OTHER_KEK, wrapped))
                .isInstanceOf(CryptoException.class);
        byte[] tampered = wrapped.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThatThrownBy(() -> AesGcmEnvelope.unwrap(KEK, tampered))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("dois embrulhos do mesmo segredo diferem (nonce aleatorio)")
    void nonceIsRandom() {
        assertThat(AesGcmEnvelope.wrap(KEK, SECRET)).isNotEqualTo(AesGcmEnvelope.wrap(KEK, SECRET));
    }
}
