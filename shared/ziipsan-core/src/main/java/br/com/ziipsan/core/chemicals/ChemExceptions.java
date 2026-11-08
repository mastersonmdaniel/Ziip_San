package br.com.ziipsan.core.chemicals;

/** Excecoes nomeadas da ED-89 (padrao InventoryExceptions da ED-58). */
public final class ChemExceptions {
    private ChemExceptions() {}

    public static final class ValidationException extends RuntimeException {
        private final String code;
        public ValidationException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    public static final class NotFoundException extends RuntimeException {
        private final String code;
        public NotFoundException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
