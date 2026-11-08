package br.com.ziipsan.platform.portal.verify;

import java.util.List;

/**
 * Lista de canais oficiais do tenant (P3.35) e a conferencia contra a fonte:
 * o aceite manda derivar {@code portal.official_channels} de
 * {@code branding.contact} (o contato que a P1.19 cadastrou: {@code phone},
 * {@code email}, {@code site}, {@code address}). Um canal cujo valor diverge
 * do contato cadastrado NAO e publicado — e erro explicito, nao ajuste
 * silencioso: e exatamente um canal divergente que o golpe exploraria.
 *
 * <p>Regras por tipo: {@code EMAIL} confere com {@code contact.email};
 * {@code PHONE} e {@code WHATSAPP} conferem com {@code contact.phone}
 * (o WhatsApp oficial e o telefone cadastrado, sem numero separado);
 * {@code WEBSITE} confere com {@code contact.site}; {@code PORTAL} e
 * {@code APP} nao tem contrapartida em {@code branding.contact} e so sao
 * aceitos quando o chamador informa o endereco do portal do tenant
 * (dominio proprio, P2.27) ou o identificador do app (ED-39) — sem essa
 * fonte, o canal e recusado em vez de publicado sem lastro.
 */
public final class OfficialChannels {

    /** Contato da marca do tenant ({@code branding.contact}, P1.19/P2.17): a fonte dos canais. */
    public record BrandingContact(String phone, String email, String site, String address) { }

    private final List<OfficialChannel> channels;

    private OfficialChannels(List<OfficialChannel> channels) {
        this.channels = channels;
    }

    public static OfficialChannels of(List<OfficialChannel> channels, BrandingContact contact,
                                      String portalUrl, String appId) {
        if (channels == null) {
            throw new IllegalArgumentException("lista de canais oficiais e obrigatoria (vazia, nao nula)");
        }
        if (contact == null) {
            throw new IllegalArgumentException("branding.contact e obrigatorio para conferir os canais oficiais");
        }
        for (OfficialChannel channel : channels) {
            validateAgainstSource(channel, contact, portalUrl, appId);
        }
        return new OfficialChannels(List.copyOf(channels));
    }

    private static void validateAgainstSource(OfficialChannel channel, BrandingContact contact,
                                              String portalUrl, String appId) {
        switch (channel.type()) {
            case EMAIL -> requireEqual(channel, contact.email(), "branding.contact.email");
            case PHONE, WHATSAPP -> requireEqual(channel, contact.phone(), "branding.contact.phone");
            case WEBSITE -> requireEqual(channel, contact.site(), "branding.contact.site");
            case PORTAL -> {
                if (portalUrl == null || portalUrl.isBlank() || !channel.value().equals(portalUrl.strip())) {
                    throw new IllegalArgumentException("canal oficial PORTAL diverge do dominio do portal do tenant"
                            + " (P2.27) ou foi publicado sem dominio: " + channel.label());
                }
            }
            case APP -> {
                if (appId == null || appId.isBlank() || !channel.value().equals(appId.strip())) {
                    throw new IllegalArgumentException("canal oficial APP diverge do identificador do app do tenant"
                            + " (ED-39) ou foi publicado sem identificador: " + channel.label());
                }
            }
        }
    }

    private static void requireEqual(OfficialChannel channel, String sourceValue, String sourceKey) {
        if (sourceValue == null || sourceValue.isBlank()) {
            throw new IllegalArgumentException("canal oficial " + channel.type() + " (" + channel.label()
                    + ") sem fonte: " + sourceKey + " nao esta cadastrado em branding.contact");
        }
        if (!channel.value().equals(sourceValue.strip())) {
            throw new IllegalArgumentException("canal oficial " + channel.type() + " (" + channel.label()
                    + ") diverge de " + sourceKey + ": publicacao recusada");
        }
    }

    public List<OfficialChannel> channels() {
        return channels;
    }

    public boolean isEmpty() {
        return channels.isEmpty();
    }
}
