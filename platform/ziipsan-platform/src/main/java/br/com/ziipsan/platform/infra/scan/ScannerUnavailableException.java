package br.com.ziipsan.platform.infra.scan;

import java.io.IOException;

/**
 * P1.20: o varredor nao esta acessivel. E o sinal para o pipeline manter o
 * arquivo em quarentena (scan_status NOT_SCANNED) — nunca para marca-lo como
 * limpo. Subclasse de {@link IOException} para o chamador poder trata-la junto
 * das falhas de IO sem confundi-la com um veredicto.
 */
public class ScannerUnavailableException extends IOException {

    public ScannerUnavailableException(String message) {
        super(message);
    }

    public ScannerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
