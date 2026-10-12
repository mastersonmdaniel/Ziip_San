package br.com.sigsan.operations.metering.rules;

/**
 * Dado obrigatório ausente ou incoerente. Não é erro de programação: é uma unidade que precisa de
 * correção cadastral ou de leitura antes de ser calculada. O {@link #getCode() código} é estável
 * para o job gravar o motivo e para os testes.
 */
public class MeteringInputException extends RuntimeException {

    private final MeteringErrorCode code;

    public MeteringInputException(MeteringErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public MeteringErrorCode getCode() {
        return code;
    }

    static void require(boolean condition, MeteringErrorCode code, String message) {
        if (!condition) {
            throw new MeteringInputException(code, message);
        }
    }

    static <T> T notNull(T value, String field) {
        if (value == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT, field + " é obrigatório");
        }
        return value;
    }
}
