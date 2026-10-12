package br.com.sigsan.security.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BlindIndexTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

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

    private final BlindIndex index = new BlindIndex(provider());

    @Test
    @DisplayName("HMAC-SHA-256 confere com o vetor de teste 2 da RFC 4231")
    void hmacMatchesRfc4231TestCase2() {
        // RFC 4231, caso 2: chave "Jefe", dado "what do ya want for nothing?"
        String expected = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";
        assertThat(BlindIndex.hmacSha256Hex("Jefe".getBytes(StandardCharsets.US_ASCII),
                "what do ya want for nothing?")).isEqualTo(expected);
    }

    @Test
    @DisplayName("é determinístico: o mesmo valor, tenant e contexto dão o mesmo índice")
    void deterministic() {
        assertThat(index.of(TENANT_A, "customers.document", "52998224725"))
                .isEqualTo(index.of(TENANT_A, "customers.document", "52998224725"));
    }

    @Test
    @DisplayName("64 caracteres hexadecimais minúsculos")
    void shape() {
        assertThat(index.of(TENANT_A, "customers.document", "52998224725")).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("o mesmo documento em tenants diferentes dá índices diferentes")
    void differsByTenant() {
        assertThat(index.of(TENANT_A, "customers.document", "52998224725"))
                .isNotEqualTo(index.of(TENANT_B, "customers.document", "52998224725"));
    }

    @Test
    @DisplayName("o mesmo valor em contextos diferentes dá índices diferentes")
    void differsByContext() {
        assertThat(index.of(TENANT_A, "customers.document", "52998224725"))
                .isNotEqualTo(index.of(TENANT_A, "customers.phone", "52998224725"));
    }

    @Test
    @DisplayName("valores diferentes dão índices diferentes; o índice não é o SHA-256 simples")
    void differsByValueAndIsKeyed() throws Exception {
        String a = index.of(TENANT_A, "customers.document", "52998224725");
        String b = index.of(TENANT_A, "customers.document", "52998224726");
        assertThat(a).isNotEqualTo(b);

        // Um SHA-256 simples seria reproduzível por qualquer um que conheça o CPF candidato.
        byte[] sha = java.security.MessageDigest.getInstance("SHA-256")
                .digest("customers.document|52998224725".getBytes(StandardCharsets.UTF_8));
        assertThat(a).isNotEqualTo(java.util.HexFormat.of().formatHex(sha));
    }

    @Test
    @DisplayName("normalização é responsabilidade de quem chama: formatos diferentes dão índices diferentes")
    void doesNotNormalize() {
        assertThat(index.of(TENANT_A, "customers.document", "529.982.247-25"))
                .isNotEqualTo(index.of(TENANT_A, "customers.document", "52998224725"));
    }
}
