package br.com.ziipsan.core.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4.8: resposta por e-mail só entra no chamado certo de remetente
 * verificado e sem anexo não varrido — cada recusa com o seu código.
 */
class InboundEmailPolicyTest {

    private static final UUID TICKET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REQUESTER = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-07T12:00:00-03:00");

    private static InboundEmail email(boolean verified, String from, String subject,
                                      List<InboundEmail.EmailAttachment> attachments) {
        return new InboundEmail("msg-1", from, verified, subject, "Segue retorno.", attachments, NOW);
    }

    private static InboundEmailPolicy.TicketFacts ticket(String status) {
        return new InboundEmailPolicy.TicketFacts(TICKET_ID, "SUP-2026-000123", status,
                REQUESTER, "solicitante@tenant.example");
    }

    private static InboundEmail.EmailAttachment attachment(String scan) {
        return new InboundEmail.EmailAttachment("print.png", "image/png", 1024,
                "a".repeat(64), scan);
    }

    @Test
    @DisplayName("P4.8: remetente verificado, chamado aberto e anexos CLEAN — a resposta entra")
    void acceptsVerifiedSenderWithCleanAttachments() {
        InboundEmail e = email(true, "solicitante@tenant.example", "Re: SUP-2026-000123",
                List.of(attachment("CLEAN")));
        assertThat(InboundEmailPolicy.evaluate(e, ticket("WAITING_CUSTOMER")).accepted()).isTrue();
        assertThat(InboundEmailPolicy.evaluate(
                email(true, "solicitante@tenant.example", "SUP-2026-000123", List.of()),
                ticket("IN_PROGRESS")).accepted()).isTrue();
    }

    @Test
    @DisplayName("P4.8: remetente NÃO verificado não entra, mesmo citando o chamado certo")
    void refusesUnverifiedSender() {
        InboundEmailPolicy.Decision d = InboundEmailPolicy.evaluate(
                email(false, "solicitante@tenant.example", "Re: SUP-2026-000123", List.of()),
                ticket("IN_PROGRESS"));
        assertThat(d.accepted()).isFalse();
        assertThat(d.code()).isEqualTo("sender-not-verified");
    }

    @Test
    @DisplayName("P4.8: anexo não varrido (QUARANTINED/NOT_SCANNED) ou infectado barra a resposta inteira")
    void refusesUnscannedAttachment() {
        for (String scan : List.of("QUARANTINED", "NOT_SCANNED", "INFECTED")) {
            InboundEmailPolicy.Decision d = InboundEmailPolicy.evaluate(
                    email(true, "solicitante@tenant.example", "Re: SUP-2026-000123",
                            List.of(attachment("CLEAN"), attachment(scan))),
                    ticket("IN_PROGRESS"));
            assertThat(d.accepted()).as(scan).isFalse();
            assertThat(d.code()).isEqualTo("attachment-not-scanned");
        }
    }

    @Test
    @DisplayName("P4.8: sem número no assunto, com número de outro chamado ou de terceiro verificado — não entra")
    void refusesWrongOrMissingReferenceAndSenderMismatch() {
        assertThat(InboundEmailPolicy.evaluate(
                email(true, "solicitante@tenant.example", "Re: dúvida", List.of()),
                ticket("IN_PROGRESS")).code()).isEqualTo("ticket-not-referenced");
        assertThat(InboundEmailPolicy.evaluate(
                email(true, "solicitante@tenant.example", "Re: SUP-2026-999999", List.of()),
                null).code()).isEqualTo("ticket-not-found");
        assertThat(InboundEmailPolicy.evaluate(
                email(true, "outra.pessoa@tenant.example", "Re: SUP-2026-000123", List.of()),
                ticket("IN_PROGRESS")).code()).isEqualTo("sender-mismatch");
    }

    @Test
    @DisplayName("P4.8: chamado encerrado (CLOSED/CANCELLED) não recebe resposta por e-mail")
    void refusesClosedTicket() {
        for (String status : List.of("CLOSED", "CANCELLED")) {
            assertThat(InboundEmailPolicy.evaluate(
                    email(true, "solicitante@tenant.example", "Re: SUP-2026-000123", List.of()),
                    ticket(status)).code()).isEqualTo("ticket-closed");
        }
    }
}
