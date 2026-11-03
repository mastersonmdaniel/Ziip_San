package br.com.ziipsan.platform.documents;

/** Documento em quarentena, infectado ou nao varrido: nunca servido. */
public class DocumentNotReleasedException extends RuntimeException {
    public DocumentNotReleasedException(String message) {
        super(message);
    }
}
