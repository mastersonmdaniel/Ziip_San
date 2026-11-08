package br.com.ziipsan.platform.omnichannel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas do omnichannel (ED-42), agrupadas como em {@code ChannelGateways}
 * da ED-41. Fronteira honesta (regra da cascata): nenhum provedor,
 * endpoint, SDK ou credencial vive aqui — a implementação concreta de
 * cada porta nasce na integração/LC com o provedor e a conta de teste
 * reais (pendência categoria B nomeada no relatório). O núcleo provado
 * localmente são as guardas, a conferência de webhook, o opt-in, o
 * registro no protocolo, a medição consumida da ED-41 e a integridade,
 * sobre fakes destas portas.
 */
public final class OmnichannelPorts {

    private OmnichannelPorts() {
    }

    /**
     * Mensagem inbound JÁ traduzida da carga do provedor pela borda do
     * canal (o parse do formato de cada API oficial é da borda, na
     * integração): a identidade do morador e da matrícula chega RESOLVIDA
     * pela borda a partir do cadastro da P1.3/thread do canal — o núcleo
     * não resolve identidade nem inventa protocolo: envelope sem
     * protocolo/matrícula resolvidos é recusado com motivo nomeado.
     */
    public record InboundEnvelope(
            String providerEventId, String externalThreadId, String externalMessageId,
            UUID protocolId, UUID consumerUnitId, UUID customerId, boolean priorityProfile,
            boolean authenticatedSession, String body, Instant occurredAt) {
        public InboundEnvelope {
            if (providerEventId == null || providerEventId.isBlank()
                    || externalThreadId == null || externalThreadId.isBlank()
                    || body == null || body.isBlank() || occurredAt == null) {
                throw new IllegalArgumentException(
                        "envelope inbound exige evento do provedor, thread, texto e instante");
            }
        }
    }

    /** Envio de uma resposta do atendente pelo canal da conversa; devolve o id da mensagem no provedor. */
    public interface ConversationGateway {
        String send(UUID tenantId, OmnichannelChannel channel, String externalThreadId, String body);
    }

    /** Persistência das conversas e das mensagens — TUDO por tenant. */
    public interface ConversationStore {
        void save(Conversation conversation);

        void update(Conversation conversation);

        Optional<Conversation> findById(UUID tenantId, UUID conversationId);

        Optional<Conversation> findOpenByThread(UUID tenantId, OmnichannelChannel channel, String externalThreadId);

        void appendMessage(ConversationMessage message);

        List<ConversationMessage> messages(UUID tenantId, UUID conversationId);
    }

    /** Idempotência dos webhooks: o mesmo evento do provedor produz efeitos UMA vez. */
    public interface WebhookEventStore {
        boolean alreadyProcessed(UUID tenantId, OmnichannelChannel channel, String providerEventId);

        void markProcessed(UUID tenantId, OmnichannelChannel channel, String providerEventId, Instant at);
    }

    /** Opt-in por canal do morador (linhas de {@code omni_channel_opt_ins}); a decisão é da {@link OmniOptInRule}. */
    public interface OptInPort {
        List<Boolean> optInRows(UUID tenantId, UUID customerId, OmnichannelChannel channel);
    }

    /**
     * "Provedor no DPA" (aceite) para os canais de conversa — generalização
     * declarada (extensão E-ED42-3) do {@code DpaProviderRegistry} da
     * ED-41, que é chaveado pelo enum de notificações e não alcança
     * Messenger/Instagram/Telegram. A lista nominal de suboperadores é um
     * dos 14 valores abertos da P3.9: NENHUM nome vive aqui; sem provedor
     * aprovado para (tenant, canal), nada entra nem sai por ele.
     */
    public interface OmniProviderRegistry {
        boolean providerApproved(UUID tenantId, OmnichannelChannel channel);
    }

    /**
     * Cofre de credenciais (aceite: "credenciais no cofre"): a porta
     * devolve o segredo SOMENTE no instante do uso (conferência do
     * webhook) e a REFERÊNCIA da credencial de envio para a borda — o
     * valor nunca é gravado em log, mensagem ou lançamento de integridade
     * (o log registra a referência, ver {@code IntegrityLog}). Qual
     * cofre/serviço é decisão da integração [A DEFINIR]; ausência de
     * segredo = fail-closed.
     */
    public interface CredentialVaultPort {
        Optional<String> webhookSecret(UUID tenantId, OmnichannelChannel channel);

        Optional<String> credentialReference(UUID tenantId, OmnichannelChannel channel);
    }

    /** Cadeia de integridade append-only (ver {@link IntegrityLog}). */
    public interface IntegrityLogStore {
        /** Próxima sequência e hash vigente da cadeia do tenant (1 e gênese quando vazia). */
        long nextSequence(UUID tenantId);

        String currentHash(UUID tenantId);

        void append(IntegrityLog.Entry entry);

        List<IntegrityLog.Entry> entries(UUID tenantId);
    }
}
