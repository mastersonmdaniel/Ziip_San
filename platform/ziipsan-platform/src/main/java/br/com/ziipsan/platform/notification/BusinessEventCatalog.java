package br.com.ziipsan.platform.notification;

import java.util.Optional;

/**
 * Porta para o catálogo único de eventos de negócio da P1.38 (declarado por pacote em
 * {@code vertical.json}, ADR-0037). A central NÃO mantém cópia do catálogo: pergunta a esta
 * porta a cada ingestão. Evento ausente, ou em versão não declarada, é recusado pela
 * {@link NotificationIngestRule} — é a mesma regra que a P1.38 impõe à publicação pela outbox.
 */
public interface BusinessEventCatalog {
    Optional<CatalogEvent> find(String eventName, int eventVersion);
}
