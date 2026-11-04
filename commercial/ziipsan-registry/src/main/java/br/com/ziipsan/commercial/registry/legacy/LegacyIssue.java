package br.com.ziipsan.commercial.registry.legacy;

/**
 * Um achado do perfil de qualidade ou da aplicação (P1.2).
 *
 * <p>Severidade: ERROR barra a aplicação da linha (ela fica fora do lote,
 * listada no relatório); WARNING entra no relatório sem barrar. O campo
 * {@code message} NUNCA carrega valor bruto da fonte — só o nome do campo,
 * o código e a regra. Um valor bruto aqui vazaria documento para o staging
 * (coluna issues, auditada) e para os logs.
 */
public record LegacyIssue(String field, String code, Severity severity, String message) {

    public enum Severity { ERROR, WARNING }

    public static LegacyIssue error(String field, String code, String message) {
        return new LegacyIssue(field, code, Severity.ERROR, message);
    }

    public static LegacyIssue warning(String field, String code, String message) {
        return new LegacyIssue(field, code, Severity.WARNING, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
