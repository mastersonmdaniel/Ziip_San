package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.37: credenciais no cofre — expiração e rotação. Janela padrão de
 * 30 dias a partir de 07/11/2026T12:00Z: a borda é 07/12/2026T12:00Z
 * (inclusiva = EXPIRING); 1 s depois da borda = VALID; no instante da
 * expiração = EXPIRED.
 */
class CredentialMonitorTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static CredentialVault.CredentialMetadata meta(Instant expiresAt) {
        return new CredentialVault.CredentialMetadata("vault:tenants/t1/psp-pix/v3", expiresAt, NOW);
    }

    @Test
    @DisplayName("situações de expiração com a fronteira inclusiva da janela de 30 dias")
    void expiryBoundaries() {
        var window = CredentialMonitor.DEFAULT_ALERT_WINDOW;
        assertThat(CredentialMonitor.statusAt(meta(Instant.parse("2026-12-07T12:00:00Z")), NOW, window))
                .isEqualTo(CredentialMonitor.ExpiryStatus.EXPIRING);
        assertThat(CredentialMonitor.statusAt(meta(Instant.parse("2026-12-07T12:00:01Z")), NOW, window))
                .isEqualTo(CredentialMonitor.ExpiryStatus.VALID);
        assertThat(CredentialMonitor.statusAt(meta(NOW), NOW, window))
                .isEqualTo(CredentialMonitor.ExpiryStatus.EXPIRED);
        assertThat(CredentialMonitor.statusAt(meta(NOW.minusSeconds(1)), NOW, window))
                .isEqualTo(CredentialMonitor.ExpiryStatus.EXPIRED);
        assertThat(CredentialMonitor.statusAt(meta(null), NOW, window))
                .isEqualTo(CredentialMonitor.ExpiryStatus.NO_EXPIRY_DECLARED);
        assertThat(CredentialMonitor.statusAt(null, NOW, window))
                .isEqualTo(CredentialMonitor.ExpiryStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("alerta sai para EXPIRING, EXPIRED e NOT_FOUND — não para VALID nem NO_EXPIRY_DECLARED")
    void alertRule() {
        assertThat(CredentialMonitor.requiresAlert(CredentialMonitor.ExpiryStatus.EXPIRING)).isTrue();
        assertThat(CredentialMonitor.requiresAlert(CredentialMonitor.ExpiryStatus.EXPIRED)).isTrue();
        assertThat(CredentialMonitor.requiresAlert(CredentialMonitor.ExpiryStatus.NOT_FOUND)).isTrue();
        assertThat(CredentialMonitor.requiresAlert(CredentialMonitor.ExpiryStatus.VALID)).isFalse();
        assertThat(CredentialMonitor.requiresAlert(CredentialMonitor.ExpiryStatus.NO_EXPIRY_DECLARED)).isFalse();
    }

    @Test
    @DisplayName("rotação pela porta do cofre devolve referência NOVA; mesma referência é recusada")
    void rotationThroughVaultPort() {
        CredentialVault vault = new CredentialVault() {
            @Override
            public Optional<CredentialMetadata> metadata(UUID tenantId, String credentialRef) {
                return Optional.of(meta(Instant.parse("2027-05-01T00:00:00Z")));
            }

            @Override
            public RotationResult rotate(UUID tenantId, String credentialRef, Instant at) {
                return new RotationResult(credentialRef, credentialRef + "-rotated", at);
            }
        };
        CredentialVault.RotationResult result = vault.rotate(TENANT, "vault:tenants/t1/psp-pix/v3", NOW);
        assertThat(result.previousRef()).isEqualTo("vault:tenants/t1/psp-pix/v3");
        assertThat(result.newRef()).isEqualTo("vault:tenants/t1/psp-pix/v3-rotated");
        assertThat(vault.metadata(TENANT, result.newRef())).isPresent();
        assertThatThrownBy(() -> new CredentialVault.RotationResult("vault:x", "vault:x", NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("referência nova");
    }
}
