package br.com.ziipsan.platform.work;

import java.util.Optional;

/**
 * Porta para o catálogo único de eventos de negócio da P1.38 (declarado por pacote em
 * {@code vertical.json}, ADR-0037). A caixa NÃO mantém cópia do catálogo: pergunta a esta
 * porta a cada ingestão. Evento ausente, ou em versão não declarada, é recusado pela
 * {@link WorkItemIngestRule} — a mesma regra que a P1.38 impõe à publicação pela outbox.
 * Mesma forma da porta {@code BusinessEventCatalog} da P3.36.
 */
public interface WorkEventCatalog {
    Optional<WorkCatalogEvent> find(String eventName, int eventVersion);
}
