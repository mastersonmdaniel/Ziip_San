package br.com.ziipsan.commercial.fiscal.xml;

import br.com.ziipsan.commercial.fiscal.rules.DpsId;

/**
 * Builder do evento da NFS-e Nacional, na forma canônica do porte — XML <b>não assinado</b>,
 * que é o que o corpus de referência congela (P1.10). O cancelamento é o e101101 do porte
 * (FISCAL-PORTE-JAVA §1, §3.5); o Id do evento é {@code PRE} + chave de acesso (50) + tipo
 * (6), validado por {@link DpsId#eventId(String, String)}.
 *
 * <p>Mesmo limite do {@link DpsXmlBuilder}: forma canônica do porte, não afirmação de
 * conformidade com o XSD oficial (indisponível neste sandbox). Sem elemento
 * {@code Signature}: a assinatura é aplicada pelo assinador da P2, sobre este XML.
 */
public final class NfseEventXmlBuilder {

    private NfseEventXmlBuilder() {
    }

    /** Entrada do evento. {@code dhEvento} em ISO-8601 com offset; motivo opcional. */
    public record EventInput(String accessKey, String eventType, String dhEvento, String reason) {
    }

    /** Monta o XML não assinado do evento, com o Id validado. */
    public static String build(EventInput input) {
        if (input == null) {
            throw new IllegalArgumentException("A entrada do evento e obrigatoria");
        }
        String eventId = DpsId.eventId(input.accessKey(), input.eventType());
        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<evento versao=\"1.00\">");
        xml.append("<infEvento Id=\"").append(XmlEscape.escape(eventId)).append("\">");
        xml.append("<tpEvento>").append(XmlEscape.escape(input.eventType())).append("</tpEvento>");
        xml.append("<chave>").append(XmlEscape.escape(input.accessKey())).append("</chave>");
        xml.append("<dhEvento>").append(XmlEscape.escape(input.dhEvento())).append("</dhEvento>");
        if (input.reason() != null && !input.reason().isBlank()) {
            xml.append("<motivo>").append(XmlEscape.escape(input.reason())).append("</motivo>");
        }
        xml.append("</infEvento></evento>");
        return xml.toString();
    }
}
