package br.com.ziipsan.platform.notification.multichannel;

/**
 * Categoria de preço de um modelo de WhatsApp, como o aceite da ED-41 nomeia:
 * cada modelo é classificado em {@code msg.template_category_map} numa destas
 * três categorias — é a categoria que o provedor usa para precificar a
 * conversa/mensagem. Modelo SEM classificação no mapa não é "presumido
 * utilidade": é recusa nomeada no disparo (ver
 * {@link MultichannelDispatchService}).
 */
public enum WhatsAppCategory {
    MARKETING, UTILITY, AUTHENTICATION
}
