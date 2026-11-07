package br.com.ziipsan.commercial.billing.maintenance;

import java.util.UUID;

/**
 * Efeitos da fila de analise (JOR-09) que vivem fora do billing: liberar a
 * retencao de uma leitura/conta e destravar o fato para o faturamento.
 *
 * <p>A retencao em si e do metering: a conta/leitura retida por anomalia
 * ({@code bill.hold_on_anomaly = HOLD_AND_REVIEW}, P2.5) fica fora do
 * faturamento pelo registro de consumo ({@code consumption_records}:
 * linha nao faturavel/pausada) ate a analise liberar. O billing nao
 * escreve tabela do metering -- quem destrava e o adaptador desta porta,
 * no modulo dono do consumo (marca a linha faturavel e resolve a retencao
 * da leitura), chamado pelo {@link BillMaintenanceService} na acao
 * RELEASE <b>antes</b> de gravar a decisao: se o destrave falhar, nenhuma
 * decisao e registrada. Declarado assim, com o dono nomeado, em vez de a
 * decisao fingir um efeito que esta camada nao tem.
 */
public interface AnalysisQueueEffects {

    /**
     * Libera a leitura/conta retida para o faturamento.
     *
     * @param readingId leitura retida do item (pode ser null se o item e so conta)
     * @param billId    conta retida do item (pode ser null se o item e so leitura)
     */
    void releaseForBilling(UUID tenantId, UUID readingId, UUID billId, UUID actorId);
}
