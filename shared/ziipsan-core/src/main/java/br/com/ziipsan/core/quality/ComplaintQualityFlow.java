package br.com.ziipsan.core.quality;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fluxo da reclamacao de qualidade (OPE-26, JOR-37): a reclamacao de
 * cor, gosto, odor ou pressao registrada como protocolo no atendimento
 * da ED-35 gera OS de coleta no ponto de consumo (amostra
 * {@code COMPLAINT}, pela porta da ED-20); o resultado vai ao laudo;
 * fora do padrao, RECOLHE (nova OS, amostra {@code RECOLLECTION}
 * ligada a original); a resposta ao usuario sai NO MESMO protocolo,
 * dentro do prazo {@code sac.quality_complaint_deadline_days}
 * (Decreto 5.440/2005 — valor da norma no aceite, so reduz, validado
 * por {@link QualityParameters}).
 *
 * <p>Este nucleo nao abre protocolo (ED-35), nao executa OS (ED-20) e
 * nao envia mensagem (ED-41): orquestra pelas portas e guarda o estado
 * do fluxo.
 */
public final class ComplaintQualityFlow {

    public enum ComplaintKind { COLOR, TASTE, ODOR, PRESSURE, OTHER }

    public enum Stage {
        OPENED, COLLECTION_ORDERED, RESULT_RECORDED, RECOLLECTION_ORDERED,
        RESPONDED, OVERDUE
    }

    private final UUID tenantId;
    private final UUID protocolId;
    private final UUID consumerPointId;
    private final ComplaintKind kind;
    private final LocalDate openedOn;
    private final int deadlineDays;
    private Stage stage = Stage.OPENED;
    private UUID collectionOrderId;
    private UUID complaintSampleId;
    private UUID recollectionOrderId;
    private ConformityEvaluator.Verdict lastVerdict;
    private String responseReference;

    private ComplaintQualityFlow(UUID tenantId, UUID protocolId, UUID consumerPointId,
                                 ComplaintKind kind, LocalDate openedOn, int deadlineDays) {
        this.tenantId = tenantId;
        this.protocolId = protocolId;
        this.consumerPointId = consumerPointId;
        this.kind = kind;
        this.openedOn = openedOn;
        this.deadlineDays = deadlineDays;
    }

