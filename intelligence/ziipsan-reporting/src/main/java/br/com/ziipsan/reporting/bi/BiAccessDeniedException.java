package br.com.ziipsan.reporting.bi;

/**
 * ED-47: acesso ao BI negado pela política (tenant divergente ou
 * ausente). A mensagem é genérica de propósito: não diz se a fonte
 * existe para outro tenant nem qual é o tenant dela — a negação não é
 * um oráculo de enumeração de tenants. O detalhe fica na auditoria.
 */
public class BiAccessDeniedException extends RuntimeException {

    public BiAccessDeniedException(String message) {
        super(message);
    }
}
