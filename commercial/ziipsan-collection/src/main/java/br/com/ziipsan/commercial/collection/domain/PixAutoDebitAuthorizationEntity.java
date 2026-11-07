package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Autorizacao do pagador do Pix Automatico (P4.5), guardada COM PROVA:
 * {@code proofHash} (SHA-256 do registro da jornada) e {@code proofReference}.
 * Tabela {@code pix_auto_debit_authorizations}: a migration NAO esta nesta
 * entrega - o plano v3.0 nao reserva versao de faixa para a P4.5 (a faixa
 * {@code V6_x} do collection fecha em V6_5/P3.29) e a P4.5 vem depois da
 * P3.12, logo o numero sai da sequencia global na integracao (ver relatorio).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "pix_auto_debit_authorizations")
@Getter
@Setter
public class PixAutoDebitAuthorizationEntity extends TenantAwareEntity {

    @Column(name = "psp", nullable = false, length = 40)
    private String psp;

    @Column(name = "psp_authorization_id", length = 80)
    private String pspAuthorizationId;

    @Column(name = "payer_document", nullable = false, length = 14)
    private String payerDocument;

    @Column(name = "receiver_document", nullable = false, length = 14)
    private String receiverDocument;

    @Column(name = "unit_id")
    private UUID unitId;

    /** Jornada de autorizacao 1 a 4 (GES-15). */
    @Column(name = "journey", nullable = false)
    private int journey;

    @Column(name = "qr_code_authorization", nullable = false)
    private boolean qrCodeAuthorization;

    /** SHA-256 (64 hex) do registro da jornada de autorizacao - a prova. */
    @Column(name = "proof_hash", nullable = false, length = 64)
    private String proofHash;

    @Column(name = "proof_reference", nullable = false, length = 200)
    private String proofReference;

    @Column(name = "authorized_at", nullable = false)
    private Instant authorizedAt;

    @Column(name = "retry_foreseen", nullable = false)
    private boolean retryForeseen;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    /** Teto por debito declarado pelo pagador; nulo = sem teto declarado. */
    @Column(name = "max_amount", precision = 14, scale = 2)
    private BigDecimal maxAmount;

    /** ATIVA, CANCELADA_PELO_PAGADOR, CANCELADA_PELO_RECEBEDOR, ENCERRADA. */
    @Column(name = "status", nullable = false, length = 28)
    private String status = "ATIVA";

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
