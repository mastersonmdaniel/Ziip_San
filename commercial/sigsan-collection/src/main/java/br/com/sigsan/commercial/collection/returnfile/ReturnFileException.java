package br.com.sigsan.commercial.collection.returnfile;

/** Arquivo de retorno estruturalmente inválido (tamanho de linha, registro, campo numérico ou data). */
public class ReturnFileException extends RuntimeException {

    public enum Reason {
        EMPTY_FILE,
        BAD_LINE_LENGTH,
        MISSING_HEADER,
        MISSING_TRAILER,
        UNKNOWN_RECORD_TYPE,
        NOT_A_RETURN_FILE,
        BAD_NUMERIC_FIELD,
        BAD_DATE_FIELD,
        BAD_CODE_FIELD,
        BAD_SERVICE_ID
    }

    private final Reason reason;
    private final int lineNumber;

    public ReturnFileException(Reason reason, int lineNumber, String message) {
        super("linha " + lineNumber + ": " + message);
        this.reason = reason;
        this.lineNumber = lineNumber;
    }

    public Reason reason() {
        return reason;
    }

    public int lineNumber() {
        return lineNumber;
    }
}
