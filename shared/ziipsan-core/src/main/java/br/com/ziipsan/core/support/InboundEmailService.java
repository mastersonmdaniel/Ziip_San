package br.com.ziipsan.core.support;

import br.com.ziipsan.core.api.ApiException;

import java.util.Objects;
import java.util.UUID;

/**
 * P4.8: entrada da resposta por e-mail no chamado (ADR-0020).
 *
 * <p>Fluxo: idempotência pelo {@code messageId} → localização do chamado
 * citado no assunto → {@link InboundEmailPolicy} → escrita como mensagem
 * do solicitante pela porta. Recusa da política vira
 * {@link ApiException.Validation} (422) com o código estável da política
 * no campo {@code email}: o gateway de entrada registra a recusa e NÃO
 * tenta de novo em outro chamado — resposta recusada não procura outro
 * destino.
 *
 * <p>Anexos: a política só deixa passar resposta cujos anexos estejam
 * todos {@code CLEAN}; o REGISTRO do anexo no chamado (pipeline da P1.20,
 * {@code TicketService.registerAttachment}) é da fiação que entrega os
 * bytes — aqui nenhum byte é tocado nem inventado.
 */
public final class InboundEmailService {

    /** Resultado da ingestão aceita. */
    public record Ingested(UUID ticketId, String ticketNumber, UUID messageIdCreated, boolean duplicate) {
    }

    private final InboundEmailGateway gateway;

    public InboundEmailService(InboundEmailGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway é obrigatório");
    }

    public Ingested ingest(InboundEmail email) {
        Objects.requireNonNull(email, "email é obrigatório");
        if (gateway.alreadyIngested(email.messageId())) {
            // Reentrega do provedor: não duplica a mensagem no chamado.
            return new Ingested(null, email.referencedTicketNumber(), null, true);
        }
        String number = email.referencedTicketNumber();
        InboundEmailPolicy.TicketFacts ticket = number == null ? null
                : gateway.findTicket(number).orElse(null);
        InboundEmailPolicy.Decision decision = InboundEmailPolicy.evaluate(email, ticket);
        if (!decision.accepted()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "email", decision.code(), decision.message()));
        }
        UUID created = gateway.appendRequesterMessage(ticket.ticketId(), ticket.requesterId(), email.body());
        gateway.markIngested(email.messageId(), ticket.ticketId(), created);
        return new Ingested(ticket.ticketId(), ticket.number(), created, false);
    }
}
