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
 * Pedido de devolucao de debito indevido do Pix Automatico (P4.5): protocolo
 * no PSP, prazo de 24 horas do banco do pagador e desfecho com atraso
 * registrado, nunca escondido. Tabela {@code pix_auto_debit_refunds} -
 * migration pendente de numero da sequencia global na integracao.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "pix_auto_debit_refunds")
@Getter
@Setter
public class PixAutoDebitRefundEntity extends TenantAwareEntity {

    @Column(name = "instruction_id", nullable = false)
    private UUID instructionId;

    @Column(name = "charge_identifier", nullable = false, length = 80)
    private String chargeIdentifier;

    @Column(name = "psp_protocol", nullable = false, length = 80)
    private String pspProtocol;

    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    /** requested_at + 24h (regra GES-15). */
    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    /** SOLICITADA, DEVOLVIDA_NO_PRAZO, DEVOLVIDA_COM_ATRASO, VENCIDO_SEM_DEVOLUCAO. */
    @Column(name = "status", nullable = false, length = 28)
    private String status = "SOLICITADA";

    @Column(name = "completed_at")
    private Instant completedAt;
}
