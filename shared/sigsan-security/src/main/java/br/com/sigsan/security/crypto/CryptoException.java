package br.com.sigsan.security.crypto;

/**
 * Falha de cifra, decifra ou chave. A mensagem nunca contém o texto claro, a chave nem o cifrado.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
