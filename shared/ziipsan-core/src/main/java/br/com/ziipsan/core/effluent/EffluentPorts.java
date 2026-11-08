package br.com.ziipsan.core.effluent;

import java.util.UUID;

/**
 * Portas da ED-105 para os vizinhos — a ED-105 CONSUME por estas
 * interfaces, sem duplicar o que cada entrega ja faz:
 * <ul>
 *   <li>{@link EffluentLabPort} — o laboratorio e a cadeia de
 *       custodia da ED-50: a amostra de efluente e uma amostra da
 *       ED-50 (etiqueta, custodia ponto→laboratorio, recebimento
 *       registrado pelo laboratorio); esta porta devolve a prova de
 *       recebimento sobre a qual nenhum resultado existe sem ela
 *       (JOR-37 aplicado ao automonitoramento);</li>
 *   <li>{@link ManualAgendaPort} — a agenda da ED-98: enquanto a
 *       ED-104 (MTR automatico) nao for liberada, a emissao do MTR
 *       como gerador e um ITEM MANUAL da agenda (RAS-17); esta
 *       porta cria o item e devolve a referencia dele.</li>
 * </ul>
 * O diario de operacao da ED-87 nao entra por porta: ele e LIDO
 * sobre as classes reais da ED-87 (ver {@link DiaryEffluentFacts}),
 * como a ED-87 leu a ED-50.
 */
public final class EffluentPorts {

    private EffluentPorts() {}

    /** Prova de custodia de uma amostra da ED-50, como o laboratorio a registra. */
    public record LabReceipt(UUID sampleId, boolean receivedByLab, String labReceiverRef) {
        public LabReceipt {
            if (sampleId == null) throw new IllegalArgumentException("sampleId e obrigatorio");
            if (receivedByLab && (labReceiverRef == null || labReceiverRef.isBlank()))
                throw new IllegalArgumentException("recebimento pelo laboratorio exige quem recebeu (ED-50)");
        }
    }

    /** Laboratorio/custodia da ED-50: prova de recebimento da amostra pelo laboratorio. */
    public interface EffluentLabPort {
        LabReceipt receiptOf(UUID tenantId, UUID sampleId);
    }

    /** Agenda da ED-98: cria o item manual e devolve a referencia do item. */
    public interface ManualAgendaPort {
        String createManualMtrItem(UUID tenantId, UUID sludgeRecordId, String description);
    }
}
