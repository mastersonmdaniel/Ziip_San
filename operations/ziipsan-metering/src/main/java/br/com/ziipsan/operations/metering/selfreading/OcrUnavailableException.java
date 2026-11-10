package br.com.ziipsan.operations.metering.selfreading;

/**
 * O motor OCR por tras da porta esta indisponivel (II-7): falha
 * TIPIFICADA. O servico de intake apanha-a e poe a submissao em
 * {@code PENDING_OCR} -- nunca fabrica uma leitura para substituir o
 * motor em falta.
 */
public class OcrUnavailableException extends RuntimeException {

    public OcrUnavailableException(String message) {
        super(message);
    }

    public OcrUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
