package br.com.ziipsan.commercial.registry.quality;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Porta da OS interna de lote da correcao cadastral (P3.32, ADR-0039): a
 * fila gera UMA OS de lote por grupo de itens, com um alvo
 * {@code CONSUMER_UNIT} por unidade e o efeito de confirmacao da correcao
 * declarado uma vez por alvo — o mesmo desenho da OS de lote da carga de
 * legado da P1.2 ({@code LegacyBatchOrderGateway}).
 *
 * <p>Diferenca deliberada para a carga de legado: la o efeito ja tinha sido
 * aplicado quando a OS abria, e ela executava e encerrava na hora. Aqui a
 * correcao e trabalho posterior (a campanha de campo da ED-16 aprova campo
 * a campo e aplica como efeito da OS): a OS abre e e submetida, SEM
 * executar; a fila so resolve os itens quando a OS e executada/encerrada e
 * o efeito confirma cada alvo.
 */
public interface BatchOrderGateway {

    /** OS de lote aberta (identificador e situacao devolvidos pelo motor de OS). */
    record OpenedCorrectionOrder(UUID orderId, String status) {
    }

    /**
     * Abre a OS de lote da correcao: um alvo por unidade, com as chaves dos
     * achados daquele alvo na carga do efeito.
     *
     * @param findingKeysByUnit unidade alvo -> chaves dos achados dela
     * @param actorId           quem abre a OS
     */
    OpenedCorrectionOrder openCorrectionBatch(Map<UUID, List<String>> findingKeysByUnit, UUID actorId);

    /**
     * Recebe a confirmacao do efeito de correcao de um alvo quando a OS de
     * lote executa: e o unico caminho que resolve itens da fila.
     */
    interface CorrectionConfirmationHandler {
        void onCorrectionConfirmed(UUID orderId, UUID unitId, List<String> findingKeys);
    }
}
