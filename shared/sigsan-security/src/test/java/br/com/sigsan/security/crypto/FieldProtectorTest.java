package br.com.sigsan.security.crypto;

import br.com.sigsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldProtectorTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String FIELD = "customers.document";
    private static final String CPF = "52998224725";

    private static FieldProtector protector() {
        byte[] master = new byte[32];
        for (int i = 0; i < master.length; i++) {
            master[i] = (byte) (31 - i);
        }
        TenantKeyProvider keys = new EnvDerivedTenantKeyProvider(master);
        return new FieldProtector(new FieldCipher(keys), new BlindIndex(keys));
    }

    @Test
    @DisplayName("nem a cifra nem o índice contêm os dígitos do documento; a máscara é a do chamador")
    void protectedFormsDoNotLeakTheValue() {
        var p = protector().protect(TENANT_A, FIELD, CPF, "***.982.247-**");
        assertThat(p.ciphertext()).doesNotContain(CPF).doesNotContain("982247");
        assertThat(p.hash()).doesNotContain(CPF).hasSize(64);
        assertThat(p.masked()).isEqualTo("***.982.247-**");
    }

    @Test
    @DisplayName("ida e volta: reveal devolve o valor normalizado")
    void roundTrip() {
        FieldProtector fp = protector();
        var p = fp.protect(TENANT_A, FIELD, CPF, "m");
        assertThat(fp.reveal(TENANT_A, FIELD, p.ciphertext())).isEqualTo(CPF);
    }

    @Test
    @DisplayName("o índice é estável (busca e unicidade) e o mesmo de indexOf")
    void indexIsStable() {
        FieldProtector fp = protector();
        var p1 = fp.protect(TENANT_A, FIELD, CPF, "m");
        var p2 = fp.protect(TENANT_A, FIELD, CPF, "m");
        assertThat(p1.hash()).isEqualTo(p2.hash()).isEqualTo(fp.indexOf(TENANT_A, FIELD, CPF));
        assertThat(p1.ciphertext()).as("nonce aleatório: duas cifras do mesmo valor diferem").isNotEqualTo(p2.ciphertext());
    }

    @Test
    @DisplayName("um tenant não decifra nem casa o índice de outro")
    void tenantsAreIsolated() {
        FieldProtector fp = protector();
        var a = fp.protect(TENANT_A, FIELD, CPF, "m");
        assertThat(fp.indexOf(TENANT_B, FIELD, CPF)).isNotEqualTo(a.hash());
        assertThatThrownBy(() -> fp.reveal(TENANT_B, FIELD, a.ciphertext())).isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("sem tenant ou com valor vazio: erro, nunca grava em claro")
    void failsClosed() {
        FieldProtector fp = protector();
        assertThatThrownBy(() -> fp.protect(null, FIELD, CPF, "m")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fp.protect(TENANT_A, FIELD, " ", "m")).isInstanceOf(IllegalArgumentException.class);
    }
}
