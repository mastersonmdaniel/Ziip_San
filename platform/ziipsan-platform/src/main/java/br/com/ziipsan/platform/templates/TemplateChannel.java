package br.com.ziipsan.platform.templates;

/**
 * Canal de um modelo (P2.30): o meio pelo qual o documento gerado sai.
 * O motor renderiza texto com variáveis; a conversão para o formato final
 * do canal (PDF, ZPL/CPCL térmico, envio por e-mail/SMS/WhatsApp) é do
 * consumidor do motor, não desta frente — o aceite pede o motor único de
 * modelos, não um renderizador de PDF nem um disparador.
 */
public enum TemplateChannel {

    /** Documento em PDF (anexo ou impressão). */
    PDF,
    /** Mensagem de e-mail (corpo em texto). */
    EMAIL,
    /** Mensagem curta de SMS. */
    SMS,
    /** Mensagem de WhatsApp. */
    WHATSAPP,
    /** Leiaute térmico (ZPL/CPCL, ED-19) para impressão em campo. */
    THERMAL
}
