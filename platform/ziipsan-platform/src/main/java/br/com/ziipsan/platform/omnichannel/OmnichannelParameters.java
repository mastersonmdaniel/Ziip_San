package br.com.ziipsan.platform.omnichannel;

import java.util.Set;
import java.util.UUID;

/**
 * Porta dos parâmetros {@code omni.*} da ED-42 no catálogo do tenant
 * (mesmo padrão das portas de parâmetros da ED-35/ED-41):
 *
 * <ul>
 *   <li>{@code omni.channels_enabled} — <b>chave NOVA/proposta</b>:
 *       canais de atendimento habilitados pelo tenant. Canal fora do
 *       conjunto NÃO atende: quais canais cada tenant contrata é decisão
 *       dele na contratação — nenhum canal é presumido habilitado;</li>
 * </ul>
 *
 * NENHUM outro parâmetro é criado aqui: prazo/SLA de atendimento não tem
 * fonte ({@code [A DEFINIR]}, pendência nomeada) e preço/categoria de
 * modelo de WhatsApp são os da ED-41 ({@code msg.template_category_map}
 * e a porta {@code ChannelPricing} dela), consumidos, não recriados.
 * A definição da chave no catálogo global vai no SQL pendente da ED-42,
 * marcada como proposta.
 */
public interface OmnichannelParameters {

    Set<OmnichannelChannel> channelsEnabled(UUID tenantId);
}
