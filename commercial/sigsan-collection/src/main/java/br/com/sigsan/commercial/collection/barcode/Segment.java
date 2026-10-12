package br.com.sigsan.commercial.collection.barcode;

/**
 * Segmento do código de barras de arrecadação (posição 2). Tabela da seção 05 do layout FEBRABAN
 * versão 08. O segmento 8 não existe no layout.
 *
 * <p>O segmento define também a forma de identificação da empresa/órgão (posições 16 em diante):
 * código de 4 posições atribuído pela FEBRABAN, ou, no segmento 6, as 8 primeiras posições do
 * CNPJ <b>exclusivamente numérico</b> (campo livre reduzido a 21 posições). A partir da versão 8
 * não são concedidas novas codificações do segmento 6.
 */
public enum Segment {
    PREFEITURAS('1', 4),
    SANEAMENTO('2', 4),
    ENERGIA_ELETRICA_E_GAS('3', 4),
    TELECOMUNICACOES('4', 4),
    ORGAOS_GOVERNAMENTAIS('5', 4),
    /** Carnes e assemelhados ou demais empresas/órgãos identificados por CNPJ numérico (legado). */
    CARNES_E_DEMAIS_POR_CNPJ('6', 8),
    MULTAS_DE_TRANSITO('7', 4),
    /** Uso exclusivo do banco: posições 16-19 trazem o código de compensação do banco. */
    USO_EXCLUSIVO_DO_BANCO('9', 4);

    private final char code;
    private final int companyIdLength;

    Segment(char code, int companyIdLength) {
        this.code = code;
        this.companyIdLength = companyIdLength;
    }

    public char code() {
        return code;
    }

    /** Tamanho do campo de identificação da empresa: 4 (código FEBRABAN) ou 8 (raiz do CNPJ numérico). */
    public int companyIdLength() {
        return companyIdLength;
    }

    /** Tamanho do campo livre: 25 posições com id de 4, 21 com id de 8. */
    public int freeFieldLength() {
        return 29 - companyIdLength; // posições 16..44 são 29; menos o id da empresa
    }

    public static Segment fromCode(char code) {
        for (Segment s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new InvalidBarcodeException(InvalidBarcodeException.Reason.UNKNOWN_SEGMENT,
                "Segmento inexistente no layout FEBRABAN: '" + code + "'");
    }
}
