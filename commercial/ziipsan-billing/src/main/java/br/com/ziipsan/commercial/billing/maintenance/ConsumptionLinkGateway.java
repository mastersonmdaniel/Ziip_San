package br.com.ziipsan.commercial.billing.maintenance;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Porta para o versionamento de consumo da P2.5 (DAD-14): a retificacao de
 * leitura/consumo pelo backoffice faz a nova versao do consumo nascer
 * ligada a nova versao da conta, pelo mecanismo que a P2.5 ja entregou --
 * {@code ConsumptionRectification.rectify(current, newBillId,
 * billReferenceMonth, parameterVersionIds)} do modulo metering, que insere
 * a versao seguinte do {@code ConsumptionRecord} e liga a anterior por
 * {@code superseded_by_id} (a unica escrita na linha antiga).
 *
 * <p>O billing nao depende do modulo metering (precedente das portas do
 * projeto, ex.: {@code CycleBatchGateway} da P2.5): o adaptador de
 * integracao, no modulo dono do consumo, carrega a versao vigente, aplica
 * a regra da P2.5 com o id e o mes da conta nova e salva as duas linhas.
 * Quem orquestra a chamada e o {@link BillMaintenanceService}, dentro do
 * efeito da retificacao -- a ligacao nao fica para "quem chamar depois".
 */
public interface ConsumptionLinkGateway {

    /**
     * Cria a nova versao do consumo apontando para a conta nova.
     *
     * @param currentConsumptionRecordId id da versao vigente do consumo
     *                                   (a que a conta anterior faturou)
     * @param newBillId                  id da nova versao da conta
     * @param newBillReferenceMonth     mes de referencia da conta nova (dia 1)
     * @return id da nova versao do consumo criada
     */
    UUID rectifyToBill(UUID tenantId, UUID currentConsumptionRecordId, UUID newBillId,
                       LocalDate newBillReferenceMonth, UUID actorId);
}
