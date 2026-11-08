package br.com.ziipsan.reporting.exportprofile;

/** Recusa nomeada dos perfis de exportacao da ED-82 (mesmo padrao de excecao das fichas da ED-77/P3.41). */
public class ExportValidationException extends RuntimeException {
    private final String code;
    public ExportValidationException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
