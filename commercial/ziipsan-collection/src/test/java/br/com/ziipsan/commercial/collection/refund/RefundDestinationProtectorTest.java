package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.TenantKeyProvider;
import br.com.ziipsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.29 - o destino so existe cifrado; retencao definida e obrigatoria. */
class RefundDestinationProtectorTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static RefundDestinationProtector protector() {
        byte[] master = new byte[32];
        for (int i = 0; i < master.length; i++) {
            master[i] = (byte) (i + 1);
        }
        TenantKeyProvider keys = new EnvDerivedTenantKeyProvider(master);
        return new RefundDestinationProtector(new FieldCipher(keys));
    }

    @Test
    void destinoGravadoSomenteCifradoEReveladoSoParaOMesmoTenant() {
        var p = protector();
        // Chave Pix de teste (CPF valido 529.982.247-25): nunca aparece no cifrado.
        var protectedDest = p.protect(TENANT, Map.of("pixKey", "52998224725"),
                LocalDate.of(2031, 1, 31));
        assertThat(protectedDest.ciphertext()).doesNotContain("52998224725");
        assertThat(protectedDest.retentionUntil()).isEqualTo(LocalDate.of(2031, 1, 31));
        assertThat(p.reveal(TENANT, protectedDest.ciphertext())).containsEntry("pixKey", "52998224725");
        assertThatThrownBy(() -> p.reveal(OTHER, protectedDest.ciphertext()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void semRetencaoDefinidaNaoGravaDestino() {
        assertThatThrownBy(() -> protector().protect(TENANT, Map.of("pixKey", "52998224725"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retencao");
    }

    @Test
    void revelacaoAposARetencaoERecusadaDadoVencidoNaoDecifra() {
        var p = protector();
        var protectedDest = p.protect(TENANT, Map.of("pixKey", "52998224725"),
                LocalDate.of(2031, 1, 31));
        // Dentro da retencao (inclusive no ultimo dia): revela.
        assertThat(p.reveal(TENANT, protectedDest.ciphertext(), protectedDest.retentionUntil(),
                LocalDate.of(2031, 1, 31))).containsEntry("pixKey", "52998224725");
        // Depois da retencao (2031-02-01 > 2031-01-31): recusa, sem decifrar.
        assertThatThrownBy(() -> p.reveal(TENANT, protectedDest.ciphertext(),
                protectedDest.retentionUntil(), LocalDate.of(2031, 2, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retencao");
        // Sem retencao gravada, a revelacao com prazo tambem e recusada.
        assertThatThrownBy(() -> p.reveal(TENANT, protectedDest.ciphertext(), null,
                LocalDate.of(2030, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
