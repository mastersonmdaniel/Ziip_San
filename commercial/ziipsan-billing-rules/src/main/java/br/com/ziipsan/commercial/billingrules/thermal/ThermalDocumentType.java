package br.com.ziipsan.commercial.billingrules.thermal;

/**
 * Tipos de documento do leiaute termico (ED-19): a conta e os comunicados de
 * debito e de excesso saem TODOS pelo mesmo motor de modelos (P2.30) e pelo
 * mesmo renderizador — o que muda e o MODELO (tipo + versao no pacote da
 * rota), nunca um leiaute proprio por documento.
 */
public enum ThermalDocumentType {

    /** Conta de campo (emissao simultanea). */
    FIELD_BILL("field_bill"),
    /** Comunicado de debito (cobranca). */
    DEBIT_NOTICE("debit_notice"),
    /** Comunicado de excesso de consumo. */
    EXCESS_NOTICE("excess_notice");

    private final String code;

    ThermalDocumentType(String code) {
        this.code = code;
    }

    /** Codigo do tipo: chave do modelo no motor da P2.30 e do pacote da rota. */
    public String code() {
        return code;
    }
}
