package br.com.ziipsan.operations.metering.field;

import java.util.UUID;

/**
 * Recibo de uma tarefa sincronizada. {@code duplicate} marca o recibo devolvido num
 * reenvio: o conteudo e o do primeiro aceite, sem nova leitura criada.
 */
public record FieldSyncReceipt(UUID taskId, UUID readingId, boolean reviewQueued, boolean duplicate) {

    public FieldSyncReceipt asDuplicate() {
        return new FieldSyncReceipt(taskId, readingId, reviewQueued, true);
    }
}
