package br.com.ziipsan.reporting.catalog;

/**
 * Um relatorio do catalogo completo (ED-46) foi impedido de executar no
 * banco primario. E recusa explicita, nunca degradacao silenciosa: o
 * chamador registra a execucao como FAILED/DENIED com esta causa.
 */
public class PrimaryAccessForbiddenException extends IllegalStateException {

    public PrimaryAccessForbiddenException(String message) {
        super(message);
    }
}
