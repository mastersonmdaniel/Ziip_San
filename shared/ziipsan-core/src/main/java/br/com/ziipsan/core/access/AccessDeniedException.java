package br.com.ziipsan.core.access;

/**
 * Negação de acesso assistido ou de emergência (P3.17; ADR-0020), com o
 * estado HTTP e o código estável que a auditoria registra.
 *
 * <p>Toda negação desta tarefa é 403: sem consentimento, consentimento
 * revogado, escrita em emergência, revelar documento, exportar, sem
 * incidente declarado, sem {@code PLATFORM_OWNER} ou sem MFA no momento.
 * A negação NUNCA é lançada sem auditoria: quem lança é o
 * {@link AccessGuard}, que grava a tentativa nas trilhas exigidas antes de
 * propagar esta exceção (ver o javadoc do guard).
 */
public final class AccessDeniedException extends RuntimeException {

    private final int status;
    private final String code;

    public AccessDeniedException(String code, String message) {
        super(message);
        this.status = 403;
        this.code = code;
    }

    /** Estado HTTP da negação: sempre 403 nesta tarefa. */
    public int status() {
        return status;
    }

    /** Código estável da negação (o mesmo gravado no {@code audit_log}/{@code audit_event}). */
    public String code() {
        return code;
    }
}
