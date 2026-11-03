package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.events.BusinessEventCatalog;
import br.com.ziipsan.platform.infra.events.BusinessEventCatalogFixture;
import br.com.ziipsan.platform.infra.events.BusinessEventCatalogLoader;
import br.com.ziipsan.platform.infra.events.BusinessEventPublisher;
import br.com.ziipsan.platform.infra.queue.MessageQueue;
import br.com.ziipsan.platform.infra.queue.PostgresMessageQueue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.38 sobre a outbox real (P1.9): a validação do catálogo acontece no
 * caminho de publicação, antes de qualquer escrita — não é um teste isolado
 * do validador. Publicação válida grava a linha com o tipo versionado;
 * fora do catálogo, em versão não declarada ou com carga fora do esquema,
 * nada é gravado. Tipo de transporte que não é evento passa direto.
 */
class BusinessEventOutboxIT {

    private static final String THING_ID = "33333333-3333-3333-3333-333333333333";

    @BeforeAll
    static void ensureDatabase() {
        OutboxItDatabase.ensure();
    }

    private BusinessEventCatalog catalog() {
        return BusinessEventCatalogLoader.fromJson(BusinessEventCatalogFixture.CATALOG_JSON);
    }

    private PostgresMessageQueue validatedQueueOn(Connection connection) {
        return new PostgresMessageQueue(new SingleConnectionDataSource(connection, true), catalog());
    }

    private String typeOf(Connection connection, UUID id, UUID tenantId) throws Exception {
        OutboxItDatabase.setTenantSession(connection, tenantId);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT type FROM public.outbox WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private int count(Connection connection, UUID id, UUID tenantId) throws Exception {
        OutboxItDatabase.setTenantSession(connection, tenantId);
        return OutboxItDatabase.countById(connection, id);
    }

    @Test
    @DisplayName("publicação válida pela outbox validada grava a linha com o tipo versionado")
    void validPublishWritesRowWithVersionedType() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        try (Connection connection = OutboxItDatabase.appConnection()) {
            new BusinessEventPublisher(validatedQueueOn(connection), catalog())
                    .publish(messageId, tenant, "example.thing.created", 1,
                            "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"CREATED\"}");
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            assertThat(count(connection, messageId, tenant)).isEqualTo(1);
            assertThat(typeOf(connection, messageId, tenant)).isEqualTo("example.thing.created@v1");
        }
    }

    @Test
    @DisplayName("fora do catálogo, versão não declarada e carga fora do esquema não gravam nada")
    void refusedPublishesWriteNothing() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID uncatalogued = UUID.randomUUID();
        UUID undeclaredVersion = UUID.randomUUID();
        UUID offSchema = UUID.randomUUID();
        UUID bareName = UUID.randomUUID();
        UUID versionedBadPayload = UUID.randomUUID();
        try (Connection connection = OutboxItDatabase.appConnection()) {
            var publisher = new BusinessEventPublisher(validatedQueueOn(connection), catalog());
            var queue = validatedQueueOn(connection);
            assertThatThrownBy(() -> publisher.publish(uncatalogued, tenant, "example.thing.deleted", 1,
                    "{\"thingId\":\"" + THING_ID + "\"}"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> publisher.publish(undeclaredVersion, tenant, "example.thing.created", 9,
                    "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"CREATED\"}"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> publisher.publish(offSchema, tenant, "example.thing.created", 1,
                    "{\"thingId\":\"" + THING_ID + "\"}"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.publish(new MessageQueue.QueuedMessage(
                    bareName, tenant, "example.thing.created",
                    "{\"thingId\":\"" + THING_ID + "\",\"statusCode\":\"CREATED\"}")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> queue.publish(new MessageQueue.QueuedMessage(
                    versionedBadPayload, tenant, "example.thing.created@v1", "{\"thingId\":1}")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            assertThat(count(connection, uncatalogued, tenant)).isZero();
            assertThat(count(connection, undeclaredVersion, tenant)).isZero();
            assertThat(count(connection, offSchema, tenant)).isZero();
            assertThat(count(connection, bareName, tenant)).isZero();
            assertThat(count(connection, versionedBadPayload, tenant)).isZero();
        }
    }

    @Test
    @DisplayName("tipo de transporte que não é evento passa pela fila validada sem validação de catálogo")
    void nonBusinessTransportTypePassesThrough() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        try (Connection connection = OutboxItDatabase.appConnection()) {
            validatedQueueOn(connection).publish(new MessageQueue.QueuedMessage(
                    messageId, tenant, "infra.heartbeat", "{\"tick\":1}"));
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            assertThat(count(connection, messageId, tenant)).isEqualTo(1);
            assertThat(typeOf(connection, messageId, tenant)).isEqualTo("infra.heartbeat");
        }
    }
}
