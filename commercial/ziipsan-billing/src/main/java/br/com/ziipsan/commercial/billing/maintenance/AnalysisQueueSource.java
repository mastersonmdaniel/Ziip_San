package br.com.ziipsan.commercial.billing.maintenance;

import java.util.List;
import java.util.UUID;

/**
 * Fonte dos itens da fila de analise (JOR-09): as leituras e contas retidas
 * do tenant, com os fatos que a fila mostra -- consumo atual, media, serie
 * dos ultimos meses, foto e ocorrencia da leitura.
 *
 * <p>Os fatos vivem no modulo metering ({@code meter_readings},
 * {@code consumption_records} e o catalogo {@code reading_anomalies}) e no
 * billing (a conta retida): o billing nao depende do metering, entao a
 * leitura chega por esta porta (precedente {@code CycleBatchGateway},
 * P2.5) e o {@link BillMaintenanceService} monta os
 * {@link AnalysisQueueRule.QueueItem} a partir das linhas -- a montagem e
 * desta camada; o adaptador de integracao, no raiz de composicao, so le as
 * tabelas dos modulos donos, sem regra.
 *
 * <p>Foto: {@code meter_readings} nao tem coluna de foto nesta base
 * (V5_1) -- {@code photoRef} e a referencia opaca do registro de captura
 * (P2.7/mobile) quando ela existir; {@code null} = leitura sem foto, e a
 * fila mostra a ausencia.
 *
 * <p>{@code rereadingOrderTypeCode}: o tipo de OS que a ocorrencia da
 * leitura gera, do catalogo de anormalidades
 * ({@code reading_anomalies.service_order_type_code}, V5_7 da P2.5);
 * {@code null} quando a ocorrencia nao gera OS -- pedir releitura de um
 * item assim e recusado pela {@link AnalysisQueueRule}.
 */
public interface AnalysisQueueSource {

    /** Linha crua da fila: um fato retido (leitura e/ou conta) do tenant. */
    record HeldReading(UUID readingId, UUID billId, UUID unitId,
                       Integer currentConsumption, Integer averageConsumption,
                       List<Integer> lastMonthsConsumption, String photoRef,
                       String occurrenceCode, String holdReason,
                       String rereadingOrderTypeCode) {
    }

    /** Todas as leituras/contas retidas do tenant, na ordem da fonte. */
    List<HeldReading> heldReadings(UUID tenantId);
}