    public static ComplaintQualityFlow open(UUID tenantId, UUID protocolId, UUID consumerPointId,
                                            ComplaintKind kind, LocalDate openedOn, Integer deadlineDaysRaw) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (protocolId == null) throw new IllegalArgumentException("protocolId da reclamacao e obrigatorio");
        if (consumerPointId == null)
            throw new IllegalArgumentException("ponto de consumo da coleta e obrigatorio (OPE-26)");
        if (kind == null) throw new IllegalArgumentException("tipo da reclamacao e obrigatorio");
        if (openedOn == null) throw new IllegalArgumentException("data da reclamacao e obrigatoria");
        int days = QualityParameters.complaintDeadlineDays(deadlineDaysRaw);
        return new ComplaintQualityFlow(tenantId, protocolId, consumerPointId, kind, openedOn, days);
    }

    /** Data-limite da resposta (abertura + prazo validado, so-reduz contra os 30 da norma). */
    public LocalDate deadline() {
        return openedOn.plusDays(deadlineDays);
    }

    /** Gera a OS de coleta no ponto de consumo, pela porta da ED-20. Devolve o id da OS. */
    public UUID orderCollection(QualityPorts.FieldCollectionOrderPort port) {
        if (port == null) throw new IllegalArgumentException("porta de OS de coleta e obrigatoria");
        if (stage != Stage.OPENED)
            throw new IllegalStateException("coleta da reclamacao ja ordenada (estagio " + stage + ")");
        this.collectionOrderId = port.openCollectionOrder(tenantId, consumerPointId,
                Sample.SampleType.COMPLAINT, "RECLAMACAO_QUALIDADE:" + kind);
        if (collectionOrderId == null)
            throw new IllegalStateException("porta de OS nao devolveu o id da OS de coleta");
        stage = Stage.COLLECTION_ORDERED;
        return collectionOrderId;
    }

    /** Liga a amostra COMPLAINT coletada pela OS e registra o veredito do laudo. */
    public void recordResult(Sample complaintSample, ConformityEvaluator.Assessment assessment) {
        if (stage != Stage.COLLECTION_ORDERED)
            throw new IllegalStateException("resultado so entra apos a OS de coleta (estagio " + stage + ")");
        if (complaintSample == null) throw new IllegalArgumentException("amostra da reclamacao e obrigatoria");
        if (complaintSample.type() != Sample.SampleType.COMPLAINT)
            throw new IllegalArgumentException("amostra da reclamacao deve ser do tipo COMPLAINT (OPE-26)");
        if (!protocolId.equals(complaintSample.protocolId()))
            throw new IllegalArgumentException("amostra nao pertence ao protocolo desta reclamacao");
        if (assessment == null) throw new IllegalArgumentException("avaliacao do laudo e obrigatoria");
        this.complaintSampleId = complaintSample.id();
        this.lastVerdict = assessment.verdict();
        stage = Stage.RESULT_RECORDED;
    }

    /**
     * Fora do padrao: ordena a recoleta (nova OS; amostra RECOLLECTION
     * sera ligada a original na coleta). Conforme ou indeterminado nao
     * recolhe por esta regra — indeterminado segue para a resposta com
     * o fato declarado, nunca como conforme.
     */
    public UUID orderRecollectionIfNonConforming(QualityPorts.FieldCollectionOrderPort port) {
        if (stage != Stage.RESULT_RECORDED)
            throw new IllegalStateException("recoleta so apos o resultado (estagio " + stage + ")");
        if (lastVerdict != ConformityEvaluator.Verdict.NON_CONFORMING) {
            return null;
        }
        if (port == null) throw new IllegalArgumentException("porta de OS de coleta e obrigatoria");
        this.recollectionOrderId = port.openCollectionOrder(tenantId, consumerPointId,
                Sample.SampleType.RECOLLECTION, "RECOLETA_RECLAMACAO_QUALIDADE:" + kind);
        if (recollectionOrderId == null)
            throw new IllegalStateException("porta de OS nao devolveu o id da OS de recoleta");
        stage = Stage.RECOLLECTION_ORDERED;
        return recollectionOrderId;
    }

    /** Resposta ao usuario NO MESMO protocolo, pela porta da ED-35. */
    public void respond(QualityPorts.ProtocolResponsePort port, String reference, LocalDate respondedOn) {
        if (stage != Stage.RESULT_RECORDED && stage != Stage.RECOLLECTION_ORDERED)
            throw new IllegalStateException("resposta so apos o resultado do laudo (estagio " + stage + ")");
        if (lastVerdict == null)
            throw new IllegalStateException("resposta exige o resultado registrado no laudo");
        if (port == null) throw new IllegalArgumentException("porta de resposta no protocolo e obrigatoria");
        if (reference == null || reference.isBlank())
            throw new IllegalArgumentException("referencia da resposta e obrigatoria");
        if (respondedOn == null) throw new IllegalArgumentException("data da resposta e obrigatoria");
        port.respondOnProtocol(tenantId, protocolId, reference);
        this.responseReference = reference;
        stage = respondedOn.isAfter(deadline()) ? Stage.OVERDUE : Stage.RESPONDED;
    }

    public boolean overdueOn(LocalDate date) {
        return responseReference == null && date.isAfter(deadline());
    }

    public UUID tenantId() { return tenantId; }
    public UUID protocolId() { return protocolId; }
    public UUID consumerPointId() { return consumerPointId; }
    public ComplaintKind kind() { return kind; }
    public Stage stage() { return stage; }
    public UUID collectionOrderId() { return collectionOrderId; }
    public UUID complaintSampleId() { return complaintSampleId; }
    public UUID recollectionOrderId() { return recollectionOrderId; }
    public ConformityEvaluator.Verdict lastVerdict() { return lastVerdict; }
    public String responseReference() { return responseReference; }
    public int deadlineDays() { return deadlineDays; }
}
