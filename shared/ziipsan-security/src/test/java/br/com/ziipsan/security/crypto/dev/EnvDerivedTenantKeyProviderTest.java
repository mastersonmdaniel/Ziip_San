package br.com.ziipsan.security.crypto.dev;

import br.com.ziipsan.security.crypto.CryptoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvDerivedTenantKeyProviderTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final byte[] MASTER = new byte[32];

    static {
        for (int i = 0; i < MASTER.length; i++) {
            MASTER[i] = (byte) (i * 7 + 3);
        }
    }

    @Test
    @DisplayName("HKDF-SHA256 confere com o caso de teste 1 da RFC 5869")
    void hkdfMatchesRfc5869() {
        HexFormat hex = HexFormat.of();
        byte[] ikm = hex.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = hex.parseHex("000102030405060708090a0b0c");
        byte[] info = hex.parseHex("f0f1f2f3f4f5f6f7f8f9");
        assertThat(hex.formatHex(EnvDerivedTenantKeyProvider.hkdf(ikm, salt, info, 42)))
                .isEqualTo("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
    }

    @Test
    @DisplayName("mesma chave mestra e tenant: mesmas chaves (determinístico)")
    void deterministic() {
        var p1 = new EnvDerivedTenantKeyProvider(MASTER);
        var p2 = new EnvDerivedTenantKeyProvider(MASTER.clone());
        assertThat(p1.encryptionKey(TENANT_A)).isEqualTo(p2.encryptionKey(TENANT_A));
        assertThat(p1.blindIndexKey(TENANT_A)).isEqualTo(p2.blindIndexKey(TENANT_A));
    }

    @Test
    @DisplayName("chave de cifra ≠ chave de índice, e um tenant ≠ outro")
    void keysAreSeparated() {
        var p = new EnvDerivedTenantKeyProvider(MASTER);
        assertThat(p.encryptionKey(TENANT_A)).hasSize(32).isNotEqualTo(p.blindIndexKey(TENANT_A));
        assertThat(p.encryptionKey(TENANT_A)).isNotEqualTo(p.encryptionKey(TENANT_B));
        assertThat(p.blindIndexKey(TENANT_A)).isNotEqualTo(p.blindIndexKey(TENANT_B));
    }

    @Test
    @DisplayName("P0.33: vetor fixo congelado — contextos ziipsan/enc/v1 e ziipsan/bidx/v1")
    void frozenVectorForZiipsanContexts() {
        // Vetor calculado por implementacao independente (HKDF-SHA256, RFC 5869) com a
        // chave mestra MASTER e o tenant TENANT_A. A partir da P0.33 estes valores sao
        // congelados: qualquer mudanca de contexto (ex.: voltar a "sigsan/...") quebra
        // este teste de proposito, porque invalidaria todas as chaves ja derivadas.
        var provider = new EnvDerivedTenantKeyProvider(MASTER);
        HexFormat hex = HexFormat.of();
        assertThat(hex.formatHex(provider.encryptionKey(TENANT_A)))
                .isEqualTo("be68391b60d8b9dcc663644d6688d56686579303d9cda78e6fd9246223edf037");
        assertThat(hex.formatHex(provider.blindIndexKey(TENANT_A)))
                .isEqualTo("4ddb3b13ade286c59f47587be5dc5755a393c28dc57969723667aeb50141794b");
    }

    @Test
    @DisplayName("sem chave, com tamanho errado ou Base64 inválido: falha em vez de seguir sem cifra")
    void rejectsMissingOrInvalidMasterKey() {
        assertThatThrownBy(() -> EnvDerivedTenantKeyProvider.fromBase64(null)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> EnvDerivedTenantKeyProvider.fromBase64("  ")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> EnvDerivedTenantKeyProvider.fromBase64("não é base64!")).isInstanceOf(CryptoException.class);
        String curta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> EnvDerivedTenantKeyProvider.fromBase64(curta)).isInstanceOf(CryptoException.class);
        assertThat(EnvDerivedTenantKeyProvider.fromBase64(Base64.getEncoder().encodeToString(MASTER))).isNotNull();
    }

    @Test
    @DisplayName("sem tenant não há chave")
    void requiresTenant() {
        var p = new EnvDerivedTenantKeyProvider(MASTER);
        assertThatThrownBy(() -> p.encryptionKey(null)).isInstanceOf(CryptoException.class);
    }
}
