package br.com.ziipsan.platform.infra.events;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo único de eventos de negócio (P1.38, ADR-0037): declarado por cada
 * pacote no {@code vertical.json} (seção {@code events}) e lido por
 * {@link BusinessEventCatalogLoader}. Um evento tem nome, versão, esquema da
 * carga, permissão exigida de quem recebe e consumidores declarados.
 *
 * <p>Semântica de versão: a carga de uma versão é imutável. Mudança
 * incompatível de carga exige uma NOVA versão do evento (a versão antiga
 * continua declarada); publicar, na mesma versão, uma carga que não segue o
 * esquema daquela versão é recusado por
 * {@link #requirePublishable(String, int, String)}.
 *
 * <p>A carga carrega só identificadores e códigos, nunca dado pessoal: o
 * validador do catálogo recusa nomes de campo de dado pessoal (ver
 * {@link BusinessEventCatalogValidator}).
 */
public record BusinessEventCatalog(
        List<BusinessEvent> events,
        List<DeclaredConsumer> declaredConsumers,
        List<CatalogGap> gaps) {

    /** Um evento de negócio declarado. */
    public record BusinessEvent(
            String name,
            int version,
            String description,
            JsonNode payloadSchema,
            String requiredPermission,
            List<String> consumers,
            String originTask) {
    }

    /** Um consumidor declarado no catálogo (só declarado, não construído). */
    public record DeclaredConsumer(String task, String name) {
    }

    /** Um tipo que aparece no código mas NÃO é evento declarado (lacuna). */
    public record CatalogGap(String name, String reason) {
    }

    /** Evento pelo nome e versão, ou vazio se a versão não está declarada. */
    public Optional<BusinessEvent> find(String name, int version) {
        return events().stream()
                .filter(e -> e.name().equals(name) && e.version() == version)
                .findFirst();
    }

    /** Verdadeiro se alguma versão do nome está declarada. */
    public boolean isCatalogued(String name) {
        return events().stream().anyMatch(e -> e.name().equals(name));
    }

    /**
     * Caminho de validação de publicação, compartilhado por
     * {@link BusinessEventPublisher} e pela outbox validada: evento fora do
     * catálogo, versão não declarada ou carga fora do esquema lança
     * {@link IllegalArgumentException} antes de qualquer escrita. Devolve o
     * evento declarado, para o publicador registrar o tipo versionado.
     */
    public BusinessEvent requirePublishable(String name, int version, String payloadJson) {
        BusinessEvent event = find(name, version).orElseThrow(() -> isCatalogued(name)
                ? new IllegalArgumentException(
                        "Versao nao declarada para o evento " + name + ": v" + version)
                : new IllegalArgumentException("Evento fora do catalogo: " + name));
        List<String> errors = BusinessEventPayloadValidator.validate(payloadJson, event.payloadSchema());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Carga fora do esquema do evento " + name + " v" + version + ": "
                            + String.join("; ", errors));
        }
        return event;
    }
}
