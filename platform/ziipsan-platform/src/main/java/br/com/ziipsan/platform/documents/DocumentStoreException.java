package br.com.ziipsan.platform.documents;

/** Falha de persistencia do pipeline (embrulha SQLException). */
public class DocumentStoreException extends RuntimeException {
    public DocumentStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
