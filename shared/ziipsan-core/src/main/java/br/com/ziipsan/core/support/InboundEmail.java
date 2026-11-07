package br.com.ziipsan.core.support;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P4.8: e-mail de resposta do solicitante que deve entrar no chamado certo
 * (ADR-0020). Fatos do e-mail JÁ recebidos pelo gateway de entrada — este
 * record não busca e-mail em provedor nenhum; quem entrega os fatos é a
 * fiação de integração (provedor de e-mail da P3.5/LC.2).
 *
 * <p>{@code senderVerified} NÃO é auto-declaração de quem chama: é o
 * resultado da verificação do remetente feita pelo gateway (o endereço
 * {@code from} conferido contra o endereço verificado do solicitante do
 * chamado e/ou autenticação do domínio). A política
 * ({@link InboundEmailPolicy}) só lê o fato; sem ele, nada entra.
 *
 * @param attachments anexos do e-mail com o estado de varredura do
 *                    pipeline da P1.20 (mesmos estados de
 *                    {@link TicketService#SCAN_STATUSES}); anexo sem
 *                    varredura concluída {@code CLEAN} barra a entrada
 *                    INTEIRA da resposta (aceite da P4.8: "sem anexo não
 *                    varrido") — nunca entra o texto e cai o anexo em
 *                    silêncio.
 */
public record InboundEmail(
        String messageId,
        String fromAddress,
        boolean senderVerified,
        String subject,
        String body,
        List<EmailAttachment> attachments,
        OffsetDateTime receivedAt) {

    /** Anexo do e-mail, no estado em que o pipeline de varredura o deixou. */
    public record EmailAttachment(String fileName, String detectedMime, long sizeBytes,
                                  String sha256, String scanStatus) {
        public EmailAttachment {
            Objects.requireNonNull(fileName, "fileName do anexo é obrigatório");
            Objects.requireNonNull(scanStatus, "scanStatus do anexo é obrigatório");
        }

        /** Só {@code CLEAN} é "varrido": QUARANTINED/NOT_SCANNED ainda não foram varridos. */
        public boolean isScannedClean() {
            return "CLEAN".equals(scanStatus);
        }
    }

    private static final Pattern TICKET_NUMBER =
            Pattern.compile("SUP-[0-9]{4}-[0-9]{6}");

    public InboundEmail {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId do e-mail é obrigatório (idempotência da entrada)");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("from do e-mail é obrigatório");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("corpo do e-mail é obrigatório");
        }
        Objects.requireNonNull(receivedAt, "receivedAt do e-mail é obrigatório");
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /**
     * Número do chamado citado no assunto (formato da P1.23,
     * {@code SUP-AAAA-NNNNNN}), ou {@code null} quando o assunto não cita
     * nenhum — caso em que a resposta NÃO entra em chamado algum (a
     * política recusa; adivinhar o chamado por remetente ou por corpo
     * colocaria conversa no chamado errado).
     */
    public String referencedTicketNumber() {
        if (subject == null) {
            return null;
        }
        Matcher m = TICKET_NUMBER.matcher(subject);
        return m.find() ? m.group() : null;
    }

    /** Verdadeiro quando algum anexo ainda não foi varrido ou foi reprovado. */
    public boolean hasUnscannedOrUnsafeAttachment() {
        return attachments.stream().anyMatch(a -> !a.isScannedClean());
    }
}
