package br.com.ziipsan.platform.omnichannel;

import br.com.ziipsan.core.protocol.ProtocolChannel;

/**
 * Canal de ATENDIMENTO conversacional da ED-42 (omnichannel híbrido,
 * ADR-0028 (c)) — o atendimento que chega e é respondido na tela do
 * Ziip SAN. NÃO é o {@code NotificationChannel} da P3.36/ED-41: aquele é o
 * canal de ENVIO de avisos (notificação outbound); este é o canal da
 * CONVERSA com o morador. Os dois se encontram no WhatsApp e no e-mail,
 * onde a ED-42 consome a mensageria da ED-41 (medição, categoria de
 * modelo, preço) pelas portas dela — sem duplicar regra.
 *
 * <p>Canais do aceite: WhatsApp oficial, e-mail e chat do portal na tela
 * do Ziip SAN; Messenger e Instagram pela API oficial da Meta; Telegram
 * pela Bot API. NENHUM provedor, endpoint ou credencial é nomeado aqui:
 * provedor é contratado por tenant, consta do DPA e a credencial vive no
 * cofre (ver {@code OmnichannelPorts.CredentialVaultPort}).
 *
 * <p><b>Verificação de webhook por família</b> (aceite: "webhook com
 * assinatura ou segredo conferido"): Meta (WhatsApp/Messenger/Instagram)
 * assina a carga com HMAC-SHA-256; o Telegram usa o segredo do cabeçalho
 * da Bot API; o webhook do provedor de e-mail usa HMAC do provedor; o
 * chat do portal NÃO tem webhook — a conversa nasce em sessão autenticada
 * do portal (a identidade é a da sessão, não uma assinatura).
 *
 * <p><b>Mapeamento ao protocolo da P1.36 (extensão declarada E-ED42-1):</b>
 * o {@link ProtocolChannel} da P1.36 não tem valores para Messenger,
 * Instagram e Telegram e esta entrega NÃO altera enum de vizinho: esses
 * três canais registram o protocolo como {@code PORTAL} (origem digital
 * atendida na tela) e a origem EXATA fica na coluna do canal omnichannel
 * da conversa ({@code omni_conversations.channel}). WhatsApp e e-mail
 * mapeiam 1:1.
 */
public enum OmnichannelChannel {

    WHATSAPP(Family.META, WebhookMode.META_HMAC_SHA256, ProtocolChannel.WHATSAPP),
    EMAIL(Family.EMAIL_PROVIDER, WebhookMode.PROVIDER_HMAC_SHA256, ProtocolChannel.EMAIL),
    PORTAL_CHAT(Family.INTERNAL, WebhookMode.AUTHENTICATED_SESSION, ProtocolChannel.PORTAL),
    MESSENGER(Family.META, WebhookMode.META_HMAC_SHA256, ProtocolChannel.PORTAL),
    INSTAGRAM(Family.META, WebhookMode.META_HMAC_SHA256, ProtocolChannel.PORTAL),
    TELEGRAM(Family.TELEGRAM_BOT_API, WebhookMode.TELEGRAM_SECRET_TOKEN, ProtocolChannel.PORTAL);

    /** Família de API oficial por onde o canal é operado (aceite da ED-42). */
    public enum Family { META, TELEGRAM_BOT_API, EMAIL_PROVIDER, INTERNAL }

    /** Como a origem de uma mensagem inbound é autenticada neste canal. */
    public enum WebhookMode { META_HMAC_SHA256, TELEGRAM_SECRET_TOKEN, PROVIDER_HMAC_SHA256, AUTHENTICATED_SESSION }

    private final Family family;
    private final WebhookMode webhookMode;
    private final ProtocolChannel protocolChannel;

    OmnichannelChannel(Family family, WebhookMode webhookMode, ProtocolChannel protocolChannel) {
        this.family = family;
        this.webhookMode = webhookMode;
        this.protocolChannel = protocolChannel;
    }

    public Family family() { return family; }

    public WebhookMode webhookMode() { return webhookMode; }

    /** Canal do protocolo único da P1.36 sob o qual a conversa é registrada (ver E-ED42-1). */
    public ProtocolChannel protocolChannel() { return protocolChannel; }

    /** Canal externo exige opt-in positivo do morador para RESPOSTA ativa (chat do portal é sessão iniciada por ele). */
    public boolean requiresOptInForReply() { return this != PORTAL_CHAT; }
}
