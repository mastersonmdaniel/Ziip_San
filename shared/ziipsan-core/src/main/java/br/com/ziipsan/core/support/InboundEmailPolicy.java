package br.com.ziipsan.core.support;

import java.util.Objects;
import java.util.UUID;

/**
 * P4.8: regras puras da resposta por e-mail (ADR-0020). Sem banco e sem
 * relógio: os fatos (chamado, solicitante verificado, varredura) chegam
 * prontos de quem chama e a decisão sai com código estável, no padrão da
 * {@code AccessPolicy} da P3.17 — regra e recusa nunca divergem no motivo.
 *
 * <p>Aceite, literal: "resposta por e-mail entra no chamado certo SÓ de
 * remetente verificado e sem anexo não varrido". Disso saem as recusas,
 * nesta ordem (a primeira que falhar decide):
 * <ol>
 *   <li>{@code ticket-not-referenced} — o assunto não cita número
 *       {@code SUP-AAAA-NNNNNN}: sem referência, não há "chamado certo";</li>
 *   <li>{@code ticket-not-found} — o número citado não é de um chamado
 *       deste tenant (quem lê já aplica o contexto RLS; fora do tenant, o
 *       chamado não existe, como em todo o {@link TicketService});</li>
 *   <li>{@code sender-not-verified} — remetente sem verificação;</li>
 *   <li>{@code sender-mismatch} — remetente verificado, mas NÃO é o
 *       endereço verificado do solicitante deste chamado (e-mail de
 *       terceiro verificado não escreve no chamado alheio);</li>
 *   <li>{@code ticket-closed} — chamado {@code CLOSED}/{@code CANCELLED}
 *       é terminal na P1.23: a resposta não reabre por e-mail;</li>
 *   <li>{@code attachment-not-scanned} — algum anexo não está
 *       {@code CLEAN} (em quarentena, não varrido ou infectado): a
 *       resposta inteira espera/recusa, nunca entra parcial.</li>
 * </ol>
 */
public final class InboundEmailPolicy {

    /** Fatos do chamado de destino, como a porta de leitura os entrega. */
    public record TicketFacts(UUID ticketId, String number, String status,
                              UUID requesterId, String requesterVerifiedEmail) {
        public TicketFacts {
            Objects.requireNonNull(ticketId, "ticketId é obrigatório");
            Objects.requireNonNull(number, "number do chamado é obrigatório");
            Objects.requireNonNull(status, "status do chamado é obrigatório");
        }
    }

    /** Decisão: aceita, ou recusada com o código estável do motivo. */
    public record Decision(boolean accepted, String code, String message) {
        static Decision accept() {
            return new Decision(true, "accepted", "Resposta por e-mail aceita para o chamado.");
        }

        static Decision reject(String code, String message) {
            return new Decision(false, code, message);
        }
    }

    private InboundEmailPolicy() {
    }

    public static Decision evaluate(InboundEmail email, TicketFacts ticket) {
        Objects.requireNonNull(email, "email é obrigatório");
        String referenced = email.referencedTicketNumber();
        if (referenced == null) {
            return Decision.reject("ticket-not-referenced",
                    "O assunto não cita o número do chamado (SUP-AAAA-NNNNNN); a resposta não entra em nenhum chamado.");
        }
        if (ticket == null) {
            return Decision.reject("ticket-not-found",
                    "O chamado citado não existe para este tenant.");
        }
        if (!referenced.equals(ticket.number())) {
            return Decision.reject("ticket-not-found",
                    "O número citado no assunto não é o do chamado localizado.");
        }
        if (!email.senderVerified()) {
            return Decision.reject("sender-not-verified",
                    "Remetente não verificado: a resposta por e-mail não entra no chamado.");
        }
        if (ticket.requesterVerifiedEmail() == null
                || !email.fromAddress().trim().equalsIgnoreCase(ticket.requesterVerifiedEmail().trim())) {
            return Decision.reject("sender-mismatch",
                    "O remetente não é o endereço verificado do solicitante deste chamado.");
        }
        if ("CLOSED".equals(ticket.status()) || "CANCELLED".equals(ticket.status())) {
            return Decision.reject("ticket-closed",
                    "O chamado está encerrado; a resposta por e-mail não o reabre.");
        }
        if (email.hasUnscannedOrUnsafeAttachment()) {
            return Decision.reject("attachment-not-scanned",
                    "A resposta tem anexo sem varredura concluída (CLEAN); nada entra até a varredura terminar limpa.");
        }
        return Decision.accept();
    }
}
