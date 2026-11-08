package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Consentimento GUARDADO da conversao ao Pix Automatico (P3.24, V6_4):
 * prova por hash SHA-256 + referencia, mesmo desenho da P4.5.
 * Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "legacy_debit_conversion_consents")
@Getter
@Setter
public class LegacyDebitConsentEntity extends TenantAwareEntity {

    @Column(name = "authorization_id", nullable = false)
    private UUID authorizationId;

    @Column(name = "enrollment_id", nullable = false, length = 40)
    private String enrollmentId;

    @Column(name = "proof_hash", nullable = false, length = 64)
    private String proofHash;

    @Column(name = "proof_reference", nullable = false, length = 200)
    private String proofReference;

    @Column(name = "channel", nullable = false, length = 40)
    private String channel;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;
}
