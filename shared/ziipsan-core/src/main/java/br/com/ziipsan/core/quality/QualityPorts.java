package br.com.ziipsan.core.quality;

import java.util.UUID;

/**
 * Portas da ED-50 para os vizinhos — a ED-50 CONSUME por estas
 * interfaces, sem duplicar o que cada entrega ja faz:
 * <ul>
 *   <li>{@link FieldCollectionOrderPort} — OS de coleta executada no
 *       app de campo da ED-20 sobre o motor de OS da P1.31: a ED-50
 *       pede a OS e recebe o id; nao cria motor de OS nem tela de
 *       campo;</li>
 *   <li>{@link ProtocolResponsePort} — resposta ao usuario NO MESMO
 *       protocolo do atendimento da ED-35 (OPE-26): a ED-50 entrega o
 *       texto/registro da resposta; o protocolo e o canal sao da
 *       ED-35/ED-41;</li>
 *   <li>{@link LegalTemplateDispatchPort} — comunicacao por MODELO do
 *       motor de modelos da P2.30 (JOR-06): a ED-50 entrega a chave do
 *       modelo e os destinatarios por tipo; nenhum texto de
 *       comunicacao e redigido nem enviado por canal proprio aqui —
 *       o envio e o registro de prova sao do motor/canal.</li>
 * </ul>
 */
public final class QualityPorts {

    private QualityPorts() {}

    /** OS de coleta de campo (ED-20/P1.31): devolve o id da OS criada. */
    public interface FieldCollectionOrderPort {
        UUID openCollectionOrder(UUID tenantId, UUID pointId, Sample.SampleType sampleType, String reason);
    }

    /** Resposta no protocolo do atendimento (ED-35), no mesmo protocolo da reclamacao. */
    public interface ProtocolResponsePort {
        void respondOnProtocol(UUID tenantId, UUID protocolId, String responseReference);
    }

    public enum RiskRecipient { HEALTH_AUTHORITY, POPULATION }

    /**
     * Disparo por modelo do motor da P2.30 (JOR-06). Devolve a
     * referencia do envio registrada pelo motor (prova de envio).
     */
    public interface LegalTemplateDispatchPort {
        String dispatchByTemplate(UUID tenantId, String templateKey, RiskRecipient recipient, UUID closingOrSampleRef);
    }
}
