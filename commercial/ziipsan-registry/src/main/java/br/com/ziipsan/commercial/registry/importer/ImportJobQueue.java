package br.com.ziipsan.commercial.registry.importer;

import java.util.UUID;

/**
 * Porta de publicação dos jobs do importador (P1.40), na fronteira do módulo
 * registry.
 *
 * <p>P1-C (integracao): o ImportService publicava direto na
 * {@code br.com.ziipsan.platform.infra.queue.MessageQueue}, o que viola a
 * regra P1.13 (commercial não depende de platform — ModuleBoundaryTest).
 * O serviço passa a depender só desta porta; o adaptador sobre a fila vive
 * no módulo app (QueueConfig), que é a raiz de composição e já detém o bean
 * MessageQueue. Sem {@code @Primary} e sem padrão silencioso: a única
 * implementação é o adaptador do app.
 */
public interface ImportJobQueue {

    /** Publica um job do importador (validate/apply/reverse) na fila. */
    void publish(UUID jobId, UUID tenantId, String type, String payloadJson);
}
