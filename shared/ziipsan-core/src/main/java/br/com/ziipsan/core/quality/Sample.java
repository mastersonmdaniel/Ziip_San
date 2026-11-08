package br.com.ziipsan.core.quality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Amostra e sua cadeia de custodia (JOR-37): nasce planejada ou por
 * reclamacao, e coletada como OS de campo executada no app da ED-20
 * ({@code fieldOrderId}), com etiqueta ({@code labelCode}), custodia
 * registrada evento a evento do ponto ao laboratorio e recebimento
 * registrado PELO laboratorio. Sem recebimento do laboratorio, nenhum
 * resultado pode ser lancado sobre a amostra (ver
 * {@link AnalysisResult}).
 *
 * <p>Maquina de estados: PLANNED -&gt; COLLECTED -&gt; IN_TRANSIT -&gt;
 * RECEIVED_BY_LAB -&gt; ANALYZED -&gt; VALIDATED -&gt; PUBLISHED, com
 * CANCELLED a partir de PLANNED/COLLECTED (motivo obrigatorio).
 * Transicao fora da ordem e recusa nomeada.
 */
public final class Sample {

    public enum SampleType {
        /** Amostra do plano de amostragem. */
        ROUTINE,
        /** Amostra da reclamacao de qualidade no ponto de consumo (OPE-26). */
        COMPLAINT,
        /** Recoleta de uma amostra com resultado fora do padrao. */
        RECOLLECTION,
        /** Controle de processo (alimentado pelo diario da ED-87, sem redigitacao). */
        PROCESS_CONTROL
    }

    public enum Status {
        PLANNED, COLLECTED, IN_TRANSIT, RECEIVED_BY_LAB, ANALYZED, VALIDATED, PUBLISHED, CANCELLED
    }

    // Correcao m-2 (revisao da ED-50): RETURNED removido — era valor
    // morto (nenhum fluxo o emitia) e o aceite da ED-50 nao preve
    // devolucao de amostra; a cadeia e ponto -> laboratorio, sem retorno
    // neste nucleo. Sem peca morta no enum nem no CHECK do SQL.
    public enum CustodyKind { COLLECTED, SEALED, DISPATCHED, RECEIVED_BY_LAB }

    public record CustodyEvent(CustodyKind kind, String actorRef, Instant at, String note) {
        public CustodyEvent {
            if (kind == null) throw new IllegalArgumentException("tipo do evento de custodia e obrigatorio");
            if (actorRef == null || actorRef.isBlank())
                throw new IllegalArgumentException("responsavel do evento de custodia e obrigatorio");
            if (at == null) throw new IllegalArgumentException("instante do evento de custodia e obrigatorio");
        }
    }

    private final UUID id;
    private final UUID tenantId;
    private final UUID pointId;
    private final SampleType type;
    private final String labelCode;
    private final UUID fieldOrderId;
    private final UUID protocolId;
    private final UUID originalSampleId;
    private Status status = Status.PLANNED;
    private final List<CustodyEvent> custody = new ArrayList<>();
    private String labReceiverRef;
    private Instant labReceivedAt;
    private String cancelReason;

    private Sample(UUID id, UUID tenantId, UUID pointId, SampleType type, String labelCode,
                   UUID fieldOrderId, UUID protocolId, UUID originalSampleId) {
        if (id == null) throw new IllegalArgumentException("id da amostra e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId da amostra e obrigatorio");
        if (pointId == null) throw new IllegalArgumentException("pointId da amostra e obrigatorio");
        if (type == null) throw new IllegalArgumentException("tipo da amostra e obrigatorio");
        if (labelCode == null || labelCode.isBlank())
            throw new IllegalArgumentException("etiqueta (labelCode) e obrigatoria: amostra sem etiqueta"
                    + " nao entra na cadeia de custodia (JOR-37)");
        if (type == SampleType.COMPLAINT && protocolId == null)
            throw new IllegalArgumentException("amostra COMPLAINT exige o protocolo da reclamacao (OPE-26)");
        if (type == SampleType.RECOLLECTION && originalSampleId == null)
            throw new IllegalArgumentException("amostra RECOLLECTION exige a amostra original");
        if (type != SampleType.RECOLLECTION && originalSampleId != null)
            throw new IllegalArgumentException("originalSampleId so existe em RECOLLECTION");
        this.id = id;
        this.tenantId = tenantId;
        this.pointId = pointId;
        this.type = type;
        this.labelCode = labelCode;
        this.fieldOrderId = fieldOrderId;
        this.protocolId = protocolId;
        this.originalSampleId = originalSampleId;
    }

