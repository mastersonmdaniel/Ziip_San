package br.com.ziipsan.operations.metering.field;

import java.util.Optional;
import java.util.UUID;

/**
 * Registro das tarefas de campo ja sincronizadas, por (tenant, task_id): a trava de
 * idempotencia da ED-18. A implementacao de producao persiste sobre a unicidade de
 * {@code meter_readings.idempotency_key} (V5_4) e devolve o recibo original no reenvio;
 * esta porta existe para a regra ser testavel sem banco e para o recibo nao depender
 * de reler a leitura.
 */
public interface SyncedTaskStore {

    Optional<FieldSyncReceipt> find(UUID tenantId, UUID taskId);

    /** Grava o recibo da tarefa; chamado uma unica vez por (tenant, task_id). */
    void save(UUID tenantId, FieldSyncReceipt receipt);
}
