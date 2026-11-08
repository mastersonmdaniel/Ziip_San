package br.com.ziipsan.platform.signing;

/**
 * Tipos de documento candidatos a assinatura (aceite da P3.34). A lista e a da
 * spec, com a tarefa dona de cada um: contrato de adesao (P3.20), termo de
 * parcelamento (P4.1), laudo e aceite da OS em campo (ED-19, ED-23),
 * autodeclaracao da tarifa social (P1.5) e autorizacao de debito.
 *
 * <p>O {@code code} e o sufixo do parametro de nivel minimo:
 * {@code sign.min_level.<code>}. Documentos de ENTREGA AO TENANT (DPA,
 * contrato SaaS) NAO sao candidatos automaticos: a spec manda usa-los "so por
 * decisao do dono" — por isso {@code TENANT_DELIVERY_DOCUMENT} nasce com
 * {@code tenantDelivery = true} e o servico exige a decisao registrada
 * COM PROVENIENCIA (ver {@link SigningParameters#tenantDeliveryDecision}).
 */
public enum SignableDocumentType {

    ADHESION_CONTRACT("adhesion_contract", false),
    INSTALLMENT_TERM("installment_term", false),
    LAB_REPORT("lab_report", false),
    SERVICE_ORDER_ACCEPTANCE("service_order_acceptance", false),
    SOCIAL_TARIFF_SELF_DECLARATION("social_tariff_self_declaration", false),
    DEBIT_AUTHORIZATION("debit_authorization", false),
    TENANT_DELIVERY_DOCUMENT("tenant_delivery_document", true);

    private final String code;
    private final boolean tenantDelivery;

    SignableDocumentType(String code, boolean tenantDelivery) {
        this.code = code;
        this.tenantDelivery = tenantDelivery;
    }

    /** Sufixo de {@code sign.min_level.<document_type>}. */
    public String code() {
        return code;
    }

    /** Documento de entrega ao tenant: so assina com decisao do dono registrada. */
    public boolean tenantDelivery() {
        return tenantDelivery;
    }
}