    public static Sample planned(UUID id, UUID tenantId, UUID pointId, SampleType type,
                                 String labelCode, UUID fieldOrderId, UUID protocolId) {
        return new Sample(id, tenantId, pointId, type, labelCode, fieldOrderId, protocolId, null);
    }

    public static Sample recollectionOf(Sample original, UUID newId, String newLabelCode, UUID fieldOrderId) {
        if (original == null) throw new IllegalArgumentException("amostra original e obrigatoria");
        return new Sample(newId, original.tenantId, original.pointId, SampleType.RECOLLECTION,
                newLabelCode, fieldOrderId, original.protocolId, original.id);
    }

    private void require(Status expected, String action) {
        if (status != expected) throw new IllegalStateException(
                "amostra " + labelCode + ": " + action + " exige estado " + expected
                        + ", estado atual " + status);
    }

    private void append(CustodyEvent event) {
        if (!custody.isEmpty() && event.at().isBefore(custody.get(custody.size() - 1).at()))
            throw new IllegalArgumentException("evento de custodia fora de ordem cronologica na amostra "
                    + labelCode);
        custody.add(event);
    }

    public void collect(String collectorRef, Instant at) {
        require(Status.PLANNED, "coleta");
        append(new CustodyEvent(CustodyKind.COLLECTED, collectorRef, at, null));
        status = Status.COLLECTED;
    }

    public void sealAndDispatch(String actorRef, Instant at) {
        require(Status.COLLECTED, "lacragem/expedicao");
        append(new CustodyEvent(CustodyKind.SEALED, actorRef, at, null));
        append(new CustodyEvent(CustodyKind.DISPATCHED, actorRef, at, null));
        status = Status.IN_TRANSIT;
    }

    /** Recebimento registrado pelo laboratorio (JOR-37) — portao para qualquer resultado. */
    public void receiveByLab(String receiverRef, Instant at) {
        require(Status.IN_TRANSIT, "recebimento pelo laboratorio");
        append(new CustodyEvent(CustodyKind.RECEIVED_BY_LAB, receiverRef, at, null));
        this.labReceiverRef = receiverRef;
        this.labReceivedAt = at;
        status = Status.RECEIVED_BY_LAB;
    }

    public void markAnalyzed() {
        require(Status.RECEIVED_BY_LAB, "conclusao da analise");
        status = Status.ANALYZED;
    }

    public void markValidated() {
        require(Status.ANALYZED, "validacao");
        status = Status.VALIDATED;
    }

    public void markPublished() {
        require(Status.VALIDATED, "publicacao");
        status = Status.PUBLISHED;
    }

    public void cancel(String reason) {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("cancelamento da amostra exige motivo");
        if (status != Status.PLANNED && status != Status.COLLECTED)
            throw new IllegalStateException("amostra " + labelCode + " em " + status
                    + " nao pode ser cancelada");
        this.cancelReason = reason;
        status = Status.CANCELLED;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID pointId() { return pointId; }
    public SampleType type() { return type; }
    public String labelCode() { return labelCode; }
    public UUID fieldOrderId() { return fieldOrderId; }
    public UUID protocolId() { return protocolId; }
    public UUID originalSampleId() { return originalSampleId; }
    public Status status() { return status; }
    public List<CustodyEvent> custody() { return List.copyOf(custody); }
    public String labReceiverRef() { return labReceiverRef; }
    public Instant labReceivedAt() { return labReceivedAt; }
    public String cancelReason() { return cancelReason; }
    public boolean receivedByLab() { return labReceivedAt != null; }
}
