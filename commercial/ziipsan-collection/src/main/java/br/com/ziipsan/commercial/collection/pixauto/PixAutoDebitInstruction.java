package br.com.ziipsan.commercial.collection.pixauto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Instrucao de cobranca recorrente de UM ciclo, vinculada a autorizacao do
 * pagador e a conta do ciclo (referencia de arrecadacao da P3.1/P3.2:
 * {@code collectionMonth} e o documento da conta). O
 * {@code chargeIdentifier} e o identificador de cobranca unico por instrucao:
 * e por ele que a conciliacao casa o retorno do PSP com a instrucao local.
 *
 * <p>Valores: {@code principal} do ciclo + {@code fine}/{@code interest} que
 * vierem do ciclo anterior falho (ver {@link NextCycleChargeCalculator});
 * {@code total} e sempre principal + multa + juros.
 */
public final class PixAutoDebitInstruction {

    public enum Status {
        AGENDADA, NOTIFICADA, ENVIADA, LIQUIDADA, FALHA_AGUARDANDO_RETENTATIVA,
        FALHA_DEFINITIVA, CANCELADA_PELO_PAGADOR, CANCELADA_PELO_RECEBEDOR
    }

    private final UUID id;
    private final UUID authorizationId;
    private final String chargeIdentifier;
    private final String documentType;
    private final UUID documentId;
    private final LocalDate collectionMonth;
    private final BigDecimal principal;
    private final BigDecimal fine;
    private final BigDecimal interest;
    private final LocalDate settlementDate;
    private Status status = Status.AGENDADA;
    private int attempts;
    private LocalDate firstAttemptDate;
    private Instant notifiedAt;
    private String endToEndId;
    private Instant settledAt;

    public PixAutoDebitInstruction(UUID id, UUID authorizationId, String chargeIdentifier,
                                   String documentType, UUID documentId, LocalDate collectionMonth,
                                   BigDecimal principal, BigDecimal fine, BigDecimal interest,
                                   LocalDate settlementDate) {
        this.id = Objects.requireNonNull(id, "id da instrucao e obrigatorio");
        this.authorizationId = Objects.requireNonNull(authorizationId, "autorizacao da instrucao e obrigatoria");
        if (chargeIdentifier == null || chargeIdentifier.isBlank()) {
            throw new IllegalArgumentException("identificador de cobranca e obrigatorio (conciliacao)");
        }
        this.chargeIdentifier = chargeIdentifier;
        this.documentType = documentType;
        this.documentId = documentId;
        this.collectionMonth = Objects.requireNonNull(collectionMonth, "mes de arrecadacao e obrigatorio");
        if (!collectionMonth.equals(collectionMonth.withDayOfMonth(1))) {
            throw new IllegalArgumentException("collectionMonth deve ser o dia 1 do mes (DAD-19)");
        }
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("principal da instrucao deve ser > 0");
        }
        this.principal = principal;
        this.fine = fine == null ? BigDecimal.ZERO : fine;
        this.interest = interest == null ? BigDecimal.ZERO : interest;
        if (this.fine.signum() < 0 || this.interest.signum() < 0) {
            throw new IllegalArgumentException("multa e juros nao podem ser negativos");
        }
        this.settlementDate = Objects.requireNonNull(settlementDate, "data de liquidacao e obrigatoria");
    }

    public BigDecimal total() {
        return principal.add(fine).add(interest);
    }

    public void markNotified(Instant at) {
        if (status != Status.AGENDADA && status != Status.NOTIFICADA) {
            throw new IllegalStateException("instrucao " + status + " nao pode ser notificada");
        }
        this.notifiedAt = Objects.requireNonNull(at, "instante da notificacao e obrigatorio");
        this.status = Status.NOTIFICADA;
    }

    public void markSubmitted(String endToEndId, LocalDate attemptDate) {
        this.endToEndId = Objects.requireNonNull(endToEndId, "endToEndId do debito e obrigatorio");
        if (attempts == 0) {
            this.firstAttemptDate = Objects.requireNonNull(attemptDate, "data da primeira tentativa e obrigatoria");
        }
        this.attempts++;
        this.status = Status.ENVIADA;
    }

    public void markFailed(boolean willRetry) {
        if (status != Status.ENVIADA) {
            throw new IllegalStateException("so instrucao ENVIADA pode falhar: " + status);
        }
        this.status = willRetry ? Status.FALHA_AGUARDANDO_RETENTATIVA : Status.FALHA_DEFINITIVA;
    }

    public void markSettled(String endToEndId, Instant at) {
        if (status != Status.ENVIADA) {
            throw new IllegalStateException("so instrucao ENVIADA pode liquidar: " + status);
        }
        this.endToEndId = Objects.requireNonNull(endToEndId, "endToEndId da liquidacao e obrigatorio");
        this.settledAt = Objects.requireNonNull(at, "instante da liquidacao e obrigatorio");
        this.status = Status.LIQUIDADA;
    }

    public void markCancelled(PixAutoDebitCancellation.Actor actor) {
        this.status = actor == PixAutoDebitCancellation.Actor.PAGADOR
                ? Status.CANCELADA_PELO_PAGADOR : Status.CANCELADA_PELO_RECEBEDOR;
    }

    /** Retentativas ja usadas: tentativas alem da primeira. */
    public int retriesUsed() {
        return Math.max(0, attempts - 1);
    }

    public UUID id() { return id; }
    public UUID authorizationId() { return authorizationId; }
    public String chargeIdentifier() { return chargeIdentifier; }
    public String documentType() { return documentType; }
    public UUID documentId() { return documentId; }
    public LocalDate collectionMonth() { return collectionMonth; }
    public BigDecimal principal() { return principal; }
    public BigDecimal fine() { return fine; }
    public BigDecimal interest() { return interest; }
    public LocalDate settlementDate() { return settlementDate; }
    public Status status() { return status; }
    public int attempts() { return attempts; }
    public LocalDate firstAttemptDate() { return firstAttemptDate; }
    public Instant notifiedAt() { return notifiedAt; }
    public String endToEndId() { return endToEndId; }
    public Instant settledAt() { return settledAt; }
}
