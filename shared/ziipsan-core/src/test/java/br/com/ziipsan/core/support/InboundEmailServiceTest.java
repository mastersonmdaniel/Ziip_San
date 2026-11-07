package br.com.ziipsan.core.support;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4.8: o serviço de entrada escreve UMA vez (idempotência por
 * messageId) e recusa sem escrever nada quando a política nega.
 */
class InboundEmailServiceTest {

    private static final class FakeGateway implements InboundEmailGateway {
        final Set<String> ingested = new HashSet<>();
        final List<String> appended = new java.util.ArrayList<>();
        InboundEmailPolicy.TicketFacts ticket = new InboundEmailPolicy.TicketFacts(
                UUID.fromString("33333333-3333-3333-3333-333333333333"), "SUP-2026-000123",
                "IN_PROGRESS", UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "solicitante@tenant.example");

        @Override
        public Optional<InboundEmailPolicy.TicketFacts> findTicket(String ticketNumber) {
            return Optional.ofNullable(ticket);
        }

        @Override
        public UUID appendRequesterMessage(UUID ticketId, UUID requesterId, String body) {
            appended.add(body);
            return UUID.fromString("55555555-5555-5555-5555-555555555555");
        }

        @Override
        public boolean alreadyIngested(String messageId) {
            return ingested.contains(messageId);
        }

        @Override
        public void markIngested(String messageId, UUID ticketId, UUID messageIdCreated) {
            ingested.add(messageId);
        }
    }

    private static InboundEmail email(boolean verified) {
        return new InboundEmail("msg-9", "solicitante@tenant.example", verified,
                "Re: SUP-2026-000123", "Corpo da resposta", List.of(),
                OffsetDateTime.parse("2026-11-07T12:00:00-03:00"));
    }

    @Test
    @DisplayName("P4.8: ingestão aceita escreve uma mensagem; reentrega do mesmo messageId não duplica")
    void ingestsOncePerMessageId() {
        FakeGateway gw = new FakeGateway();
        InboundEmailService service = new InboundEmailService(gw);
        assertThat(service.ingest(email(true)).duplicate()).isFalse();
        assertThat(service.ingest(email(true)).duplicate()).isTrue();
        assertThat(gw.appended).hasSize(1);
    }

    @Test
    @DisplayName("P4.8: ingestão recusada pela política lança 422 e não escreve mensagem nenhuma")
    void refusedIngestionWritesNothing() {
        FakeGateway gw = new FakeGateway();
        InboundEmailService service = new InboundEmailService(gw);
        assertThatThrownBy(() -> service.ingest(email(false)))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(gw.appended).isEmpty();
        assertThat(gw.ingested).isEmpty();
    }
}
