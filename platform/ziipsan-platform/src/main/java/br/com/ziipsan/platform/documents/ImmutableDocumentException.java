package br.com.ziipsan.platform.documents;

/** Tentativa de substituir ou regerar um documento imutavel: recusada. */
public class ImmutableDocumentException extends RuntimeException {
    public ImmutableDocumentException(String message) {
        super(message);
    }
}
