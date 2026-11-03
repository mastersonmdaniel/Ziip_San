package br.com.ziipsan.platform.infra.events;

import br.com.ziipsan.platform.infra.InMemoryMessageQueue;
import br.com.ziipsan.platform.infra.queue.PostgresMessageQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.38: catálogo único de eventos de negócio. O catálogo sintético aqui é
 * só de teste (nunca entra no manifesto real): duas versões do mesmo evento,
 * para provar que carga incompatível com a versão é recusada naquela versão
 * e aceita na versão nova.
 */
class BusinessEventCatalogTest {

    static final String CATALOG_JSON = BusinessEventCatalogFixture.CATALOG_JSON;

    static final String THING_ID = "11111111-1111-1111-1111-111111111111";
    static final String PARENT_ID = "22222222-2222-2222-2222-222222222222";

    private BusinessEventCatalog catalog() {
        return BusinessEventCatalogLoader.fromJson(CATALOG_JSON);
    }

    private BusinessEventPublisher publisher(InMemoryMessageQueue queue) {
        return new BusinessEventPublisher(queue, catalog());
    }

    @Test
    @DisplayName("publicação válida grava o tipo versionado na fila")
    void validPublishWritesVersionedType() {
        var queue = new InMemoryMessageQueue();
        UUID id = publisher(queue).publish(UUID.randomUUID(), "example.thing.created", 1,
                "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"CREATED\"}");
        assertThat(queue.published()).hasSize(1);
        assertThat(queue.published().get(0).messageId()).isEqualTo(id);
        assertThat(queue.published().get(0).type()).isEqualTo("example.thing.created@v1");
    }

