package br.com.ziipsan.platform.omnichannel;

import java.time.Instant;
import java.util.UUID;

/**
 * Conversa de atendimento de UM canal, registrada no protocolo da
 * matrícula (aceite da ED-42: "conversa registrada no protocolo da
 * matrícula"): {@code protocolId} é o protocolo único da P1.36 (V8_1,
 * {@code service_protocols.id} UUID) e {@code consumerUnitId} é a
 * matrícula/unidade consumidora à qual o protocolo pertence. Sem
 * protocolo, sem matrícula e sem morador ({@code customerId}) a conversa
 * NÃO nasce — o construtor recusa os três nulos, alinhado à recusa
 * {@code IDENTITY_UNRESOLVED} do serviço de entrada: atendimento solto,
 * sem lastro no protocolo, é exatamente o que o aceite proíbe.
 *
 * <p>{@code externalThreadId} é a identidade da conversa NO canal (a
 * thread do WhatsApp/Meta/Telegram, o thread-id do e-mail, a sessão do
 * chat do portal): é a chave de correlação das mensagens e do destino
 * das respostas, resolvida pela borda do canal — nunca um dado pessoal
 * solto na carga.
 *
 * <p>Transições: nasce {@code OPEN}; {@code assign} leva a
 * {@code ASSIGNED}; a resposta do atendente leva a
 * {@code WAITING_CUSTOMER}; mensagem nova do morador devolve a
 * {@code ASSIGNED}/{@code OPEN}; {@code close} encerra de qualquer
 * estado aberto. Conversa encerrada é terminal: não atribui, não
 * responde, não reabre (reabertura é conversa nova no mesmo protocolo).
 * NENHUM prazo de atendimento vive aqui: SLA de atendimento sem fonte é
 * {@code [A DEFINIR]} (pendência nomeada no relatório) — a fila ordena
 * por prioridade e antiguidade, sem prometer prazo.
 */
public record Conversation(
        UUID id, UUID tenantId, UUID protocolId, UUID consumerUnitId, UUID customerId,
        OmnichannelChannel channel, String externalThreadId, Status status, UUID assignedTo,
        boolean priorityProfile, Instant openedAt, Instant closedAt) {

    public enum Status { OPEN, ASSIGNED, WAITING_CUSTOMER, CLOSED }

    public Conversation {
        if (id == null || tenantId == null || protocolId == null || consumerUnitId == null
                || customerId == null || channel == null || status == null || openedAt == null) {
            throw new IllegalArgumentException(
                    "conversa exige id, tenant, protocolo da matrícula, unidade consumidora, morador, canal, situação e abertura");
        }
        if (externalThreadId == null || externalThreadId.isBlank()) {
            throw new IllegalArgumentException("conversa exige a thread do canal (correlação das mensagens)");
        }
        if (status == Status.CLOSED && closedAt == null) {
            throw new IllegalArgumentException("conversa encerrada exige o instante do encerramento");
        }
        if (status != Status.CLOSED && closedAt != null) {
            throw new IllegalArgumentException("conversa aberta não tem instante de encerramento");
        }
    }

    public static Conversation open(UUID id, UUID tenantId, UUID protocolId, UUID consumerUnitId,
                                    UUID customerId, OmnichannelChannel channel, String externalThreadId,
                                    boolean priorityProfile, Instant openedAt) {
        return new Conversation(id, tenantId, protocolId, consumerUnitId, customerId, channel,
                externalThreadId, Status.OPEN, null, priorityProfile, openedAt, null);
    }

    public Conversation assign(UUID attendantId) {
        requireOpen("atribuir");
        if (attendantId == null) {
            throw new IllegalArgumentException("a atribuição exige o atendente");
        }
        return new Conversation(id, tenantId, protocolId, consumerUnitId, customerId, channel,
                externalThreadId, Status.ASSIGNED, attendantId, priorityProfile, openedAt, null);
    }

    /** Resposta do atendente registrada: a conversa aguarda o morador. */
    public Conversation markReplied() {
        requireOpen("responder");
        return new Conversation(id, tenantId, protocolId, consumerUnitId, customerId, channel,
                externalThreadId, Status.WAITING_CUSTOMER, assignedTo, priorityProfile, openedAt, null);
    }

    /** Mensagem nova do morador: volta para a fila do atendente responsável (ou aberta, se ninguém assumiu). */
    public Conversation markCustomerMessage() {
        requireOpen("receber mensagem");
        Status next = assignedTo != null ? Status.ASSIGNED : Status.OPEN;
        return new Conversation(id, tenantId, protocolId, consumerUnitId, customerId, channel,
                externalThreadId, next, assignedTo, priorityProfile, openedAt, null);
    }

    public Conversation close(Instant at) {
        requireOpen("encerrar");
        if (at == null || at.isBefore(openedAt)) {
            throw new IllegalArgumentException("o encerramento exige instante não anterior à abertura");
        }
        return new Conversation(id, tenantId, protocolId, consumerUnitId, customerId, channel,
                externalThreadId, Status.CLOSED, assignedTo, priorityProfile, openedAt, at);
    }

    public boolean isOpen() { return status != Status.CLOSED; }

    private void requireOpen(String operation) {
        if (status == Status.CLOSED) {
            throw new IllegalStateException("conversa encerrada não pode " + operation);
        }
    }
}
