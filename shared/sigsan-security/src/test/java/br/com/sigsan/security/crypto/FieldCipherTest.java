package br.com.sigsan.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCipherTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String FIELD = "customers.document";

    /** Chaves fixas e distintas por tenant. Só para teste: não é uma implementação de produção. */
    private static TenantKeyProvider provider() {
        return new TenantKeyProvider() {
            @Override
            public byte[] encryptionKey(UUID tenantId) {
                return key(tenantId, (byte) 0x11);
            }

            @Override
            public byte[] blindIndexKey(UUID tenantId) {
                return key(tenantId, (byte) 0x22);
            }

            private byte[] key(UUID tenantId, byte salt) {
                byte[] k = new byte[32];
                byte[] id = tenantId.toString().getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i < 32; i++) k[i] = (byte) (id[i % id.length] ^ salt ^ i);
                return k;
            }
        };
    }

    private final FieldCipher cipher = new FieldCipher(provider());

    @Test
    @DisplayName("cifra e decifra devolvem o texto original, inclusive com acento e emoji")
    void roundTrip() {
        for (String text : new String[]{"52998224725", "12ABC34501DE35", "José da Conceição", "", "😀 dado"}) {
            String sealed = cipher.encrypt(TENANT_A, FIELD, text);
            assertThat(cipher.decrypt(TENANT_A, FIELD, sealed)).isEqualTo(text);
        }
    }

    @Test
    @DisplayName("o valor cifrado não contém o texto claro")
    void ciphertextDoesNotLeakPlaintext() {
        String sealed = cipher.encrypt(TENANT_A, FIELD, "52998224725");
        assertThat(sealed).doesNotContain("52998224725");
        assertThat(new String(Base64.getDecoder().decode(sealed), StandardCharsets.ISO_8859_1))
                .doesNotContain("52998224725");
    }

    @Test
    @DisplayName("nonce aleatório: o mesmo texto cifrado várias vezes dá valores diferentes")
    void nonceIsRandom() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) seen.add(cipher.encrypt(TENANT_A, FIELD, "52998224725"));
        assertThat(seen).hasSize(200);
    }

    @Test
    @DisplayName("formato: versão 1, nonce de 12 bytes e tag de 16 bytes")
    void format() {
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt(TENANT_A, FIELD, "abc"));
        assertThat(raw[0]).isEqualTo((byte) 1);
        assertThat(raw.length).isEqualTo(1 + 12 + 3 + 16);
    }

    @Test
    @DisplayName("valor cifrado de um tenant não decifra em outro tenant")
    void cannotCrossTenants() {
        String sealed = cipher.encrypt(TENANT_A, FIELD, "52998224725");
        assertThatThrownBy(() -> cipher.decrypt(TENANT_B, FIELD, sealed))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("valor cifrado de um campo não decifra em outro campo do mesmo tenant")
    void cannotCrossFields() {
        String sealed = cipher.encrypt(TENANT_A, "customers.document", "52998224725");
        assertThatThrownBy(() -> cipher.decrypt(TENANT_A, "customers.phone", sealed))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("adulteração de qualquer byte é detectada")
    void tamperingIsDetected() {
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt(TENANT_A, FIELD, "52998224725"));
        for (int pos = 0; pos < raw.length; pos++) {
            byte[] bad = raw.clone();
            bad[pos] ^= 0x01;
            String tampered = Base64.getEncoder().encodeToString(bad);
            assertThatThrownBy(() -> cipher.decrypt(TENANT_A, FIELD, tampered))
                    .as("byte %d alterado", pos)
                    .isInstanceOf(CryptoException.class);
        }
    }

    @Test
    @DisplayName("entradas inválidas: base64 ruim, curto demais, versão desconhecida")
    void malformedInputs() {
        assertThatThrownBy(() -> cipher.decrypt(TENANT_A, FIELD, "###"))
                .isInstanceOf(CryptoException.class).hasMessageContaining("malformado");
        assertThatThrownBy(() -> cipher.decrypt(TENANT_A, FIELD, Base64.getEncoder().encodeToString(new byte[5])))
                .isInstanceOf(CryptoException.class).hasMessageContaining("curto");
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt(TENANT_A, FIELD, "x"));
        raw[0] = 9;
        assertThatThrownBy(() -> cipher.decrypt(TENANT_A, FIELD, Base64.getEncoder().encodeToString(raw)))
                .isInstanceOf(CryptoException.class).hasMessageContaining("Versão de cifra desconhecida");
    }

    @Test
    @DisplayName("mensagens de erro nunca carregam o texto claro")
    void errorsDoNotLeakPlaintext() {
        String sealed = cipher.encrypt(TENANT_A, FIELD, "52998224725");
        assertThatThrownBy(() -> cipher.decrypt(TENANT_B, FIELD, sealed))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("52998224725").doesNotContain(sealed));
    }

    @Test
    @DisplayName("tenant, contexto e valor nulos ou vazios são rejeitados")
    void requiredArguments() {
        assertThatThrownBy(() -> cipher.encrypt(null, FIELD, "x")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> cipher.encrypt(TENANT_A, " ", "x")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> cipher.encrypt(TENANT_A, FIELD, null)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> cipher.decrypt(TENANT_A, FIELD, null)).isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("chave com tamanho diferente de 32 bytes é recusada")
    void wrongKeyLengthIsRejected() {
        TenantKeyProvider bad = new TenantKeyProvider() {
            @Override
            public byte[] encryptionKey(UUID t) {
                return new byte[16];
            }

            @Override
            public byte[] blindIndexKey(UUID t) {
                return new byte[16];
            }
        };
        assertThatThrownBy(() -> new FieldCipher(bad).encrypt(TENANT_A, FIELD, "x"))
                .isInstanceOf(CryptoException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new BlindIndex(bad).of(TENANT_A, FIELD, "x"))
                .isInstanceOf(CryptoException.class).hasMessageContaining("32 bytes");
    }
}
