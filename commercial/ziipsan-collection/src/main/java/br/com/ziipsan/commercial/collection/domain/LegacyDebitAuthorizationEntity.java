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
 * Autorizacao legada importada do FEBRABAN 150 (P3.24, V6_4). Conferencia
 * por matricula (UNIQUE por tenant na V6_4); elegibilidade de mesmo banco
 * espelhada no CHECK da V6_4 e no estado INELEGIVEL_BANCO_DIVERSO.
 * Nao usar {@code @Builder} (Lombok descarta campos herdados).
 */
@Entity
@Table(name = "legacy_debit_authorizations")
@Getter
@Setter
public class LegacyDebitAuthorizationEntity extends TenantAwareEntity {

    @Column(name = "enrollment_id", nullable = false, length = 40)
    private String enrollmentId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "payer_document", nullable = false, length = 14)
    private String payerDocument;

    @Column(name = "payer_bank_code", nullable = false, length = 3)
    private String payerBankCode;

    @Column(name = "receiver_bank_code", nullable = false, length = 3)
    private String receiverBankCode;

    @Column(name = "legacy_reference", nullable = false, length = 200)
    private String legacyReference;

    /** IMPORTADA_NAO_CONFERIDA, ATIVA_LEGADO, INELEGIVEL_BANCO_DIVERSO, CANCELADA, CONVERTIDA_PIX. */
    @Column(name = "status", nullable = false, length = 28)
    private String status = "IMPORTADA_NAO_CONFERIDA";

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "conferred_at")
    private Instant conferredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "converted_at")
    private Instant convertedAt;
}
