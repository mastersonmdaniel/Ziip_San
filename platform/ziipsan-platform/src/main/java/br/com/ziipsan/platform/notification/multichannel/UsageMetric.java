package br.com.ziipsan.platform.notification.multichannel;

/**
 * Métricas do uso medido por tenant e por módulo (aceite da ED-41): as
 * cinco famílias que o aceite nomeia. A ED-41 PRODUZ {@code MESSAGES}
 * (cada envio externo, qualquer canal) e {@code SMS} (o recorte pago de
 * SMS); {@code AI_TOKENS}, {@code STORAGE} e {@code GIS} são produzidas
 * pelos módulos donos através do MESMO registro ({@link UsageStore}) —
 * esta entrega entrega o contrato de medição comum, não a medição deles.
 */
public enum UsageMetric {
    MESSAGES, SMS, AI_TOKENS, STORAGE, GIS
}
