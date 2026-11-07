package br.com.ziipsan.reporting.export;

/** Exportacao recusada pela regra da P1.12 (sem a permissao *:export do recurso). */
public class ExportDeniedException extends RuntimeException {
    public ExportDeniedException(String reportCode, String requiredPermission) {
        super("Exportacao negada para " + reportCode + ": falta a permissao " + requiredPermission);
    }
}
