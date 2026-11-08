package br.com.ziipsan.core.inventory;

/** Excecoes nomeadas da ED-58 (padrao das Governance*Exception da P3.31). */
public final class InventoryExceptions {
    private InventoryExceptions() {}

    /** Validacao de dominio/estado: codigo estavel + mensagem. */
    public static final class ValidationException extends RuntimeException {
        private final String code;
        public ValidationException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    /**
     * Recusa de acesso (semantica HTTP 403): usada na autoaprovacao
     * (requisitante aprovando a propria requisicao) e na falta do papel
     * da faixa de alcada. A tentativa recusada E auditada pelo servico.
     */
    public static final class AccessDeniedException extends RuntimeException {
        private final String code;
        public AccessDeniedException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
        /** Semantica HTTP declarada para a camada web mapear sem reinterpretar. */
        public int httpStatus() { return 403; }
    }

    public static final class NotFoundException extends RuntimeException {
        private final String code;
        public NotFoundException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
