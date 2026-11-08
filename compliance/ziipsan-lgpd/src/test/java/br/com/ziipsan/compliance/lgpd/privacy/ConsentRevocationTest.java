package br.com.ziipsan.compliance.lgpd.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Revogação pelo portal: finalidade pelo catálogo da P1.3 (DAD-30) e consentId conferido
 * contra o titular (P1.15). Os códigos de finalidade são dados de teste — o catálogo
 * real é o do tenant em consent_purposes.
 */
class ConsentRevocationTest {

    private static final UUID SUBJECT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final Set<String> CATALOG = Set.of("COMUNICACAO", "PESQUISA");

    private static ConsentRevocation.Consent consent(LegalBasis basis,
                                                     ConsentRevocation.ConsentStatus status,
                                                     UUID subject, String purpose) {
        return new ConsentRevocation.Consent(UUID.randomUUID(), subject, purpose, basis, status);
    }

    @Test
    @DisplayName("revoga consentimento GRANTED do próprio titular, finalidade do catálogo")
    void revokesOwnGrantedConsent() {
        ConsentRevocation.Consent consent = consent(LegalBasis.CONSENT,
                ConsentRevocation.ConsentStatus.GRANTED, SUBJECT, "COMUNICACAO");
        ConsentRevocation.Revocation revocation =
                ConsentRevocation.revoke(consent, SUBJECT, CATALOG, NOW, "PORTAL");
        assertThat(revocation.newStatus()).isEqualTo(ConsentRevocation.ConsentStatus.REVOKED);
        assertThat(revocation.consentId()).isEqualTo(consent.id());
        assertThat(revocation.revokedAt()).isEqualTo(NOW);
        assertThat(revocation.method()).isEqualTo("PORTAL");
    }

    @Test
    @DisplayName("consentimento de outro titular é recusado (P1.15)")
    void otherSubjectsConsentRefused() {
        ConsentRevocation.Consent consent = consent(LegalBasis.CONSENT,
                ConsentRevocation.ConsentStatus.GRANTED, UUID.randomUUID(), "COMUNICACAO");
        assertThatThrownBy(() -> ConsentRevocation.revoke(consent, SUBJECT, CATALOG, NOW, "PORTAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outro titular");
    }

    @Test
    @DisplayName("finalidade fora do catálogo é recusada (DAD-30)")
    void purposeOutsideCatalogRefused() {
        ConsentRevocation.Consent consent = consent(LegalBasis.CONSENT,
                ConsentRevocation.ConsentStatus.GRANTED, SUBJECT, "FINALIDADE_DESCONHECIDA");
        assertThatThrownBy(() -> ConsentRevocation.revoke(consent, SUBJECT, CATALOG, NOW, "PORTAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("catálogo");
    }

    @Test
    @DisplayName("tratamento por obrigação legal não se revoga: a base não é CONSENT")
    void nonConsentBasisRefused() {
        ConsentRevocation.Consent consent = consent(LegalBasis.LEGAL_OBLIGATION,
                ConsentRevocation.ConsentStatus.GRANTED, SUBJECT, "COMUNICACAO");
        assertThatThrownBy(() -> ConsentRevocation.revoke(consent, SUBJECT, CATALOG, NOW, "PORTAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONSENT");
    }

    @Test
    @DisplayName("consentimento já revogado não é revogado de novo (a prova não se reescreve)")
    void alreadyRevokedRefused() {
        ConsentRevocation.Consent consent = consent(LegalBasis.CONSENT,
                ConsentRevocation.ConsentStatus.REVOKED, SUBJECT, "COMUNICACAO");
        assertThatThrownBy(() -> ConsentRevocation.revoke(consent, SUBJECT, CATALOG, NOW, "PORTAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REVOKED");
    }
}
