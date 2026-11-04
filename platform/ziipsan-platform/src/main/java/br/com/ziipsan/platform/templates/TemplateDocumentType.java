package br.com.ziipsan.platform.templates;

/**
 * Tipo de documento de um modelo (P2.30). O código é o que o banco confere
 * (CHECK de {@code document_templates.document_type} na V1_12) e o que os
 * consumidores (P3.4, P3.5, P3.31, P4.1, ED-19, ED-29, ED-31, ED-36, ED-41,
 * ED-51) usam para pedir um documento ao motor sem criar leiaute próprio.
 *
 * <p>Os 14 tipos são exatamente os dos modelos de fábrica do aceite: cada
 * fábrica de {@link FactoryTemplates} usa um deles, e cada um tem um catálogo
 * próprio de variáveis em {@link TemplateVariableCatalog}.
 *
 * <p>Não confundir com o catálogo {@code document_types} da P1.20 (tipos de
 * arquivo do pipeline, definidos pelo tenant): quando o documento gerado é
 * guardado pelo pipeline, o chamador mapeia este tipo para o
 * {@code document_types} do tenant. O motor não inventa esse mapeamento.
 */
public enum TemplateDocumentType {

    PROTOCOL_RECEIPT("Comprovante de protocolo"),
    USER_RESPONSE("Resposta ao usuário"),
    CUT_NOTICE("Aviso de corte"),
    COLLECTION_LETTER("Carta de cobrança"),
    INSTALLMENT_TERM("Termo de parcelamento"),
    INFRACTION_NOTICE("Auto de infração"),
    CERTIFICATE("Certidão"),
    CDA("Certidão de dívida ativa"),
    SERVICE_ORDER_PRINT("OS impressa"),
    LAB_REPORT("Laudo"),
    AUDIT_REPORT("Relatório de auditoria"),
    EMAIL("E-mail"),
    SMS("SMS"),
    WHATSAPP("WhatsApp");

    private final String label;

    TemplateDocumentType(String label) {
        this.label = label;
    }

    /** Rótulo em português, como aparece na lista de modelos. */
    public String label() {
        return label;
    }
}
