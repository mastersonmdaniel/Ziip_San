package br.com.ziipsan.commercial.collection.pixauto;

import br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Devolucao de debito indevido (GES-15): identificado um debito indevido, o
 * BANCO DO PAGADOR devolve em ate 24 horas
 * ({@link PixAutoDebitRules#UNDUE_DEBIT_REFUND_HOURS}). O Ziip SAN nao devolve
 * por conta propria: ele protocola o pedido no PSP, guarda o prazo e marca o
 * estouro - devolucao concluida depois do prazo fica registrada como
 * COM_ATRASO, e pedido vencido sem devolucao como VENCIDO_SEM_DEVOLUCAO,
 * nunca como cumprido.
 */
public final class UndueDebitRefund {

    public enum Status { SOLICITADA, DEVOLVIDA_NO_PRAZO, DEVOLVIDA_COM_ATRASO, VENCIDO_SEM_DEVOLUCAO }

    private final UUID id;
    private final UUID instructionId;
    private final String chargeIdentifier;
    private final String pspProtocol;
    private final String reason;
    private final Instant requestedAt;
    private final Instant deadline;
    private Status status = Status.SOLICITADA;
    private Instant completedAt;

    public UndueDebitRefund(UUID id, UUID instructionId, String chargeIdentifier, String pspProtocol,
                            String reason, Instant requestedAt) {
        this.id = Objects.requireNonNull(id, "id da devolucao e obrigatorio");
        this.instructionId = Objects.requireNonNull(instructionId, "instrucao da devolucao e obrigatoria");
        if (chargeIdentifier == null || chargeIdentifier.isBlank()) {
            throw new IllegalArgumentException("identificador de cobranca da devolucao e obrigatorio");
        }
        this.chargeIdentifier = chargeIdentifier;
        if (pspProtocol == null || pspProtocol.isBlank()) {
            throw new IllegalArgumentException("protocolo do PSP da devolucao e obrigatorio");
        }
        this.pspProtocol = pspProtocol;
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("motivo do debito indevido e obrigatorio");
        }
        this.reason = reason;
        this.requestedAt = Objects.requireNonNull(requestedAt, "instante do pedido e obrigatorio");
        this.deadline = requestedAt.plusSeconds(PixAutoDebitRules.UNDUE_DEBIT_REFUND_HOURS * 3600L);
    }

    public void complete(Instant at) {
        Objects.requireNonNull(at, "instante da devolucao e obrigatorio");
        if (status != Status.SOLICITADA) {
            throw new IllegalStateException("devolucao nao esta SOLICITADA: " + status);
        }
        this.completedAt = at;
        this.status = at.isAfter(deadline) ? Status.DEVOLVIDA_COM_ATRASO : Status.DEVOLVIDA_NO_PRAZO;
    }

    /** Estado na leitura: pedido ainda aberto depois do prazo e vencido, nao cumprido. */
    public Status statusAt(Instant at) {
        if (status == Status.SOLICITADA && at != null && at.isAfter(deadline)) {
            return Status.VENCIDO_SEM_DEVOLUCAO;
        }
        return status;
    }

    public UUID id() { return id; }
    public UUID instructionId() { return instructionId; }
    public String chargeIdentifier() { return chargeIdentifier; }
    public String pspProtocol() { return pspProtocol; }
    public String reason() { return reason; }
    public Instant requestedAt() { return requestedAt; }
    public Instant deadline() { return deadline; }
    public Status status() { return status; }
    public Instant completedAt() { return completedAt; }
}