    @Test
    @DisplayName("evento fora do catálogo é recusado e nada é publicado")
    void uncataloguedEventIsRefused() {
        var queue = new InMemoryMessageQueue();
        assertThatThrownBy(() -> publisher(queue).publish(UUID.randomUUID(), "example.thing.deleted", 1,
                "{\"thingId\":\"" + THING_ID + "\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do catalogo");
        assertThat(queue.published()).isEmpty();
    }

    @Test
    @DisplayName("versão não declarada é recusada e nada é publicado")
    void undeclaredVersionIsRefused() {
        var queue = new InMemoryMessageQueue();
        assertThatThrownBy(() -> publisher(queue).publish(UUID.randomUUID(), "example.thing.created", 3,
                "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"CREATED\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Versao nao declarada");
        assertThat(queue.published()).isEmpty();
    }

    @Test
    @DisplayName("carga fora do esquema é recusada: campo obrigatório, tipo, enum, uuid e campo extra")
    void offSchemaPayloadsAreRefused() {
        var queue = new InMemoryMessageQueue();
        var publisher = publisher(queue);
        UUID tenant = UUID.randomUUID();
        assertThatThrownBy(() -> publisher.publish(tenant, "example.thing.created", 1,
                "{\"thingId\":\"" + THING_ID + "\"}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obrigatorio");
        assertThatThrownBy(() -> publisher.publish(tenant, "example.thing.created", 1,
                "{\"thingId\":123,\"statusCode\":\"CREATED\"}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("string");
        assertThatThrownBy(() -> publisher.publish(tenant, "example.thing.created", 1,
                "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"DELETED\"}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("enum");
        assertThatThrownBy(() -> publisher.publish(tenant, "example.thing.created", 1,
                "{\"thingId\":\"not-a-uuid\",\"statusCode\":\"CREATED\"}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("uuid");
        assertThatThrownBy(() -> publisher.publish(tenant, "example.thing.created", 1,
                "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"CREATED\",\"extra\":1}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nao esta no esquema");
        assertThatThrownBy(() -> publisher.publish(tenant, "example.thing.created", 1, "nao-json"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON valido");
        assertThat(queue.published()).isEmpty();
    }

    @Test
    @DisplayName("mudança incompatível exige nova versão: carga da v2 é recusada na v1 e aceita na v2")
    void incompatibleChangeRequiresNewVersion() {
        var queue = new InMemoryMessageQueue();
        var publisher = publisher(queue);
        UUID tenant = UUID.randomUUID();
        String v2Payload = "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"CREATED\","
                + "\"parentThingId\":\"" + PARENT_ID + "\"}";
        assertThatThrownBy(() -> publisher.publish(tenant, "example.thing.created", 1, v2Payload))
                .isInstanceOf(IllegalArgumentException.class);
        publisher.publish(tenant, "example.thing.created", 2, v2Payload);
        assertThat(queue.published()).hasSize(1);
        assertThat(queue.published().get(0).type()).isEqualTo("example.thing.created@v2");
    }

    @Test
    @DisplayName("destinatário só recebe evento cuja permissão tem")
    void recipientGate() {
        var event = catalog().find("example.thing.created", 1).orElseThrow();
        assertThat(EventRecipientGate.canDeliver(event, Set.of("registry:read"))).isTrue();
        assertThat(EventRecipientGate.canDeliver(event, Set.of("registry:write"))).isFalse();
        assertThat(EventRecipientGate.canDeliver(event, Set.of())).isFalse();
        assertThat(EventRecipientGate.canDeliver(event, null)).isFalse();
        assertThat(EventRecipientGate.canDeliver(event, Set.of("registry:read", "user:read"))).isTrue();
    }

    @Test
    @DisplayName("validador do catálogo recusa permissão inventada, dado pessoal, duplicata e consumidor não declarado")
    void catalogValidatorRefuses() {
        assertThatThrownBy(() -> BusinessEventCatalogLoader.fromJson(
                CATALOG_JSON.replace("\"registry:read\"", "\"billing:read\"")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("P0.13");
        assertThatThrownBy(() -> BusinessEventCatalogLoader.fromJson(
                CATALOG_JSON.replace("\"statusCode\"", "\"customerEmail\"")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dado pessoal");
        assertThatThrownBy(() -> BusinessEventCatalogLoader.fromJson(
                CATALOG_JSON.replace("\"version\": 2", "\"version\": 1")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicado");
        assertThatThrownBy(() -> BusinessEventCatalogLoader.fromJson(
                CATALOG_JSON.replace("\"consumers\": [\"P3.36\", \"P2.26\"]",
                        "\"consumers\": [\"P3.36\", \"P9.99\"]")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nao esta declarado");
    }

    @Test
    @DisplayName("campo de dado pessoal dentro de item de array também é recusado no catálogo")
    void personalDataInsideArrayItemsIsRefused() {
        String json = CATALOG_JSON
                .replace("\"statusCode\": {\"type\": \"string\", \"enum\": [\"CREATED\"]}",
                        "\"statusCode\": {\"type\": \"string\", \"enum\": [\"CREATED\"]},\n"
                                + "\"refs\": {\"type\": \"array\", \"items\": {\"type\": \"object\", "
                                + "\"properties\": {\"thingId\": {\"type\": \"string\"}, "
                                + "\"customerEmail\": {\"type\": \"string\"}}}}");
        assertThatThrownBy(() -> BusinessEventCatalogLoader.fromJson(json))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dado pessoal");
    }

    @Test
    @DisplayName("array de objetos só com identificadores é aceito no catálogo")
    void arrayOfIdentifierObjectsIsAllowed() {
        String json = CATALOG_JSON
                .replace("\"statusCode\": {\"type\": \"string\", \"enum\": [\"CREATED\"]}",
                        "\"statusCode\": {\"type\": \"string\", \"enum\": [\"CREATED\"]},\n"
                                + "\"refs\": {\"type\": \"array\", \"items\": {\"type\": \"object\", "
                                + "\"properties\": {\"thingId\": {\"type\": \"string\"}, "
                                + "\"statusCode\": {\"type\": \"string\"}}}}");
        BusinessEventCatalog loaded = BusinessEventCatalogLoader.fromJson(json);
        assertThat(loaded.find("example.thing.created", 1)).isPresent();
    }

    @Test
    @DisplayName("campo identificador com token de documento é código, não dado pessoal")
    void identifierFieldWithDocumentTokenIsAllowed() {
        String json = CATALOG_JSON
                .replace("\"statusCode\": {\"type\": \"string\", \"enum\": [\"CREATED\"]}",
                        "\"statusCode\": {\"type\": \"string\", \"enum\": [\"CREATED\"]},\n"
                                + "\"documentTypeId\": {\"type\": \"string\"}");
        BusinessEventCatalog loaded = BusinessEventCatalogLoader.fromJson(json);
        assertThat(loaded.find("example.thing.created", 1)).isPresent();
    }

    @Test
    @DisplayName("fila validada recusa evento de negócio antes de tocar no banco (sem DataSource real)")
    void validatedQueueRefusesBeforeDatabase() {
        var unreachable = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/nope", "x", "y");
        var queue = new PostgresMessageQueue(unreachable, catalog());
        UUID tenant = UUID.randomUUID();
        assertThatThrownBy(() -> queue.publish(new br.com.ziipsan.platform.infra.queue.MessageQueue.QueuedMessage(
                UUID.randomUUID(), tenant, "example.thing.created@v3",
                "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"CREATED\"}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Versao nao declarada");
        assertThatThrownBy(() -> queue.publish(new br.com.ziipsan.platform.infra.queue.MessageQueue.QueuedMessage(
                UUID.randomUUID(), tenant, "example.thing.created",
                "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"CREATED\"}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sem versao");
        assertThatThrownBy(() -> queue.publish(new br.com.ziipsan.platform.infra.queue.MessageQueue.QueuedMessage(
                UUID.randomUUID(), tenant, "example.thing.created@v1", "{\"thingId\":1}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("esquema");
    }

    static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (java.nio.file.Files.exists(dir.resolve("packages/sanitation/vertical.json"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Raiz do repositorio nao encontrada a partir de user.dir");
    }

    @Test
    @DisplayName("manifesto real carrega com catálogo vazio, consumidores declarados e a lacuna da P1.9")
    void realManifestLoadsWithEmptyCatalogAndGap() {
        Path root = repoRoot();
        BusinessEventCatalog real = BusinessEventCatalogLoader.load(
                root.resolve("packages/sanitation/vertical.json"));
        assertThat(real.events()).isEmpty();
        assertThat(real.declaredConsumers().stream().map(BusinessEventCatalog.DeclaredConsumer::task))
                .containsExactlyInAnyOrder("P3.36", "P3.5", "ED-41", "P2.26", "P2.28", "ED-07");
        assertThat(real.gaps().stream().map(BusinessEventCatalog.CatalogGap::name))
                .contains("billing.invoice.issued");
        assertThat(real.isCatalogued("billing.invoice.issued")).isFalse();
        var queue = new InMemoryMessageQueue();
        var publisher = new BusinessEventPublisher(queue, real);
        assertThatThrownBy(() -> publisher.publish(UUID.randomUUID(), "billing.invoice.issued", 1, "{\"id\":1}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fora do catalogo");
        assertThat(queue.published()).isEmpty();
    }
}
