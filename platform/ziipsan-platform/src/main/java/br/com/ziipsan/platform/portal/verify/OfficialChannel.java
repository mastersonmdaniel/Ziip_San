package br.com.ziipsan.platform.portal.verify;

/**
 * Um canal oficial do tenant (P3.35): o que pode ser publicado na conta,
 * no portal e nos e-mails como "canal oficial". O VALOR (telefone, e-mail,
 * endereco do site) nunca e inventado por esta tarefa: ele vem de
 * {@code portal.official_channels} (classe d) e e conferido contra
 * {@code branding.contact} antes de publicar (ver {@link OfficialChannels}).
 */
public record OfficialChannel(ChannelType type, String label, String value) {

    public enum ChannelType { WEBSITE, PORTAL, EMAIL, PHONE, WHATSAPP, APP }

    public OfficialChannel {
        if (type == null) {
            throw new IllegalArgumentException("canal oficial exige o tipo");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("canal oficial exige o rotulo exibido ao consumidor");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("canal oficial exige o valor (endereco, numero ou e-mail)");
        }
        label = label.strip();
        value = value.strip();
    }
}
