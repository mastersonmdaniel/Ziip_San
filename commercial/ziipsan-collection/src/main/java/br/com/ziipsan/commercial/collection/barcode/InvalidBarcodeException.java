package br.com.ziipsan.commercial.collection.barcode;

/**
 * Código de barras ou linha digitável malformado, com dígito verificador errado ou incompatível
 * com o layout do convênio. O {@link Reason} permite ao chamador decidir (rejeitar o pagamento,
 * pedir redigitação) sem interpretar texto de mensagem.
 */
public class InvalidBarcodeException extends IllegalArgumentException {

    public enum Reason {
        BAD_LENGTH,
        NOT_NUMERIC,
        NOT_COLLECTION_PRODUCT,
        UNKNOWN_SEGMENT,
        UNKNOWN_VALUE_IDENTIFIER,
        BAD_CHECK_DIGIT,
        BAD_BLOCK_CHECK_DIGIT,
        LAYOUT_MISMATCH,
        FIELD_OUT_OF_RANGE,
        INVALID_DUE_DATE
    }

    private final Reason reason;

    public InvalidBarcodeException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
