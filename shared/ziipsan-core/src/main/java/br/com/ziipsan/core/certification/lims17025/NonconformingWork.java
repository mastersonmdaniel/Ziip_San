package br.com.ziipsan.core.certification.lims17025;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Trabalho nao conforme (aceite da ED-51): quando um trabalho do
 * laboratorio e identificado como nao conforme, abre-se o registro
 * com a descricao do desvio, o ensaio e/ou laudo afetado e a
 * DISPOSICAO decidida; o fechamento exige a acao efetivamente tomada
 * e quem fechou — registro aberto nao some, e fechamento sem acao
 * nao existe.
 *
 * <p>As disposicoes sao palavras do produto (nenhuma fonte externa
 * as enumera; sao o vocabulario operacional minimo do fluxo):
 * repetir o ensaio, descartar o resultado, emendar o laudo,
 * notificar o cliente ou outra disposicao com justificativa
 * ({@code OTHER} exige a justificativa no proprio registro).
 *
 * <p>Este registro e o do LABORATORIO (dominio da ED-51). Ele nao
 * duplica a ocorrencia unica da P2.25: quando a fiacao classificar o
 * desvio tambem como ocorrencia, o vinculo e o {@code occurrenceId}
 * opcional referenciado por id (padrao da ED-69 com a saida nao
 * conforme) — a existencia/kind da ocorrencia e conferida na fiacao
 * (pendencia P-ED51-02).
 *
 * <p>So com a norma ligada: a abertura passa pela porta da
 * {@link Lims17025Activation}.
 */
public final class NonconformingWork {

    /** Disposicao decidida para o trabalho nao conforme (vocabulario do produto, declarado). */
    public enum Disposition {
        REPEAT_ASSAY, DISCARD_RESULT, AMEND_REPORT, NOTIFY_CLIENT, OTHER
    }

    public enum Status { OPEN, CLOSED }

    private final UUID id;
    private final UUID tenantId;
    private final UUID assayId;
    private final UUID reportId;
    private final UUID occurrenceId;
    private final String description;
    private final Disposition disposition;
    private final String dispositionJustification;
    private final String openedByRef;
    private final Instant openedAt;
    private Status status = Status.OPEN;
    private String actionTaken;
    private String closedByRef;
    private Instant closedAt;

    private NonconformingWork(UUID id, UUID tenantId, UUID assayId, UUID reportId, UUID occurrenceId,
                              String description, Disposition disposition,
                              String dispositionJustification, String openedByRef, Instant openedAt) {
        if (id == null) throw new IllegalArgumentException("id do trabalho nao conforme e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (assayId == null && reportId == null)
            throw new IllegalArgumentException("trabalho nao conforme exige o ensaio e/ou o laudo afetado");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description do desvio e obrigatoria");
        Objects.requireNonNull(disposition, "disposition (disposicao decidida) e obrigatoria");
        if (disposition == Disposition.OTHER
                && (dispositionJustification == null || dispositionJustification.isBlank())) {
            throw new IllegalArgumentException("disposicao OTHER exige a justificativa da disposicao");
        }
        if (openedByRef == null || openedByRef.isBlank())
            throw new IllegalArgumentException("openedByRef e obrigatorio (quem abriu)");
        Objects.requireNonNull(openedAt, "openedAt e obrigatorio");
        this.id = id;
        this.tenantId = tenantId;
        this.assayId = assayId;
        this.reportId = reportId;
        this.occurrenceId = occurrenceId;
        this.description = description;
        this.disposition = disposition;
        this.dispositionJustification = dispositionJustification;
        this.openedByRef = openedByRef;
        this.openedAt = openedAt;
    }

    /** Abertura do registro: so com a norma ligada (porta da ativacao). */
    public static NonconformingWork open(Lims17025Activation activation, UUID id, UUID tenantId,
                                         UUID assayId, UUID reportId, UUID occurrenceId,
                                         String description, Disposition disposition,
                                         String dispositionJustification, String openedByRef,
                                         Instant openedAt) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        return new NonconformingWork(id, tenantId, assayId, reportId, occurrenceId, description,
                disposition, dispositionJustification, openedByRef, openedAt);
    }

    /** Fechamento: exige a acao efetivamente tomada e quem fechou. */
    public void close(String actionTaken, String closedByRef, Instant closedAt) {
        if (status != Status.OPEN)
            throw new IllegalStateException("trabalho nao conforme " + id + " ja fechado");
        if (actionTaken == null || actionTaken.isBlank())
            throw new IllegalArgumentException("fechamento exige a acao efetivamente tomada");
        if (closedByRef == null || closedByRef.isBlank())
            throw new IllegalArgumentException("closedByRef e obrigatorio (quem fechou)");
        Objects.requireNonNull(closedAt, "closedAt e obrigatorio");
        if (closedAt.isBefore(openedAt))
            throw new IllegalArgumentException("fechamento anterior a abertura");
        this.actionTaken = actionTaken;
        this.closedByRef = closedByRef;
        this.closedAt = closedAt;
        this.status = Status.CLOSED;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID assayId() { return assayId; }
    public UUID reportId() { return reportId; }
    public UUID occurrenceId() { return occurrenceId; }
    public String description() { return description; }
    public Disposition disposition() { return disposition; }
    public String dispositionJustification() { return dispositionJustification; }
    public String openedByRef() { return openedByRef; }
    public Instant openedAt() { return openedAt; }
    public Status status() { return status; }
    public String actionTaken() { return actionTaken; }
    public String closedByRef() { return closedByRef; }
    public Instant closedAt() { return closedAt; }
}
