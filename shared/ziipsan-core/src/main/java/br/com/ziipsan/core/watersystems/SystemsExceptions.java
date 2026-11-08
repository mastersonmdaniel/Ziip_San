package br.com.ziipsan.core.watersystems;

/** Excecoes nomeadas da ED-79 (sistemas, PSA, relatorio anual, Carta). */
public final class SystemsExceptions {
    private SystemsExceptions() {}

    public static class ValidationException extends RuntimeException {
        private final String code;
        public ValidationException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    public static class NotFoundException extends RuntimeException {
        private final String code;
        public NotFoundException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
